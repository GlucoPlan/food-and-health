"""Утренняя рассылка отчётов в Telegram (ТЗ 17.5, 17.7). Запускается таймером в 6:00 по Москве.

    python -m app.send_reports [--date ГГГГ-ММ-ДД] [--force]

Отправленное запоминается в telegram-sent.json в папке данных: повторный запуск не шлёт дубли.
Если Telegram недоступен — ещё две попытки, через 5 и 20 минут.
"""

import argparse
import json
import os
import sys
import time
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Callable

from .daydata import MSK, TABLES, Family
from .db import Store
from .reports import daily
from .reports.telegram_html import render
from .telegram import Bot, Recipient, TelegramError, load_recipients

RETRY_DELAYS = (5 * 60, 20 * 60)
KEEP_SENT_DAYS = 60
SENT_FILE = "telegram-sent.json"


def yesterday() -> date:
    return datetime.now(MSK).date() - timedelta(days=1)


def _key(day: date, chat_id: int, profile_id: str) -> str:
    return f"{day.isoformat()}|{chat_id}|{profile_id}"


def _load_sent(path: Path) -> set[str]:
    try:
        return set(json.loads(path.read_text()))
    except (FileNotFoundError, ValueError):
        return set()


def _save_sent(path: Path, sent: set[str], today: date) -> None:
    oldest = (today - timedelta(days=KEEP_SENT_DAYS)).isoformat()
    kept = sorted(k for k in sent if k.split("|", 1)[0] >= oldest)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(kept))
    tmp.replace(path)


def send_daily(
    store: Store,
    recipients: list[Recipient],
    bot: Bot,
    day: date,
    sent_path: Path,
    sleep: Callable[[float], None] = time.sleep,
    log: Callable[[str], None] = print,
    delays: tuple[float, ...] = RETRY_DELAYS,
    force: bool = False,
) -> int:
    """Отправить отчёты за [day]. [force] — и уже отправленные. Возвращает число неотправленных."""
    sent = _load_sent(sent_path)
    pending = [
        (r, pid) for r in recipients for pid in r.profiles if force or _key(day, r.chat_id, pid) not in sent
    ]
    if not pending:
        log(f"Отчёты за {day.isoformat()} уже отправлены; отправить заново: --force")
    failed_for_good = 0

    for attempt in range(len(delays) + 1):
        # Данные читаются заново на каждой попытке: за 20 минут могли дойти поздние записи
        family = Family(store.records(TABLES))
        retry = []
        for recipient, pid in pending:
            profile = family.profiles.get(pid)
            if profile is None:
                log(f"{recipient.name}: профиля {pid} нет (удалён?) — пропускаю")
                failed_for_good += 1
                continue
            try:
                for message in render(daily.build(family, profile, day), profile.get("name", "")):
                    bot.send(recipient.chat_id, message)
            except TelegramError as e:
                log(f"{recipient.name}, отчёт «{profile.get('name')}»: {e}")
                if e.retryable:
                    retry.append((recipient, pid))
                else:
                    failed_for_good += 1
                continue
            sent.add(_key(day, recipient.chat_id, pid))
            _save_sent(sent_path, sent, day)
            log(f"{recipient.name}: отправлен отчёт «{profile.get('name')}» за {day.isoformat()}")
        pending = retry
        if not pending:
            break
        if attempt < len(delays):
            log(f"Не отправлено: {len(pending)}; повтор через {round(delays[attempt] / 60)} мин")
            sleep(delays[attempt])
    return len(pending) + failed_for_good


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m app.send_reports")
    parser.add_argument("--date", type=date.fromisoformat, help="день отчёта; по умолчанию вчера по Москве")
    parser.add_argument("--force", action="store_true", help="отправить и уже отправленные отчёты")
    parser.add_argument("--config", type=Path,
                        default=Path(os.environ.get("FH_TELEGRAM_CONFIG", "/etc/foodhealth/telegram.json")))
    args = parser.parse_args(argv)

    token = os.environ.get("FH_TELEGRAM_TOKEN", "").strip()
    recipients = load_recipients(args.config)
    if not token or not recipients:
        print("Telegram не настроен: запустите sudo bash server/deploy/telegram-setup.sh")
        return 0
    data_dir = Path(os.environ.get("FH_DATA_DIR", "data")).resolve()
    failed = send_daily(Store(data_dir / "fh.db"), recipients, Bot(token), args.date or yesterday(),
                        data_dir / SENT_FILE, force=args.force)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
