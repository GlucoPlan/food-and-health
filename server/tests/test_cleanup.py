import os
import time

from app import cleanup
from app.db import Store

JPEG = b"\xff\xd8\xff" + b"x" * 10
KEEP = "11111111-1111-4111-8111-111111111111.jpg"
KEEP_DELETED_PAN = "22222222-2222-4222-8222-222222222222.jpg"
ORPHAN = "33333333-3333-4333-8333-333333333333.jpg"
FRESH_ORPHAN = "44444444-4444-4444-8444-444444444444.jpg"


def pan(id_, photo, deleted=False):
    return {"table": "pan", "id": id_, "data": {"id": id_, "name": id_, "photo": photo}, "updated_at": 1, "deleted": deleted}


def make(tmp_path, files, age_hours=48):
    data = tmp_path / "data"
    store = Store(data / "fh.db")
    store.apply("A", [
        pan("p1", KEEP),
        pan("p2", KEEP_DELETED_PAN, deleted=True),
        pan("p3", None),
        {"table": "product", "id": "x", "data": {"photo": ORPHAN}, "updated_at": 1, "deleted": False},
    ])
    photos = data / "photos"
    photos.mkdir()
    old = time.time() - age_hours * 3600
    for name in files:
        (photos / name).write_bytes(JPEG)
        os.utime(photos / name, (old, old))
    return data


def test_удаляются_только_фото_без_кастрюли(tmp_path):
    data = make(tmp_path, [KEEP, KEEP_DELETED_PAN, ORPHAN])
    removed = cleanup.cleanup(data)
    assert removed == [ORPHAN]
    assert sorted(p.name for p in (data / "photos").iterdir()) == [KEEP, KEEP_DELETED_PAN]


def test_свежие_фото_не_трогаются(tmp_path):
    data = make(tmp_path, [ORPHAN])
    (data / "photos" / FRESH_ORPHAN).write_bytes(JPEG)
    assert cleanup.cleanup(data) == [ORPHAN]
    assert (data / "photos" / FRESH_ORPHAN).exists()


def test_оборванные_загрузки_старше_суток_удаляются(tmp_path):
    data = make(tmp_path, [KEEP, "55555555-5555-4555-8555-555555555555.part"])
    assert cleanup.cleanup(data) == ["55555555-5555-4555-8555-555555555555.part"]


def test_после_замены_фото_кастрюли_старое_удаляется(tmp_path):
    data = make(tmp_path, [KEEP, ORPHAN])
    # Кастрюле p1 поменяли фото: теперь она ссылается на ORPHAN, а KEEP стал не нужен
    Store(data / "fh.db").apply("A", [pan("p1", ORPHAN)])
    assert cleanup.cleanup(data) == [KEEP]


def test_без_базы_или_фото_ничего_не_делает(tmp_path):
    assert cleanup.cleanup(tmp_path / "empty") == []


def test_командная_строка(tmp_path, capsys):
    data = make(tmp_path, [ORPHAN])
    assert cleanup.main(["--data-dir", str(data)]) == 0
    assert "Удалено ненужных фото: 1" in capsys.readouterr().out
