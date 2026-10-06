"""Отчёт → сообщения Telegram (ТЗ 17.5, 17.7): выжимка открытым текстом, подробности — в сворачиваемом блоке."""

import re
from html import escape

LIMIT = 4096  # длина сообщения Telegram; считаем с тегами — с запасом
KIND_TITLES = {"day": "Итоги дня", "week": "Итоги недели", "month": "Итоги месяца", "analysis": "Анализ Claude"}


def _line(line: dict) -> str:
    text = "".join(
        f"<b>{escape(s['text'])}</b>" if s.get("bold") else escape(s["text"]) for s in line["spans"]
    )
    style = line.get("style")
    if style == "warn":
        return f"⚠ {text}"
    if style == "muted":
        return f"<i>{text}</i>"
    return text


def _section(section: dict) -> list[str]:
    return [f"<b>{escape(section['title'])}</b>"] + [_line(line) for line in section["lines"]]


def _quote(lines: list[str]) -> str:
    return "<blockquote expandable>" + "\n".join(lines) + "</blockquote>"


QUOTE_OVERHEAD = len(_quote([]))


def render(report: dict, profile_name: str) -> list[str]:
    """Одно сообщение, если помещается; иначе выжимка и подробности следующими сообщениями."""
    title = f"<b>{escape(KIND_TITLES.get(report['kind'], 'Отчёт'))} · {escape(profile_name)} · {escape(report['title'])}</b>"
    head = "\n".join([title] + [_line(line) for line in report["summary"]])
    details: list[str] = []
    for section in report["sections"]:
        if details:
            details.append("")
        details += _section(section)
    if not details:
        return [head]
    whole = head + "\n\n" + _quote(details)
    if len(whole) <= LIMIT:
        return [whole]
    return [head] + [_quote(chunk) for chunk in _chunks(details, LIMIT - QUOTE_OVERHEAD)]


def _fit(line: str, limit: int) -> str:
    """Строка длиннее сообщения (на деле не бывает): без тегов и с обрезкой, не разрывая «&amp;»."""
    if len(line) <= limit:
        return line
    text = re.sub(r"<[^>]+>", "", line)[: limit - 1]
    amp = text.rfind("&")
    if amp >= 0 and ";" not in text[amp:]:
        text = text[:amp]
    return text + "…"


def _chunks(lines: list[str], limit: int) -> list[list[str]]:
    """Строки подряд, каждая часть не длиннее [limit] вместе с переводами строк."""
    chunks: list[list[str]] = [[]]
    size = 0
    for line in (_fit(x, limit) for x in lines):
        extra = len(line) + (1 if chunks[-1] else 0)
        if size + extra > limit and chunks[-1]:
            chunks.append([])
            size = 0
            extra = len(line)
        chunks[-1].append(line)
        size += extra
    return [c for c in chunks if c]
