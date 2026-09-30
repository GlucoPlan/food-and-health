"""Удаление ненужных фото кастрюль (ТЗ 15.8).

Фото нужно, пока на него ссылается хоть одна кастрюля — в том числе удалённая: её показывают
сохранённые варки. Файлы моложе суток не трогаются: фото могло прийти раньше записи своей кастрюли.

Запуск: python -m app.cleanup [--data-dir ПАПКА]   (раз в сутки — таймером перед резервной копией)
"""

import argparse
import json
import os
import sqlite3
import sys
import time
from pathlib import Path

GRACE_SECONDS = 24 * 3600


def referenced_photos(db_path: Path) -> set[str]:
    """Имена файлов фото ('<uuid>.jpg'), на которые ссылаются записи кастрюль."""
    conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    try:
        rows = conn.execute("SELECT data FROM record WHERE tbl = 'pan'").fetchall()
    finally:
        conn.close()
    names = set()
    for (data,) in rows:
        try:
            photo = json.loads(data).get("photo")
        except (ValueError, AttributeError):
            continue
        if isinstance(photo, str) and photo:
            names.add(photo)
    return names


def cleanup(data_dir: Path, now: float | None = None, grace: int = GRACE_SECONDS) -> list[str]:
    """Удаляет ненужные фото и оборванные загрузки старше [grace] секунд. Возвращает удалённые имена."""
    now = now if now is not None else time.time()
    photos_dir = data_dir / "photos"
    db_path = data_dir / "fh.db"
    if not photos_dir.is_dir() or not db_path.is_file():
        return []
    keep = referenced_photos(db_path)
    removed = []
    for f in sorted(photos_dir.iterdir()):
        if not f.is_file() or now - f.stat().st_mtime < grace:
            continue
        # .part — оборванная загрузка; .jpg — фото, если на него никто не ссылается
        if f.suffix == ".part" or (f.suffix == ".jpg" and f.name not in keep):
            f.unlink()
            removed.append(f.name)
    return removed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m app.cleanup")
    parser.add_argument("--data-dir", type=Path, default=Path(os.environ.get("FH_DATA_DIR", "data")))
    args = parser.parse_args(argv)
    removed = cleanup(args.data_dir)
    print(f"Удалено ненужных фото: {len(removed)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
