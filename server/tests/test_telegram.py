"""Telegram (ТЗ 17.7): сообщения, рассылка, настройка. Настоящий Telegram не вызывается."""

from datetime import date

import pytest

from app import telegram_setup
from app.db import Store
from app.reports.telegram_html import LIMIT, render
from app.send_reports import send_daily
from app.telegram import Bot, Recipient, TelegramError, chats_from_updates, load_recipients, save_recipients

from .test_reports import DAY, at, item, meal, product, profile


def ln(*spans, style="normal"):
    return {"spans": [{"text": t, "bold": b} for t, b in spans], "style": style}


REPORT = {
    "kind": "day", "title": "29 сентября, вторник",
    "summary": [ln(("Калории: ", False), ("1850", True), (" из 2050", False)),
                ln(("Соли 8 г при норме до 5", False), style="warn"),
                ln(("Ничего <важного> & всё", False), style="muted")],
    "sections": [{"title": "Приёмы пищи", "lines": [ln(("08:00 — 120 ккал", True))]}],
}


# ---------- сообщения ----------

def test_выжимка_открыто_подробности_в_сворачиваемом_блоке():
    [msg] = render(REPORT, "Я")
    assert msg.startswith("<b>Итоги дня · Я · 29 сентября, вторник</b>\n")
    assert "Калории: <b>1850</b> из 2050" in msg
    assert "⚠ Соли 8 г при норме до 5" in msg
    assert "<i>Ничего &lt;важного&gt; &amp; всё</i>" in msg
    assert msg.endswith("<blockquote expandable><b>Приёмы пищи</b>\n<b>08:00 — 120 ккал</b></blockquote>")


def test_пустой_отчёт_одним_сообщением():
    empty = {**REPORT, "summary": [ln(("Ничего не записано", False), style="muted")], "sections": []}
    assert render(empty, "Дочь") == ["<b>Итоги дня · Дочь · 29 сентября, вторник</b>\n<i>Ничего не записано</i>"]


def test_длинный_отчёт_делится_и_каждая_часть_в_лимите():
    lines = [ln((f"Продукт номер {i} — 100 г: 250 ккал, Б 8 · Ж 3 · У 48", False)) for i in range(300)]
    long = {**REPORT, "sections": [{"title": "Приёмы пищи", "lines": lines}]}
    messages = render(long, "Я")
    assert len(messages) >= 3
    assert "<blockquote" not in messages[0]
    assert all(len(m) <= LIMIT for m in messages)
    assert all(m.startswith("<blockquote expandable>") and m.endswith("</blockquote>") for m in messages[1:])
    body = "\n".join(messages[1:])
    assert all(f"Продукт номер {i} —" in body for i in (0, 150, 299))


def test_сверхдлинная_строка_без_тегов_и_без_разрыва_сущности():
    huge = {**REPORT, "sections": [{"title": "Т", "lines": [ln(("а" * 5000 + " & б", True))]}]}
    messages = render(huge, "Я")
    assert all(len(m) <= LIMIT for m in messages)
    assert "<b>" not in messages[-1].replace("<b>Т</b>", "")


# ---------- API и настройки ----------

class FakeTelegram:
    """Подменяет HTTP: запоминает отправленное, умеет падать."""

    def __init__(self, updates=(), fail=None):
        self.sent: list[tuple[int, str]] = []
        self.updates = list(updates)
        self.fail = fail  # (chat_id → HTTP-код) или None

    def post(self, url: str, payload: dict, files=None):
        method = url.rsplit("/", 1)[1]
        if method == "getMe":
            return (401, {"ok": False, "description": "Unauthorized"}) if "bad" in url else \
                (200, {"ok": True, "result": {"username": "fh_bot"}})
        if method == "getUpdates":
            return 200, {"ok": True, "result": self.updates}
        if method == "sendMessage":
            code = self.fail(payload["chat_id"]) if self.fail else None
            if code:
                return code, {"ok": False, "description": f"ошибка {code}"}
            self.sent.append((payload["chat_id"], payload["text"]))
            assert payload["parse_mode"] == "HTML"
            return 200, {"ok": True, "result": {}}
        raise AssertionError(method)

    def bot(self, token="1:tok"):
        return Bot(token, post=self.post)


def test_ошибки_telegram_повторяемые_и_нет():
    for code, retryable in ((500, True), (429, True), (403, False), (400, False)):
        with pytest.raises(TelegramError) as e:
            FakeTelegram(fail=lambda _, c=code: c).bot().send(1, "x")
        assert e.value.retryable is retryable


def update(chat_id, first, username=None, type_="private"):
    return {"update_id": chat_id, "message": {"chat": {"id": chat_id, "type": type_, "first_name": first,
                                                        **({"username": username} if username else {})}}}


def test_написавшие_боту_без_повторов_и_групп():
    chats = chats_from_updates([update(1, "Иван", "ivan"), update(1, "Иван", "ivan"), update(2, "Рита"),
                                update(-5, "Семья", type_="group"), {"update_id": 9}])
    assert [(c.chat_id, c.name, c.username) for c in chats] == [(1, "Иван", "ivan"), (2, "Рита", None)]


def test_получатели_туда_и_обратно(tmp_path):
    path = tmp_path / "telegram.json"
    assert load_recipients(path) == []
    people = [Recipient(1, "Иван", ("me", "kid")), Recipient(2, "Рита", ())]
    save_recipients(path, people)
    assert load_recipients(path) == people


# ---------- рассылка ----------

@pytest.fixture
def store(client, settings):
    from .test_reports import put
    put(client, profile("me", "Я"), profile("wife", "Рита", sex="female"),
        profile("kid", "Дочь", sex="female", birth_date="2014-05-20", sd1_enabled=1),
        product("milk", "Молоко", 60, 3, 3.2, 4.7),
        meal("m1", at(DAY, "08:00"), "kid"), item("i1", "m1", "milk", 200, 120))
    return Store(settings.db_path)


FAMILY = [Recipient(1, "Иван", ("me", "kid")), Recipient(2, "Рита", ("wife", "kid"))]


def test_каждому_свои_отчёты_и_дочери(store, tmp_path):
    tg = FakeTelegram()
    failed = send_daily(store, FAMILY, tg.bot(), DAY, tmp_path / "sent.json", sleep=pytest.fail, log=lambda _: None)
    assert failed == 0
    got = [(chat, text.split("\n", 1)[0]) for chat, text in tg.sent]
    assert got == [
        (1, "<b>Итоги дня · Я · 29 сентября, вторник</b>"),
        (1, "<b>Итоги дня · Дочь · 29 сентября, вторник</b>"),
        (2, "<b>Итоги дня · Рита · 29 сентября, вторник</b>"),
        (2, "<b>Итоги дня · Дочь · 29 сентября, вторник</b>"),
    ]


def test_повторный_запуск_не_шлёт_дубли(store, tmp_path):
    tg = FakeTelegram()
    sent = tmp_path / "sent.json"
    send_daily(store, FAMILY, tg.bot(), DAY, sent, log=lambda _: None)
    send_daily(store, FAMILY, tg.bot(), DAY, sent, log=lambda _: None)
    assert len(tg.sent) == 4
    # --force — отправить заново
    send_daily(store, FAMILY, tg.bot(), DAY, sent, log=lambda _: None, force=True)
    assert len(tg.sent) == 8
    # Другой день — снова отправляется
    send_daily(store, FAMILY, tg.bot(), date(2026, 9, 30), sent, log=lambda _: None)
    assert len(tg.sent) == 12


def test_сбой_telegram_повтор_через_паузу(store, tmp_path):
    calls = {"n": 0}

    def flaky(_chat):
        calls["n"] += 1
        return 502 if calls["n"] <= 2 else None

    tg = FakeTelegram(fail=flaky)
    pauses = []
    failed = send_daily(store, FAMILY, tg.bot(), DAY, tmp_path / "sent.json",
                        sleep=pauses.append, log=lambda _: None, delays=(300, 1200))
    assert failed == 0
    assert pauses == [300]
    assert len(tg.sent) == 4


def test_заблокировавший_бота_не_мешает_остальным_и_не_повторяется(store, tmp_path):
    tg = FakeTelegram(fail=lambda chat: 403 if chat == 1 else None)
    pauses = []
    failed = send_daily(store, FAMILY, tg.bot(), DAY, tmp_path / "sent.json", sleep=pauses.append, log=lambda _: None)
    assert failed == 2
    assert pauses == []
    assert [chat for chat, _ in tg.sent] == [2, 2]


def test_удалённый_профиль_пропускается(store, tmp_path):
    tg = FakeTelegram()
    logs = []
    failed = send_daily(store, [Recipient(1, "Иван", ("нет", "me"))], tg.bot(), DAY, tmp_path / "sent.json",
                        log=logs.append)
    assert failed == 1
    assert len(tg.sent) == 1
    assert any("нет" in line for line in logs)


# ---------- настройка ----------

def test_настройка_токен_получатели_пробные_сообщения(tmp_path):
    env = tmp_path / "env"
    env.write_text("FH_FAMILY_KEY=k\n")
    env.chmod(0o640)
    before = env.stat()
    config = tmp_path / "telegram.json"
    tg = FakeTelegram(updates=[update(1, "Иван", "ivan"), update(2, "Рита")])
    profiles = [{"id": "kid", "name": "Дочь"}, {"id": "me", "name": "Я"}, {"id": "wife", "name": "Рита"}]
    answers = iter(["bad-token", "1:good", "2,1", "3,1"])
    out = []

    code = telegram_setup.configure(env, config, profiles, ask=lambda _: next(answers), say=out.append,
                                    make_bot=lambda token: Bot(token, post=tg.post))
    assert code == 0
    assert env.read_text() == "FH_FAMILY_KEY=k\nFH_TELEGRAM_TOKEN=1:good\n"
    assert oct(env.stat().st_mode & 0o777) == "0o640"
    # Владелец и группа прежние: иначе сервис не прочитает файл (под root группа стала бы root)
    assert (env.stat().st_uid, env.stat().st_gid) == (before.st_uid, before.st_gid)
    assert load_recipients(config) == [
        Recipient(1, "Иван (@ivan)", ("kid", "me")), Recipient(2, "Рита", ("kid", "wife")),
    ]
    assert [chat for chat, _ in tg.sent] == [1, 2]
    assert "итоги дня: Дочь, Я" in tg.sent[0][1]
    assert any("Токен не подошёл" in line for line in out)

    # Повторный запуск: токен уже есть, Enter — оставить как было, 0 — ничего не слать
    answers = iter(["", "0"])
    telegram_setup.configure(env, config, profiles, ask=lambda _: next(answers), say=out.append,
                             make_bot=lambda token: Bot(token, post=tg.post))
    assert load_recipients(config) == [Recipient(1, "Иван (@ivan)", ("kid", "me")), Recipient(2, "Рита", ())]


def test_настройка_никто_не_писал_боту(tmp_path):
    env = tmp_path / "env"
    env.write_text("FH_TELEGRAM_TOKEN=1:good\n")
    out = []
    code = telegram_setup.configure(env, tmp_path / "t.json", [], ask=pytest.fail, say=out.append,
                                    make_bot=lambda token: Bot(token, post=FakeTelegram().post))
    assert code == 1
    assert "/start" in out[-1]


def test_выбор_номеров():
    assert telegram_setup.parse_choice("2, 1", 3) == [0, 1]
    assert telegram_setup.parse_choice("0", 3) == []
    assert telegram_setup.parse_choice("  ", 3) is None
    with pytest.raises(ValueError):
        telegram_setup.parse_choice("4", 3)


def test_профили_для_настройки(store):
    assert telegram_setup.list_profiles(store) == [
        {"id": "kid", "name": "Дочь"}, {"id": "wife", "name": "Рита"}, {"id": "me", "name": "Я"},
    ]


def test_без_настройки_рассылка_молча_выходит(monkeypatch, tmp_path, capsys):
    from app import send_reports
    monkeypatch.delenv("FH_TELEGRAM_TOKEN", raising=False)
    assert send_reports.main(["--config", str(tmp_path / "нет.json")]) == 0
    assert "не настроен" in capsys.readouterr().out


def test_диагностика_видит_записи_дня(store):
    from app import diagnose
    text = "\n".join(diagnose.report(store, DAY))
    assert "meal: 1 (удалённых 0)" in text
    assert "Профиль «Дочь»" in text
    assert "meal: всего 1, за день 1, последняя 29.09 08:00" in text


def test_одна_картинка_фото_несколько_альбомом_по_10():
    calls = []

    def post(url, payload, files=None):
        calls.append((url.rsplit("/", 1)[1], sorted(files or {})))
        return 200, {"ok": True, "result": {}}

    Bot("t", post=post).send_photos(1, [("Вес", b"png")])
    assert calls == [("sendPhoto", ["photo"])]
    calls.clear()
    Bot("t", post=post).send_photos(1, [(f"г{i}", b"png") for i in range(11)])
    assert [c[0] for c in calls] == ["sendMediaGroup", "sendPhoto"]
    assert len(calls[0][1]) == 10


def test_multipart_собирается_с_файлами():
    from app.telegram import _multipart
    body, content_type = _multipart({"chat_id": "1", "media": [{"type": "photo"}]}, {"p0": ("c.png", b"\x89PNG")})
    boundary = content_type.split("boundary=")[1]
    assert body.startswith(f"--{boundary}\r\n".encode()) and body.endswith(f"--{boundary}--\r\n".encode())
    assert b'name="media"\r\n\r\n[{"type": "photo"}]' in body
    assert b'filename="c.png"' in body and b"\x89PNG" in body
