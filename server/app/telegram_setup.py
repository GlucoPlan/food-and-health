"""Настройка бота (ТЗ 17.7): токен и кто чьи отчёты получает. Запускается из deploy/telegram-setup.sh.

    python -m app.telegram_setup profiles                  — профили семьи в JSON (от имени сервиса)
    python -m app.telegram_setup configure --env-file ... --config ... --profiles ФАЙЛ   — диалог (root)
"""

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Callable

from .daydata import Family
from .db import Store
from .telegram import Bot, Recipient, TelegramError, chats_from_updates, load_recipients, save_recipients

TOKEN_KEY = "FH_TELEGRAM_TOKEN"


def list_profiles(store: Store) -> list[dict]:
    family = Family(store.records(["profile"]))
    return sorted(({"id": p["id"], "name": p.get("name", "")} for p in family.profiles.values()),
                  key=lambda p: p["name"])


def read_token(env_file: Path) -> str | None:
    for line in env_file.read_text().splitlines():
        if line.startswith(f"{TOKEN_KEY}="):
            return line.split("=", 1)[1].strip() or None
    return None


def write_token(env_file: Path, token: str) -> None:
    lines = [ln for ln in env_file.read_text().splitlines() if not ln.startswith(f"{TOKEN_KEY}=")]
    lines.append(f"{TOKEN_KEY}={token}")
    tmp = env_file.with_suffix(".tmp")
    tmp.write_text("\n".join(lines) + "\n")
    os.chmod(tmp, env_file.stat().st_mode & 0o777)
    tmp.replace(env_file)


def parse_choice(text: str, count: int) -> list[int] | None:
    """«1, 3» → [0, 2]; «0» → []; пусто → None (оставить как есть). Неверный ввод — ValueError."""
    text = text.strip()
    if not text:
        return None
    numbers = [int(x) for x in text.replace(" ", "").split(",") if x]
    if numbers == [0]:
        return []
    if any(n < 1 or n > count for n in numbers):
        raise ValueError(f"номера от 1 до {count}")
    return sorted({n - 1 for n in numbers})


def configure(
    env_file: Path,
    config: Path,
    profiles: list[dict],
    ask: Callable[[str], str] = input,
    say: Callable[[str], None] = print,
    make_bot: Callable[[str], Bot] = Bot,
    new_token: bool = False,
) -> int:
    token = None if new_token else read_token(env_file)
    while True:
        if token is None:
            token = ask("Токен бота от @BotFather: ").strip()
        try:
            me = make_bot(token).me()
            break
        except TelegramError as e:
            say(f"Токен не подошёл ({e}). Попробуйте ещё раз.")
            token = None
    write_token(env_file, token)
    bot = make_bot(token)
    say(f"Бот: @{me.get('username')}")

    known = {r.chat_id: r for r in load_recipients(config)}
    for chat in chats_from_updates(bot.updates()):
        if chat.chat_id not in known:
            label = chat.name + (f" (@{chat.username})" if chat.username else "")
            known[chat.chat_id] = Recipient(chat.chat_id, label, ())
    if not known:
        say(f"Боту ещё никто не написал. Каждый пишет @{me.get('username')} команду /start, "
            "затем запустите настройку снова (Telegram хранит сообщения боту около суток).")
        return 1

    names = {p["id"]: p["name"] for p in profiles}
    say("\nПрофили:")
    for i, p in enumerate(profiles, 1):
        say(f"  {i}. {p['name']}")

    result = []
    for r in known.values():
        current = ", ".join(names.get(pid, "?") for pid in r.profiles) or "ничего"
        while True:
            answer = ask(f"\n{r.name} — сейчас: {current}.\n"
                         "Чьи отчёты присылать? Номера через запятую, 0 — ничего, Enter — оставить: ")
            try:
                choice = parse_choice(answer, len(profiles))
                break
            except ValueError as e:
                say(f"Не понял: {e}")
        chosen = r.profiles if choice is None else tuple(profiles[i]["id"] for i in choice)
        result.append(Recipient(r.chat_id, r.name, chosen))

    save_recipients(config, result)
    say(f"\nСохранено в {config}")
    for r in result:
        if not r.profiles:
            continue
        who = ", ".join(names.get(pid, "?") for pid in r.profiles)
        try:
            bot.send(r.chat_id, f"Бот «Еда и здоровье» настроен. Каждое утро в 6:00 — итоги дня: {who}.")
            say(f"{r.name}: пробное сообщение отправлено")
        except TelegramError as e:
            say(f"{r.name}: пробное сообщение не ушло — {e}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m app.telegram_setup")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("profiles")
    c = sub.add_parser("configure")
    c.add_argument("--env-file", type=Path, required=True)
    c.add_argument("--config", type=Path, required=True)
    c.add_argument("--profiles", type=Path, required=True)
    c.add_argument("--new-token", action="store_true")
    args = parser.parse_args(argv)

    if args.command == "profiles":
        data_dir = Path(os.environ.get("FH_DATA_DIR", "data")).resolve()
        print(json.dumps(list_profiles(Store(data_dir / "fh.db")), ensure_ascii=False))
        return 0
    profiles = json.loads(args.profiles.read_text())
    return configure(args.env_file, args.config, profiles, new_token=args.new_token)


if __name__ == "__main__":
    sys.exit(main())
