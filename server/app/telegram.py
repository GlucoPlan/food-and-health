"""Telegram Bot API (ТЗ 17.7): бот только отправляет, команд не слушает.

Токен — FH_TELEGRAM_TOKEN в файле окружения сервера. Кто чьи отчёты получает и кому идёт
копия базы (ТЗ 17.8) — telegram.json рядом с ним:
    {"recipients": [{"chat_id": 123, "name": "Иван", "profiles": ["<id профиля>", ...]}], "backup_chat_id": 123}
Только стандартная библиотека: лишних зависимостей на сервере нет.
"""

import json
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

API = "https://api.telegram.org"
TIMEOUT = 30

# (url, поля, файлы {имя: (имя файла, байты)}) → (HTTP-код, ответ JSON); подменяется в тестах
Transport = Callable[[str, dict, dict | None], tuple[int, dict]]


class TelegramError(Exception):
    def __init__(self, message: str, retryable: bool):
        super().__init__(message)
        self.retryable = retryable


def _multipart(fields: dict, files: dict) -> tuple[bytes, str]:
    boundary = uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n'.encode()
                     + text.encode() + b"\r\n")
    for name, (filename, data) in files.items():
        mime = "image/png" if filename.endswith(".png") else "application/octet-stream"
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                     f"Content-Type: {mime}\r\n\r\n".encode() + data + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def http_post(url: str, payload: dict, files: dict | None = None) -> tuple[int, dict]:
    if files:
        body, content_type = _multipart(payload, files)
    else:
        body, content_type = json.dumps(payload).encode(), "application/json"
    request = urllib.request.Request(url, data=body, headers={"Content-Type": content_type}, method="POST")
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

    def call(self, method: str, files: dict | None = None, **params) -> dict | list:
        code, body = self._post(f"{API}/bot{self._token}/{method}", params, files)
        if code == 200 and body.get("ok"):
            return body.get("result")
        description = body.get("description") or f"HTTP {code}"
        # 429 — слишком часто, 5xx — сбой Telegram: стоит повторить; 400/401/403 — не поможет
        raise TelegramError(f"Telegram: {description}", retryable=code == 429 or code >= 500)

    def me(self) -> dict:
        return self.call("getMe")

    def updates(self) -> list[dict]:
        return self.call("getUpdates", allowed_updates=["message"])

    def send_photos(self, chat_id: int, photos: list[tuple[str, bytes]]) -> None:
        """Графики (ТЗ 17.6): одна картинка — фото с подписью, несколько — альбомом (до 10)."""
        for start in range(0, len(photos), 10):
            chunk = photos[start:start + 10]
            if len(chunk) == 1:
                title, png = chunk[0]
                self.call("sendPhoto", files={"photo": ("chart.png", png)}, chat_id=str(chat_id), caption=title)
                continue
            media = [{"type": "photo", "media": f"attach://p{i}", "caption": title} for i, (title, _) in enumerate(chunk)]
            files = {f"p{i}": (f"chart{i}.png", png) for i, (_, png) in enumerate(chunk)}
            self.call("sendMediaGroup", files=files, chat_id=str(chat_id), media=media)

    def send(self, chat_id: int, html: str, silent: bool = False) -> None:
        self.call(
            "sendMessage", chat_id=chat_id, text=html, parse_mode="HTML",
            link_preview_options={"is_disabled": True}, disable_notification=silent,
        )

    def send_document(self, chat_id: int, filename: str, data: bytes, caption: str, silent: bool = False) -> None:
        """Файл до 50 МБ (лимит Bot API); подпись — HTML."""
        self.call("sendDocument", files={"document": (filename, data)}, chat_id=str(chat_id),
                  caption=caption, parse_mode="HTML", disable_notification=silent)


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


def _load(path: Path) -> dict:
    return json.loads(path.read_text()) if path.is_file() else {}


def load_recipients(path: Path) -> list[Recipient]:
    data = _load(path)
    return [
        Recipient(int(r["chat_id"]), str(r.get("name", r["chat_id"])), tuple(r.get("profiles", [])))
        for r in data.get("recipients", [])
    ]


def load_backup_chat(path: Path) -> int | None:
    """Кому идут копия базы (ТЗ 17.8) и сообщения о сбоях анализа (17.9) — Ивану; None — никому."""
    chat = _load(path).get("backup_chat_id")
    return int(chat) if chat is not None else None


def save_recipients(path: Path, recipients: list[Recipient], backup_chat_id: int | None = None) -> None:
    data: dict = {"recipients": [
        {"chat_id": r.chat_id, "name": r.name, "profiles": list(r.profiles)} for r in recipients
    ]}
    if backup_chat_id is not None:
        data["backup_chat_id"] = backup_chat_id
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    tmp.replace(path)
