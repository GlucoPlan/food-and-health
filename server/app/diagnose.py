"""Что сервер видит в базе для отчётов — для разбора «пустых» отчётов. Только чтение, без содержимого записей.

    python -m app.diagnose [--date ГГГГ-ММ-ДД]
"""

import argparse
import os
import sys
from collections import Counter
from datetime import date, timedelta
from pathlib import Path

from .daydata import TABLES, Family, day_bounds, local
from .db import Store
from .send_reports import today

TIMED = {"meal": "eaten_at", "weight": "measured_at", "blood_pressure": "measured_at", "sleep": "woke_at",
         "water": "drunk_at", "body_measure": "measured_at", "height": "measured_at"}


def report(store: Store, day: date) -> list[str]:
    records = store.records(TABLES)
    out = [f"Записей для отчётов: {len(records)}"]
    by_table = Counter((r["table"], r["deleted"]) for r in records)
    for table in TABLES:
        out.append(f"  {table}: {by_table[(table, False)]} (удалённых {by_table[(table, True)]})")

    family = Family(records)
    start, end = day_bounds(day)
    out.append(f"\nДень {day.isoformat()}: {local(start):%d.%m %H:%M} – {local(end):%d.%m %H:%M} по Москве")
    for pid, p in family.profiles.items():
        out.append(f"\nПрофиль «{p.get('name')}» ({pid[:8]}…)")
        for table, key in TIMED.items():
            rows = [r for r in family._alive[table] if r.get("profile_id") == pid]
            times = [r.get(key) for r in rows]
            bad = sum(1 for t in times if not isinstance(t, (int, float)))
            good = sorted(t for t in times if isinstance(t, (int, float)))
            in_day = sum(1 for t in good if start <= t < end)
            last = f"{local(good[-1]):%d.%m %H:%M}" if good else "—"
            extra = f", без времени {bad}" if bad else ""
            out.append(f"  {table}: всего {len(rows)}, за день {in_day}, последняя {last}{extra}")
    others = Counter(r.get("profile_id") for t in TIMED for r in family._alive[t])
    unknown = {pid: n for pid, n in others.items() if pid not in family.profiles}
    if unknown:
        out.append(f"\nЗаписи с неизвестным профилем: {sum(unknown.values())} ({len(unknown)} профилей)")
    return out


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m app.diagnose")
    parser.add_argument("--date", type=date.fromisoformat)
    args = parser.parse_args(argv)
    data_dir = Path(os.environ.get("FH_DATA_DIR", "data")).resolve()
    print("\n".join(report(Store(data_dir / "fh.db"), args.date or today() - timedelta(days=1))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
