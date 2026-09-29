"""Резервные копии сервера (ТЗ 9): раз в сутки, хранить последние 30.

Копия — папка ГГГГ-ММ-ДД_ЧЧММСС:
    fh.db.gz   согласованная копия базы (резервное копирование SQLite работает и во время записи)
    photos/    фото; неизменившиеся — жёсткие ссылки на предыдущую копию, место почти не занимают

Запуск: python -m app.backup {backup|restore|export} ...
"""

import argparse
import gzip
import os
import re
import shutil
import sqlite3
import sys
import tarfile
import tempfile
from datetime import datetime
from pathlib import Path

KEEP = 30
NAME = re.compile(r"^\d{4}-\d{2}-\d{2}_\d{6}$")


def list_backups(backups_dir: Path) -> list[Path]:
    """Копии от старых к новым."""
    if not backups_dir.is_dir():
        return []
    return sorted(p for p in backups_dir.iterdir() if p.is_dir() and NAME.match(p.name))


def make_backup(data_dir: Path, backups_dir: Path, now: datetime | None = None) -> Path:
    now = now or datetime.now()
    backups_dir.mkdir(parents=True, exist_ok=True)
    previous = list_backups(backups_dir)
    target = backups_dir / now.strftime("%Y-%m-%d_%H%M%S")
    if target.exists():
        raise FileExistsError(f"Копия {target.name} уже есть")
    work = Path(tempfile.mkdtemp(prefix=".partial-", dir=backups_dir))
    try:
        _backup_db(data_dir / "fh.db", work / "fh.db.gz")
        _backup_photos(data_dir / "photos", work / "photos", previous[-1] / "photos" if previous else None)
        work.rename(target)
    except BaseException:
        shutil.rmtree(work, ignore_errors=True)
        raise
    return target


def _backup_db(db: Path, out_gz: Path) -> None:
    if not db.is_file():
        raise FileNotFoundError(f"Нет базы {db}")
    raw = out_gz.with_suffix("")
    source = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    dest = sqlite3.connect(raw)
    try:
        source.backup(dest)
    finally:
        dest.close()
        source.close()
    with open(raw, "rb") as src, gzip.open(out_gz, "wb") as dst:
        shutil.copyfileobj(src, dst)
    raw.unlink()


def _backup_photos(photos: Path, out: Path, previous: Path | None) -> None:
    out.mkdir()
    if not photos.is_dir():
        return
    for f in sorted(photos.iterdir()):
        if not f.is_file():
            continue
        old = previous / f.name if previous else None
        # Фото неизменяемы (имя — случайный id), поэтому то же имя и размер = тот же файл
        if old is not None and old.is_file() and old.stat().st_size == f.stat().st_size:
            os.link(old, out / f.name)
        else:
            shutil.copy2(f, out / f.name)


def prune(backups_dir: Path, keep: int = KEEP) -> list[Path]:
    """Удалить копии сверх [keep] самых новых. Возвращает удалённые."""
    old = list_backups(backups_dir)[:-keep] if keep > 0 else list_backups(backups_dir)
    for p in old:
        shutil.rmtree(p)
    return old


def export_archive(backup: Path, env_file: Path | None, out: Path) -> Path:
    """Архив для переезда: копия данных и файл настроек с ключом семьи (телефоны не придётся перенастраивать)."""
    with tarfile.open(out, "w:gz") as tar:
        tar.add(backup, arcname="backup")
        if env_file is not None and env_file.is_file():
            tar.add(env_file, arcname="env")
    os.chmod(out, 0o600)
    return out


def restore(source: Path, data_dir: Path, env_out: Path | None = None) -> None:
    """Восстановить данные из копии (папки) или из архива переезда (.tar.gz). Сервис должен быть остановлен."""
    with tempfile.TemporaryDirectory() as tmp:
        env_in = None
        if source.is_file():
            with tarfile.open(source, "r:gz") as tar:
                tar.extractall(tmp, filter="data")
            backup = Path(tmp) / "backup"
            env_in = Path(tmp) / "env"
        else:
            backup = source
        db_gz = backup / "fh.db.gz"
        if not db_gz.is_file():
            raise FileNotFoundError(f"В {source} нет fh.db.gz — это не копия сервера")

        data_dir.mkdir(parents=True, exist_ok=True)
        new_db = data_dir / "fh.db.restoring"
        with gzip.open(db_gz, "rb") as src, open(new_db, "wb") as dst:
            shutil.copyfileobj(src, dst)
        conn = sqlite3.connect(new_db)
        try:
            if conn.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
                raise ValueError("Копия базы повреждена")
        finally:
            conn.close()

        for suffix in ("-wal", "-shm"):
            (data_dir / f"fh.db{suffix}").unlink(missing_ok=True)
        new_db.replace(data_dir / "fh.db")

        photos = data_dir / "photos"
        staged = data_dir / "photos.restoring"
        shutil.rmtree(staged, ignore_errors=True)
        if (backup / "photos").is_dir():
            shutil.copytree(backup / "photos", staged)
        else:
            staged.mkdir()
        shutil.rmtree(photos, ignore_errors=True)
        staged.rename(photos)

        if env_out is not None and env_in is not None and env_in.is_file():
            shutil.copy2(env_in, env_out)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m app.backup")
    parser.add_argument("--data-dir", type=Path, default=Path(os.environ.get("FH_DATA_DIR", "data")))
    parser.add_argument("--backups-dir", type=Path, default=Path("/var/backups/foodhealth"))
    sub = parser.add_subparsers(dest="command", required=True)
    b = sub.add_parser("backup", help="сделать копию и удалить старые")
    b.add_argument("--keep", type=int, default=KEEP)
    r = sub.add_parser("restore", help="восстановить из копии или архива")
    r.add_argument("source", type=Path)
    r.add_argument("--env-out", type=Path)
    e = sub.add_parser("export", help="архив для переезда из свежей копии")
    e.add_argument("--env", type=Path)
    e.add_argument("--out", type=Path, required=True)
    args = parser.parse_args(argv)

    if args.command == "backup":
        target = make_backup(args.data_dir, args.backups_dir)
        removed = prune(args.backups_dir, args.keep)
        print(f"Копия: {target}; удалено старых: {len(removed)}")
    elif args.command == "restore":
        restore(args.source, args.data_dir, args.env_out)
        print(f"Данные восстановлены из {args.source}")
    elif args.command == "export":
        target = make_backup(args.data_dir, args.backups_dir)
        prune(args.backups_dir, KEEP)
        print(f"Архив: {export_archive(target, args.env, args.out)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
