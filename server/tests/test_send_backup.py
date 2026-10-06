"""Копия базы в Telegram (ТЗ 17.8). Настоящий Telegram не вызывается."""

import io
import tarfile
from datetime import datetime

import pytest

from app import backup, send_backup
from app.backup import restore
from app.telegram import Bot, save_recipients

from .test_backup import data, names  # noqa: F401 — фикстура


class FakeTelegram:
    """Запоминает файлы и сообщения; [fail] — HTTP-коды ошибок для очередных вызовов."""

    def __init__(self, fail=()):
        self.docs = []
        self.texts = []
        self.fail = list(fail)

    def post(self, url, payload, files=None):
        if self.fail:
            code = self.fail.pop(0)
            if code:
                return code, {"ok": False, "description": f"ошибка {code}"}
        method = url.rsplit("/", 1)[1]
        if method == "sendDocument":
            name, content = files["document"]
            self.docs.append((name, content, payload["caption"]))
        elif method == "sendMessage":
            self.texts.append(payload["text"])
        else:
            raise AssertionError(method)
        assert payload["disable_notification"] is True
        return 200, {"ok": True, "result": {}}


@pytest.fixture
def copy(data, tmp_path):  # noqa: F811
    return backup.make_backup(data, tmp_path / "backups", datetime(2026, 10, 7, 3, 30))


def assemble(docs) -> bytes:
    return b"".join(content for _, content, _ in docs)


def test_одним_файлом_база_и_фото_восстанавливаются(copy, tmp_path):
    tg = FakeTelegram()
    assert send_backup.send_backup(Bot("t", post=tg.post), 1, copy, log=lambda _: None)
    [(name, content, caption)] = tg.docs
    assert name == "foodhealth-2026-10-07_033000.tar.gz"
    assert "07.10.2026 03:30" in caption and f"restore.sh {name}" in caption
    assert tg.texts == []

    archive = tmp_path / name
    archive.write_bytes(content)
    with tarfile.open(archive) as tar:
        # Ключ семьи и токен в Telegram не уходят
        assert "env" not in tar.getnames()
    new_data = tmp_path / "restored"
    restore(archive, new_data, env_out=tmp_path / "env")
    assert names(new_data / "fh.db") == ["p1"]
    assert (new_data / "photos" / "a.jpg").read_bytes().startswith(b"\xff\xd8\xff")
    assert not (tmp_path / "env").exists()


def test_большой_архив_частями_и_инструкция_как_собрать(copy):
    tg = FakeTelegram()
    assert send_backup.send_backup(Bot("t", post=tg.post), 1, copy, part_size=100, log=lambda _: None)
    assert len(tg.docs) > 1
    assert [n for n, _, _ in tg.docs] == [f"foodhealth-2026-10-07_033000.tar.gz.part{i:02d}"
                                          for i in range(1, len(tg.docs) + 1)]
    assert all(len(c) <= 100 for _, c, _ in tg.docs)
    assert f"часть 1 из {len(tg.docs)}" in tg.docs[0][2]
    [final] = tg.texts
    assert "cat foodhealth-2026-10-07_033000.tar.gz.part* &gt; foodhealth-2026-10-07_033000.tar.gz" in final
    with tarfile.open(fileobj=io.BytesIO(assemble(tg.docs))) as tar:
        assert "backup/fh.db.gz" in tar.getnames() and "backup/photos/a.jpg" in tar.getnames()


def test_сбой_telegram_повтор_с_оборвавшейся_части(copy):
    # Первая часть дошла, на второй — 502, после паузы досылается со второй
    tg = FakeTelegram(fail=[0, 502])
    pauses = []
    assert send_backup.send_backup(Bot("t", post=tg.post), 1, copy, part_size=100,
                                   sleep=pauses.append, log=lambda _: None)
    assert pauses == [5 * 60]
    assert [n for n, _, _ in tg.docs] == sorted({n for n, _, _ in tg.docs})  # без повторов
    with tarfile.open(fileobj=io.BytesIO(assemble(tg.docs))) as tar:
        assert "backup/fh.db.gz" in tar.getnames()
    assert len(tg.texts) == 1


def test_неповторяемая_ошибка_без_пауз(copy):
    pauses = []
    tg = FakeTelegram(fail=[403])
    assert not send_backup.send_backup(Bot("t", post=tg.post), 1, copy, sleep=pauses.append, log=lambda _: None)
    assert pauses == [] and tg.docs == []


def test_telegram_недоступен_три_попытки(copy):
    pauses = []
    tg = FakeTelegram(fail=[500, 500, 500])
    assert not send_backup.send_backup(Bot("t", post=tg.post), 1, copy, sleep=pauses.append, log=lambda _: None)
    assert pauses == [5 * 60, 20 * 60]


def test_без_получателя_копии_молча_выходит(monkeypatch, tmp_path, capsys):
    monkeypatch.setenv("FH_TELEGRAM_TOKEN", "1:tok")
    config = tmp_path / "telegram.json"
    save_recipients(config, [])
    assert send_backup.main(["--config", str(config), "--backups-dir", str(tmp_path)]) == 0
    assert "не настроена" in capsys.readouterr().out


def test_отправляется_самая_свежая_копия(monkeypatch, data, tmp_path):  # noqa: F811
    backups = tmp_path / "backups"
    backup.make_backup(data, backups, datetime(2026, 10, 6, 3, 30))
    backup.make_backup(data, backups, datetime(2026, 10, 7, 3, 30))
    config = tmp_path / "telegram.json"
    save_recipients(config, [], backup_chat_id=1)
    monkeypatch.setenv("FH_TELEGRAM_TOKEN", "1:tok")
    sent = []
    monkeypatch.setattr(send_backup, "send_backup", lambda bot, chat, copy: sent.append((chat, copy.name)) or True)
    assert send_backup.main(["--config", str(config), "--backups-dir", str(backups)]) == 0
    assert sent == [(1, "2026-10-07_033000")]
