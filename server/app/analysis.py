"""Анализ Claude (ТЗ 17.9): ночная выгрузка, запуск Claude Code, последний ответ на каждого.

Папка обмена — analysis/ в папке данных (группа foodhealth, в неё входит пользователь ivan):
    prompts/  common.md — общий промт; <имя профиля>.md — свой, иначе sd1.md (Дневник СД1) или adult.md
    in/       <id профиля>.json — задание: промт и данные; пишет выгрузка, удаляет запуск
    out/      <id профиля>.json — последний удачный анализ; <id профиля>.status.json — итог последнего запуска
    work/     пустая рабочая папка Claude Code

    python -m app.analysis export [--date ГГГГ-ММ-ДД]   — 4:00, от имени сервиса: задания на каждого (за вчера)
    python -m app.analysis run --claude ПУТЬ              — сразу после, от имени ivan: Claude Code по очереди
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Callable

from .daydata import MSK, num

DIR_NAME = "analysis"
TIMEOUT = 15 * 60
ERROR_LIMIT = 300


def analysis_dir(data_dir: Path) -> Path:
    return data_dir / DIR_NAME


def enabled(adir: Path) -> bool:
    """Анализ включён, если install.sh нашёл Claude Code и завёл промты."""
    return (adir / "prompts" / "common.md").is_file()


def _write_json(path: Path, data: dict) -> None:
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=1))
    tmp.replace(path)


def _read_json(path: Path) -> dict | None:
    try:
        return json.loads(path.read_text())
    except (FileNotFoundError, ValueError):
        return None


# ---------- выгрузка ----------

def prompt_for(prompts: Path, profile: dict) -> str:
    """Общий промт и свой: по имени профиля, иначе по флагу «Дневник СД1»."""
    own = prompts / f"{profile.get('name', '')}.md"
    if not own.is_file():
        own = prompts / ("sd1.md" if num(profile.get("sd1_enabled")) else "adult.md")
    parts = [(prompts / "common.md").read_text().strip()]
    if own.is_file():
        parts.append(own.read_text().strip())
    return "\n\n".join(parts)


def plain(report: dict) -> str:
    """Отчёт → простой текст для Claude: выжимка, затем разделы."""
    def text(ln: dict) -> str:
        t = "".join(s["text"] for s in ln["spans"])
        return f"⚠ {t}" if ln.get("style") == "warn" else t

    out = [text(ln) for ln in report["summary"]]
    for sec in report["sections"]:
        out += ["", f"[{sec['title']}]"] + [text(ln) for ln in sec["lines"]]
    return "\n".join(out)


SEX = {"male": "мужской", "female": "женский"}
ACTIVITY = {"sedentary": "сидячий образ жизни", "light": "немного активности", "moderate": "умеренная активность",
            "high": "много активности"}


def profile_text(family, profile: dict, day: date) -> str:
    from .norms import age_on
    from .reports.text import fmt

    lines = [f"Профиль: {profile.get('name', '')}"]
    if profile.get("sex") in SEX:
        lines.append(f"Пол: {SEX[profile['sex']]}")
    if profile.get("birth_date"):
        try:
            lines.append(f"Возраст: {age_on(date.fromisoformat(profile['birth_date']), day)} лет")
        except ValueError:
            pass
    heights = family.heights(profile["id"])
    if heights:
        lines.append(f"Рост: {fmt(num(heights[-1].get('height_cm')))} см")
    weights = family.weights(profile["id"])
    if weights:
        lines.append(f"Последний вес: {fmt(num(weights[-1].get('weight_kg')))} кг")
    if profile.get("activity") in ACTIVITY:
        lines.append(f"Активность: {ACTIVITY[profile['activity']]}")
    target = num(profile.get("target_weight_kg"), None)
    if target:
        pace = num(profile.get("weight_pace_kg"), 0.5)
        lines.append(f"Цель по весу: {fmt(target)} кг, темп {fmt(pace)} кг в неделю")
    else:
        lines.append("Цель по весу: поддержание")
    if num(profile.get("sd1_enabled")):
        low, high = num(profile.get("glucose_low"), None), num(profile.get("glucose_high"), None)
        rng = f", целевой диапазон сахара {fmt(low)}–{fmt(high)} ммоль/л" if low and high else ""
        lines.append(f"Сахарный диабет 1 типа{rng}")
    n = family.norms(profile, day)
    f = n.final
    if f.kcal:
        lines.append(f"Нормы в день: {fmt(f.kcal, 0)} ккал, белки {fmt(f.protein or 0, 0)} г, "
                     f"жиры {fmt(f.fat or 0, 0)} г, углеводы {fmt(f.carbs or 0, 0)} г"
                     + (f", клетчатка от {fmt(n.fiber_min_g, 0)} г" if n.fiber_min_g else "")
                     + (f", соль до {fmt(n.salt_max_g)} г" if n.salt_max_g else ""))
    if n.missing:
        lines.append("Для расчёта норм не хватает: " + ", ".join(n.missing))
    return "\n".join(lines)


def build_input(family, profile: dict, day: date, settings) -> str:
    """Данные для Claude: профиль и нормы, вчерашний день подробно, сводки за 7 и 30 дней."""
    from .reports import daily, period
    from .reports.text import day_title

    week = period.build(family, profile, day - timedelta(days=6), day, settings, kind="days")
    month = period.build(family, profile, day - timedelta(days=29), day, settings, kind="days")
    return "\n".join([
        f"Анализ за {day_title(day)} {day.year}. Время — московское. Нутриенты — по записям в приложении: "
        "витамины и минералы известны не для всех продуктов.",
        "",
        "=== Профиль ===", profile_text(family, profile, day),
        "", f"=== Вчера: {day_title(day)} ===", plain(daily.build(family, profile, day)),
        "", f"=== Последние 7 дней: {week['title']} ===", plain(week),
        "", f"=== Последние 30 дней: {month['title']} ===", plain(month),
    ])


def export(family, adir: Path, day: date, settings) -> list[str]:
    """Задания на каждый профиль. Возвращает id профилей."""
    inbox = adir / "in"
    inbox.mkdir(exist_ok=True)
    for old in inbox.glob("*.json"):
        old.unlink()
    ids = []
    for pid, profile in sorted(family.profiles.items(), key=lambda p: p[1].get("name", "")):
        _write_json(inbox / f"{pid}.json", {
            "profile_id": pid, "name": profile.get("name", ""), "date": day.isoformat(),
            "prompt": prompt_for(adir / "prompts", profile),
            "data": build_input(family, profile, day, settings),
        })
        ids.append(pid)
    return ids


# ---------- запуск Claude Code ----------

def claude_command(claude: str, prompt: str) -> list[str]:
    # Без инструментов, MCP, настроек, команд и сохранения сессии: Claude только читает данные и отвечает
    return [claude, "-p", "--tools", "", "--strict-mcp-config", "--setting-sources", "",
            "--disable-slash-commands", "--no-session-persistence", "--system-prompt", prompt]


Runner = Callable[[list[str], str, Path], tuple[int, str, str]]


def run_process(cmd: list[str], stdin: str, cwd: Path) -> tuple[int, str, str]:
    try:
        p = subprocess.run(cmd, input=stdin, capture_output=True, text=True, cwd=cwd, timeout=TIMEOUT)
    except subprocess.TimeoutExpired:
        return -1, "", f"нет ответа за {TIMEOUT // 60} мин"
    except OSError as e:
        return -1, "", f"не запускается: {e}"
    return p.returncode, p.stdout, p.stderr


def _short(text: str) -> str:
    lines = [ln.strip() for ln in text.strip().splitlines() if ln.strip()]
    last = lines[-1] if lines else "без описания"
    return last[:ERROR_LIMIT]


def run(adir: Path, claude: str, runner: Runner = run_process, now: Callable[[], datetime] = lambda: datetime.now(MSK),
        log: Callable[[str], None] = print) -> int:
    """Claude Code по каждому заданию, по очереди. Возвращает число неудачных."""
    out = adir / "out"
    out.mkdir(exist_ok=True)
    work = adir / "work"
    work.mkdir(exist_ok=True)
    failed = 0
    for task_file in sorted((adir / "in").glob("*.json")):
        task = _read_json(task_file)
        task_file.unlink(missing_ok=True)
        if task is None:
            continue
        pid, name = task["profile_id"], task["name"]
        started = time.monotonic()
        code, stdout, stderr = runner(claude_command(claude, task["prompt"]), task["data"], work)
        text = stdout.strip()
        status = {"date": task["date"], "finished_at": now().isoformat(timespec="seconds"), "ok": False}
        if code == 0 and text:
            _write_json(out / f"{pid}.json", {
                "profile_id": pid, "name": name, "date": task["date"],
                "created_at": status["finished_at"], "text": text,
            })
            status["ok"] = True
            log(f"{name}: анализ готов за {round(time.monotonic() - started)} с, {len(text)} знаков")
        else:
            # Ошибку Claude Code (лимит, вход) обычно видно в последней строке
            status["error"] = _short(stderr or stdout) if code != 0 else "пустой ответ"
            failed += 1
            log(f"{name}: анализ не удался (код {code}): {status['error']}")
        _write_json(out / f"{pid}.status.json", status)
    return failed


# ---------- результат: приложение и Telegram ----------

BOLD = re.compile(r"\*\*(.+?)\*\*")
HEADING = re.compile(r"^#{1,6}\s+(.*)$")
BULLET = re.compile(r"^[-*•]\s+(.*)$")


def _spans(text: str) -> list[dict]:
    spans, pos = [], 0
    for m in BOLD.finditer(text):
        if m.start() > pos:
            spans.append({"text": text[pos:m.start()], "bold": False})
        spans.append({"text": m.group(1), "bold": True})
        pos = m.end()
    if pos < len(text):
        spans.append({"text": text[pos:], "bold": False})
    return spans


def to_report(analysis: dict) -> dict:
    """Ответ Claude (markdown) → отчёт: текст до первого заголовка — выжимка, заголовки «##» — разделы."""
    from .reports.text import day_title

    day = date.fromisoformat(analysis["date"])
    summary: list[dict] = []
    sections: list[dict] = []
    for raw in analysis["text"].splitlines():
        s = raw.strip()
        if not s or set(s) <= {"-", "*", "_"}:
            continue
        if m := HEADING.match(s):
            sections.append({"title": BOLD.sub(r"\1", m.group(1)).strip(), "lines": []})
            continue
        if m := BULLET.match(s):
            s = "• " + m.group(1)
        line = {"spans": _spans(s), "style": "normal"}
        (sections[-1]["lines"] if sections else summary).append(line)
    sections = [s for s in sections if s["lines"]]
    return {
        "kind": "analysis", "date": analysis["date"], "profile_id": analysis["profile_id"],
        "profile_name": analysis.get("name", ""), "title": f"по данным за {day_title(day)}",
        "incomplete": False, "empty": False, "summary": summary, "sections": sections, "images": [],
    }


def latest(adir: Path, profile_id: str) -> dict | None:
    """Последний удачный анализ профиля."""
    if not re.fullmatch(r"[\w-]+", profile_id):
        return None
    return _read_json(adir / "out" / f"{profile_id}.json")


def report_for(adir: Path, profile_id: str, day: date | None = None) -> dict | None:
    """Анализ в виде отчёта; [day] — только если он за этот день."""
    a = latest(adir, profile_id)
    if a is None or (day is not None and a.get("date") != day.isoformat()):
        return None
    return to_report(a)


def empty_report(profile_id: str) -> dict:
    return {
        "kind": "analysis", "date": "", "profile_id": profile_id, "title": "Анализа пока нет",
        "incomplete": False, "empty": True, "images": [], "sections": [],
        "summary": [{"spans": [{"text": "Claude разбирает данные ночью, анализ появляется к утру", "bold": False}],
                     "style": "muted"}],
    }


def failures(adir: Path, profiles: dict[str, dict], day: date) -> list[str]:
    """Чей анализ за [day] не получился: «Имя: причина»."""
    result = []
    for pid, profile in sorted(profiles.items(), key=lambda p: p[1].get("name", "")):
        status = _read_json(adir / "out" / f"{pid}.status.json")
        if status is None or status.get("date") != day.isoformat():
            reason = "не запускался"
        elif status.get("ok"):
            continue
        else:
            reason = status.get("error", "ошибка")
        result.append(f"{profile.get('name', pid)}: {reason}")
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m app.analysis")
    parser.add_argument("--data-dir", type=Path, default=Path(os.environ.get("FH_DATA_DIR", "data")))
    sub = parser.add_subparsers(dest="command", required=True)
    e = sub.add_parser("export", help="задания на каждый профиль")
    e.add_argument("--date", type=date.fromisoformat, help="день анализа; по умолчанию вчера по Москве")
    r = sub.add_parser("run", help="запустить Claude Code по заданиям")
    r.add_argument("--claude", required=True)
    args = parser.parse_args(argv)
    adir = analysis_dir(args.data_dir.resolve())

    if args.command == "export":
        from . import report_settings
        from .daydata import TABLES, Family
        from .db import Store

        if not enabled(adir):
            print("Анализ не включён: install.sh не нашёл Claude Code у пользователя ivan")
            return 0
        day = args.date or datetime.now(MSK).date() - timedelta(days=1)
        family = Family(Store(args.data_dir.resolve() / "fh.db").records(TABLES))
        settings = report_settings.load(Path(os.environ.get("FH_REPORTS_CONFIG", "/etc/foodhealth/reports.json")))
        ids = export(family, adir, day, settings)
        print(f"Задания на анализ за {day.isoformat()}: {len(ids)}")
        return 0
    return 1 if run(adir, args.claude) else 0


if __name__ == "__main__":
    sys.exit(main())
