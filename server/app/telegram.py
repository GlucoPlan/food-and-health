"""Telegram Bot API (ТЗ 17.7): бот только отправляет, команд не слушает.

Токен — FH_TELEGRAM_TOKEN в файле окружения сервера. Кто чьи отчёты получает — telegram.json рядом с ним:
    {"recipients": [{"chat_id": 123, "name": "Иван", "profiles": ["<id профиля>", ...]}]}
Только стандартная библиотека: лишних зависимостей на сервере нет.
"""

import json
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

API = "https://api.telegram.org"
TIMEOUT = 30

# (url, тело) → (HTTP-код, ответ JSON); подменяется в тестах
Transport = Callable[[str, dict], tuple[int, dict]]


class TelegramError(Exception):
    def __init__(self, message: str, retryable: bool):
        super().__init__(message)
        self.retryable = retryable


def http_post(url: str, payload: dict) -> tuple[int, dict]:
    request = urllib.request.Request(
        url, data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"}, method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as e:
        try:
            body = json.loads(e.read() or b"{}")
        except ValueError:
            body = {}
        return e.code, body
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        raise TelegramError(f"Нет связи с Telegram: {e}", retryable=True) from e


class Bot:
    def __init__(self, token: str, post: Transport = http_post):
        self._token = token
        self._post = post

    def call(self, method: str, **params) -> dict | list:
        code, body = self._post(f"{API}/bot{self._token}/{method}", params)
        if code == 200 and body.get("ok"):
            return body.get("result")
        description = body.get("description") or f"HTTP {code}"
        # 429 — слишком часто, 5xx — сбой Telegram: стоит повторить; 400/401/403 — не поможет
        raise TelegramError(f"Telegram: {description}", retryable=code == 429 or code >= 500)

    def me(self) -> dict:
        return self.call("getMe")

    def updates(self) -> list[dict]:
        return self.call("getUpdates", allowed_updates=["message"])

    def send(self, chat_id: int, html: str) -> None:
        self.call(
            "sendMessage", chat_id=chat_id, text=html, parse_mode="HTML",
            link_preview_options={"is_disabled": True},
        )


@dataclass(frozen=True)
class Chat:
    chat_id: int
    name: str
    username: str | None = None


def chats_from_updates(updates: list[dict]) -> list[Chat]:
    """Личные чаты тех, кто писал боту (Telegram хранит сообщения боту около суток)."""
    found: dict[int, Chat] = {}
    for u in updates:
        chat = (u.get("message") or {}).get("chat") or {}
        if chat.get("type") != "private" or "id" not in chat:
            continue
        name = " ".join(p for p in (chat.get("first_name"), chat.get("last_name")) if p) or str(chat["id"])
        found[chat["id"]] = Chat(chat["id"], name, chat.get("username"))
    return list(found.values())


@dataclass(frozen=True)
class Recipient:
    chat_id: int
    name: str
    profiles: tuple[str, ...]


def load_recipients(path: Path) -> list[Recipient]:
    if not path.is_file():
        return []
    data = json.loads(path.read_text())
    return [
        Recipient(int(r["chat_id"]), str(r.get("name", r["chat_id"])), tuple(r.get("profiles", [])))
        for r in data.get("recipients", [])
    ]


def save_recipients(path: Path, recipients: list[Recipient]) -> None:
    data = {"recipients": [
        {"chat_id": r.chat_id, "name": r.name, "profiles": list(r.profiles)} for r in recipients
    ]}
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    tmp.replace(path)
