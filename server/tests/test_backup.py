import gzip
import sqlite3
from datetime import datetime, timedelta

import pytest

from app import backup
from app.db import Store


@pytest.fixture
def data(tmp_path):
    d = tmp_path / "data"
    store = Store(d / "fh.db")
    store.apply("A", [{"table": "product", "id": "p1", "data": {"name": "Молоко"}, "updated_at": 1, "deleted": False}])
    (d / "photos").mkdir()
    (d / "photos" / "a.jpg").write_bytes(b"\xff\xd8\xff" + b"a" * 100)
    return d


def names(db_path):
    conn = sqlite3.connect(db_path)
    try:
        return [r[0] for r in conn.execute("SELECT id FROM record ORDER BY id")]
    finally:
        conn.close()


def unpack(backup_dir, tmp_path):
    out = tmp_path / "unpacked.db"
    with gzip.open(backup_dir / "fh.db.gz", "rb") as src:
        out.write_bytes(src.read())
    return out


def test_копия_содержит_базу_и_фото(data, tmp_path):
    target = backup.make_backup(data, tmp_path / "backups", datetime(2026, 9, 29, 3, 30))
    assert target.name == "2026-09-29_033000"
    assert names(unpack(target, tmp_path)) == ["p1"]
    assert (target / "photos" / "a.jpg").read_bytes() == (data / "photos" / "a.jpg").read_bytes()


def test_копия_согласованная_при_открытой_записи(data, tmp_path):
    writer = sqlite3.connect(data / "fh.db", isolation_level=None)
    writer.execute("BEGIN IMMEDIATE")
    writer.execute(
        "INSERT INTO record VALUES ('product','p2','{}',1,0,'A',99,1)"
    )
    try:
        target = backup.make_backup(data, tmp_path / "backups", datetime(2026, 9, 29, 3, 30))
    finally:
        writer.execute("ROLLBACK")
        writer.close()
    # Незавершённая запись в копию не попала, копия целая
    assert names(unpack(target, tmp_path)) == ["p1"]


def test_неизменившиеся_фото_жёсткие_ссылки(data, tmp_path):
    backups = tmp_path / "backups"
    first = backup.make_backup(data, backups, datetime(2026, 9, 28, 3, 30))
    (data / "photos" / "b.jpg").write_bytes(b"\xff\xd8\xff" + b"b" * 50)
    second = backup.make_backup(data, backups, datetime(2026, 9, 29, 3, 30))

    assert (first / "photos" / "a.jpg").stat().st_ino == (second / "photos" / "a.jpg").stat().st_ino
    assert (second / "photos" / "b.jpg").is_file()
    assert not (first / "photos" / "b.jpg").exists()


def test_хранятся_30_последних(data, tmp_path):
    backups = tmp_path / "backups"
    start = datetime(2026, 8, 1, 3, 30)
    for i in range(33):
        backup.make_backup(data, backups, start + timedelta(days=i))
    removed = backup.prune(backups)
    kept = backup.list_backups(backups)
    assert len(kept) == 30
    assert [p.name for p in removed] == ["2026-08-01_033000", "2026-08-02_033000", "2026-08-03_033000"]
    assert kept[0].name == "2026-08-04_033000"


def test_две_копии_за_день_не_путаются(data, tmp_path):
    backups = tmp_path / "backups"
    backup.make_backup(data, backups, datetime(2026, 9, 29, 3, 30))
    backup.make_backup(data, backups, datetime(2026, 9, 29, 14, 5))
    assert len(backup.list_backups(backups)) == 2


def test_без_базы_копия_не_создаётся(tmp_path):
    with pytest.raises(FileNotFoundError):
        backup.make_backup(tmp_path / "empty", tmp_path / "backups")
    assert backup.list_backups(tmp_path / "backups") == []
    assert not any((tmp_path / "backups").iterdir())


def test_восстановление_из_копии(data, tmp_path):
    target = backup.make_backup(data, tmp_path / "backups", datetime(2026, 9, 29, 3, 30))
    # Базу «испортили»: добавили запись и удалили фото
    Store(data / "fh.db").apply("A", [{"table": "product", "id": "junk", "data": {}, "updated_at": 1, "deleted": False}])
    (data / "photos" / "a.jpg").unlink()

    backup.restore(target, data)

    assert names(data / "fh.db") == ["p1"]
    assert (data / "photos" / "a.jpg").is_file()
    assert Store(data / "fh.db").count() == 1


def test_переезд_архив_с_ключом(data, tmp_path):
    env = tmp_path / "env"
    env.write_text("FH_FAMILY_KEY=secret-secret-secret\nFH_DATA_DIR=/var/lib/foodhealth\n")
    target = backup.make_backup(data, tmp_path / "backups", datetime(2026, 9, 29, 3, 30))
    archive = backup.export_archive(target, env, tmp_path / "export.tar.gz")
    assert oct(archive.stat().st_mode & 0o777) == "0o600"

    new_data = tmp_path / "new-server" / "data"
    new_env = tmp_path / "new-server" / "env"
    backup.restore(archive, new_data, env_out=new_env)

    assert names(new_data / "fh.db") == ["p1"]
    assert (new_data / "photos" / "a.jpg").is_file()
    assert new_env.read_text() == env.read_text()


def test_не_копия_отклоняется(tmp_path):
    (tmp_path / "junk").mkdir()
    with pytest.raises(FileNotFoundError):
        backup.restore(tmp_path / "junk", tmp_path / "data")


def test_командная_строка(data, tmp_path, capsys):
    backups = tmp_path / "backups"
    assert backup.main(["--data-dir", str(data), "--backups-dir", str(backups), "backup"]) == 0
    assert "Копия" in capsys.readouterr().out
    assert len(backup.list_backups(backups)) == 1
