"""Утренняя рассылка отчётов в Telegram (ТЗ 17.5–17.7). Запускается таймером в 6:00 по Москве.

    python -m app.send_reports [--date ГГГГ-ММ-ДД] [--week] [--force]

Без параметров — итоги вчерашнего дня, а по понедельникам ещё и прошлой недели.
--date — за другой день; --week — только недельный (за неделю с --date, иначе за прошлую полную).
Отправленное запоминается в telegram-sent.json в папке данных: повторный запуск не шлёт дубли,
--force — отправить заново. Если Telegram недоступен — ещё две попытки, через 5 и 20 минут.
"""

import argparse
import base64
import json
import os
import sys
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Callable

from . import report_settings
from .daydata import MSK, TABLES, Family
from .db import Store
from .report_settings import ReportSettings
from .reports import daily, period
from .reports.telegram_html import render
from .telegram import Bot, Recipient, TelegramError, load_recipients

RETRY_DELAYS = (5 * 60, 20 * 60)
KEEP_SENT_DAYS = 60
SENT_FILE = "telegram-sent.json"


@dataclass(frozen=True)
class Job:
    """Какой отчёт отправить: день или неделя с [first] по [last]."""
    kind: str
    first: date
    last: date

    def key(self, chat_id: int, profile_id: str) -> str:
        # Ключ ежедневного — как в первой версии, чтобы старые отметки об отправке оставались в силе
        middle = "" if self.kind == "day" else f"{self.kind}|"
        return f"{self.first.isoformat()}|{middle}{chat_id}|{profile_id}"


def day_job(day: date) -> Job:
    return Job("day", day, day)


def week_job(day: date) -> Job:
    return Job("week", *period.week_of(day))


def today() -> date:
    return datetime.now(MSK).date()


def default_jobs(day: date | None, week_only: bool) -> list[Job]:
    if week_only:
        return [week_job(day or today() - timedelta(days=7))]
    day = day or today() - timedelta(days=1)
    jobs = [day_job(day)]
    if day.weekday() == 6:  # воскресенье — неделя закончилась
        jobs.append(week_job(day))
    return jobs


def _load_sent(path: Path) -> set[str]:
    try:
        return set(json.loads(path.read_text()))
    except (FileNotFoundError, ValueError):
        return set()


def _save_sent(path: Path, sent: set[str], now: date) -> None:
    oldest = (now - timedelta(days=KEEP_SENT_DAYS)).isoformat()
    kept = sorted(k for k in sent if k.split("|", 1)[0] >= oldest)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(kept))
    tmp.replace(path)


def _build(job: Job, family: Family, profile: dict, settings: ReportSettings) -> dict:
    if job.kind == "day":
        return daily.build(family, profile, job.first)
    return period.build(family, profile, job.first, job.last, settings, kind=job.kind)


def send(
    store: Store,
    recipients: list[Recipient],
    bot: Bot,
    jobs: list[Job],
    sent_path: Path,
    settings: ReportSettings = ReportSettings(),
    sleep: Callable[[float], None] = time.sleep,
    log: Callable[[str], None] = print,
    delays: tuple[float, ...] = RETRY_DELAYS,
    force: bool = False,
) -> int:
    """Отправить отчёты. На каждого человека и профиль — по порядку [jobs]. Возвращает число неотправленных."""
    sent = _load_sent(sent_path)
    pending = [
        (r, pid, job) for r in recipients for pid in r.profiles for job in jobs
        if force or job.key(r.chat_id, pid) not in sent
    ]
    if not pending:
        log("Эти отчёты уже отправлены; отправить заново: --force")
    failed_for_good = 0

    for attempt in range(len(delays) + 1):
        # Данные читаются заново на каждой попытке: за 20 минут могли дойти поздние записи
        family = Family(store.records(TABLES))
        retry = []
        for recipient, pid, job in pending:
            profile = family.profiles.get(pid)
            if profile is None:
                log(f"{recipient.name}: профиля {pid} нет (удалён?) — пропускаю")
                failed_for_good += 1
                continue
            what = f"{job.kind} «{profile.get('name')}» за {job.first.isoformat()}"
            try:
                report = _build(job, family, profile, settings)
                for message in render(report, profile.get("name", "")):
                    bot.send(recipient.chat_id, message)
                images = [(i["title"], base64.b64decode(i["png"])) for i in report.get("images", [])]
                if images:
                    bot.send_photos(recipient.chat_id, images)
            except TelegramError as e:
                log(f"{recipient.name}, {what}: {e}")
                if e.retryable:
                    retry.append((recipient, pid, job))
                else:
                    failed_for_good += 1
                continue
            sent.add(job.key(recipient.chat_id, pid))
            _save_sent(sent_path, sent, today())
            log(f"{recipient.name}: отправлен {what}")
        pending = retry
        if not pending:
            break
        if attempt < len(delays):
            log(f"Не отправлено: {len(pending)}; повтор через {round(delays[attempt] / 60)} мин")
            sleep(delays[attempt])
    return len(pending) + failed_for_good


def send_daily(store: Store, recipients: list[Recipient], bot: Bot, day: date, sent_path: Path, **kwargs) -> int:
    return send(store, recipients, bot, [day_job(day)], sent_path, **kwargs)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m app.send_reports")
    parser.add_argument("--date", type=date.fromisoformat, help="день отчёта; по умолчанию вчера по Москве")
    parser.add_argument("--week", action="store_true", help="только недельный отчёт")
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
    settings = report_settings.load(Path(os.environ.get("FH_REPORTS_CONFIG", "/etc/foodhealth/reports.json")))
    failed = send(Store(data_dir / "fh.db"), recipients, Bot(token), default_jobs(args.date, args.week),
                  data_dir / SENT_FILE, settings=settings, force=args.force)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
