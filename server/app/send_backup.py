"""Копия базы в Telegram (ТЗ 17.8): после ночной резервной копии — полная копия (база и фото) одним архивом,
без звука. Запускается от root сразу после foodhealth-backup.service.

    python -m app.send_backup [--backups-dir ПАПКА] [--backup КОПИЯ]

Архив — того же вида, что архив переезда, но без файла настроек: ключ семьи и токен бота в Telegram не уходят.
Восстанавливается тем же restore.sh. Больше лимита бота — делится на части (собрать: cat).
Если Telegram недоступен — ещё две попытки, через 5 и 20 минут, с той части, на которой оборвалось.
"""

import argparse
import os
import sys
import tempfile
import time
from pathlib import Path
from typing import Callable, Iterator

from .backup import NAME, export_archive, list_backups
from .send_reports import RETRY_DELAYS
from .telegram import Bot, TelegramError, load_backup_chat

# Лимит Bot API на файл — 50 МБ, с запасом на заголовки multipart
PART_SIZE = 45 * 1024 * 1024
RESTORE = "sudo bash server/deploy/restore.sh"


def archive_name(backup: Path) -> str:
    return f"foodhealth-{backup.name}.tar.gz"


def parts(archive: Path, part_size: int = PART_SIZE) -> Iterator[bytes]:
    """Архив кусками не больше [part_size]; в памяти — только один кусок."""
    with open(archive, "rb") as f:
        while chunk := f.read(part_size):
            yield chunk


def _size(n: int) -> str:
    return f"{n / 1024 / 1024:.1f}".replace(".", ",") + " МБ"


def _when(backup: Path) -> str:
    # 2026-10-07_033000 → 07.10.2026 03:30
    if not NAME.match(backup.name):
        return backup.name
    day, clock = backup.name.split("_")
    y, m, d = day.split("-")
    return f"{d}.{m}.{y} {clock[:2]}:{clock[2:4]}"


def messages(backup: Path, total_size: int, count: int) -> tuple[list[str], str | None]:
    """Подписи к частям и итоговое сообщение (только если частей несколько)."""
    name = archive_name(backup)
    head = f"Копия базы и фото за {_when(backup)}, {_size(total_size)}"
    if count == 1:
        return [f"{head}.\nВосстановить: <code>{RESTORE} {name}</code>"], None
    captions = [f"{head}: часть {i} из {count}." for i in range(1, count + 1)]
    final = (f"{head}, {count} частей. Собрать и восстановить:\n"
             f"<code>cat {name}.part* &gt; {name}</code>\n<code>{RESTORE} {name}</code>")
    return captions, final


def send_backup(
    bot: Bot,
    chat_id: int,
    backup: Path,
    part_size: int = PART_SIZE,
    sleep: Callable[[float], None] = time.sleep,
    log: Callable[[str], None] = print,
    delays: tuple[float, ...] = RETRY_DELAYS,
) -> bool:
    """Отправить копию [backup] (папку копии). True — дошло целиком."""
    with tempfile.TemporaryDirectory() as tmp:
        archive = export_archive(backup, None, Path(tmp) / archive_name(backup))
        total = archive.stat().st_size
        count = max(1, -(-total // part_size))
        captions, final = messages(backup, total, count)
        name = archive_name(backup)
        files = [name] if count == 1 else [f"{name}.part{i:02d}" for i in range(1, count + 1)]

        done = 0  # отправлено частей; итоговое сообщение — ещё один шаг
        steps = count + (1 if final else 0)
        for attempt in range(len(delays) + 1):
            try:
                for i, chunk in enumerate(parts(archive, part_size)):
                    if i < done:
                        continue
                    bot.send_document(chat_id, files[i], chunk, captions[i], silent=True)
                    done += 1
                if final and done == count:
                    bot.send(chat_id, final, silent=True)
                    done += 1
            except TelegramError as e:
                log(f"Копия {backup.name}: {e}")
                if not e.retryable:
                    return False
                if attempt < len(delays):
                    log(f"Отправлено {done} из {steps}; повтор через {round(delays[attempt] / 60)} мин")
                    sleep(delays[attempt])
                continue
            log(f"Копия {backup.name} отправлена: {_size(total)}, файлов {count}")
            return True
        return False


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m app.send_backup")
    parser.add_argument("--backups-dir", type=Path, default=Path("/var/backups/foodhealth"))
    parser.add_argument("--backup", type=Path, help="папка копии; по умолчанию самая свежая")
    parser.add_argument("--config", type=Path,
                        default=Path(os.environ.get("FH_TELEGRAM_CONFIG", "/etc/foodhealth/telegram.json")))
    args = parser.parse_args(argv)

    token = os.environ.get("FH_TELEGRAM_TOKEN", "").strip()
    chat_id = load_backup_chat(args.config)
    if not token or chat_id is None:
        print("Копия в Telegram не настроена: sudo bash server/deploy/telegram-setup.sh")
        return 0
    backup = args.backup or next(reversed(list_backups(args.backups_dir)), None)
    if backup is None:
        print(f"В {args.backups_dir} нет копий")
        return 1
    return 0 if send_backup(Bot(token), chat_id, backup) else 1


if __name__ == "__main__":
    sys.exit(main())
