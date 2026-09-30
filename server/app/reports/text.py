"""Строки отчёта: из них телефон рисует экран, а Telegram (подзадача 3-3) собирает сообщение.

Строка — {"spans": [{"text", "bold"}], "style"}; style: normal, big (крупно), warn (замечание), muted.
"""

from datetime import date

MONTHS = ["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября",
          "ноября", "декабря"]
WEEKDAYS = ["понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"]


class B(str):
    """Жирный кусок строки."""


def line(*parts: str, style: str = "normal") -> dict:
    return {
        "spans": [{"text": str(p), "bold": isinstance(p, B)} for p in parts if p != ""],
        "style": style,
    }


def section(title: str, lines: list[dict]) -> dict:
    return {"title": title, "lines": lines}


def fmt(value: float, digits: int = 1) -> str:
    """12.0 → «12», 12.34 → «12,3»; минус — настоящий минус."""
    text = f"{round(value, digits):.{digits}f}".rstrip("0").rstrip(".") if digits else f"{round(value):d}"
    if text in ("-0", ""):
        text = "0"
    return text.replace(".", ",").replace("-", "−")


def signed(value: float, digits: int = 1) -> str:
    text = fmt(value, digits)
    return text if text.startswith("−") or text == "0" else "+" + text


def percent(part: float, whole: float) -> int:
    return round(part / whole * 100) if whole else 0


def day_title(day: date) -> str:
    return f"{day.day} {MONTHS[day.month - 1]}, {WEEKDAYS[day.weekday()]}"


def hhmm(dt) -> str:
    return dt.strftime("%H:%M")


def duration(minutes: int) -> str:
    h, m = divmod(minutes, 60)
    return f"{h} ч {m} мин" if m else f"{h} ч"
