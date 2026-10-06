"""Анализ Claude (ТЗ 17.9): выгрузка, запуск, ответ в приложении и Telegram. Настоящий Claude не вызывается."""

import json
from datetime import date, datetime
from pathlib import Path

import pytest

from app import analysis
from app.daydata import TABLES, Family
from app.db import Store
from app.report_settings import ReportSettings
from app.send_reports import default_jobs, notify_analysis_failures, send
from app.telegram import Recipient

from .test_reports import DAY, at, body, item, meal, product, profile, put
from .test_telegram import FakeTelegram

SAMPLES = Path(__file__).resolve().parent.parent / "deploy" / "prompts"

ANSWER = """Главное: белка мало, **соль выше нормы**.
Вес снижается по плану.

## Вчера
- Завтрак — **молоко**, 120 ккал
* Ужин поздно

---
## Неделя
Средние калории 1900.

## Пустой раздел
"""


@pytest.fixture
def adir(settings):
    d = analysis.analysis_dir(settings.data_dir)
    (d / "prompts").mkdir(parents=True)
    for f in SAMPLES.glob("*.md"):
        (d / "prompts" / f.name).write_text(f.read_text())
    return d


@pytest.fixture
def family(client, settings):
    put(client, profile("me", "Я", target_weight_kg=85.0), profile("kid", "Дочь", sex="female",
        birth_date="2014-05-20", sd1_enabled=1, glucose_low=4.0, glucose_high=8.0),
        product("milk", "Молоко", 60, 3, 3.2, 4.7),
        meal("m1", at(DAY, "08:00"), "kid", glucose=6.5, dose=2), item("i1", "m1", "milk", 200, 120, carbs=9.4),
        *body("me"))
    return Family(Store(settings.db_path).records(TABLES))


# ---------- выгрузка ----------

def test_промт_общий_и_свой_по_флагу_сд1_или_по_имени(adir, family):
    kid = analysis.prompt_for(adir / "prompts", family.profiles["kid"])
    me = analysis.prompt_for(adir / "prompts", family.profiles["me"])
    assert kid.startswith((SAMPLES / "common.md").read_text().strip())
    assert "эндокринолог" in kid and "эндокринолог" not in me
    (adir / "prompts" / "Я.md").write_text("Свой промт Ивана")
    assert analysis.prompt_for(adir / "prompts", family.profiles["me"]).endswith("Свой промт Ивана")


def test_данные_вчера_неделя_месяц_и_профиль(family):
    text = analysis.build_input(family, family.profiles["kid"], DAY, ReportSettings())
    for part in ("=== Профиль ===", "=== Вчера: 29 сентября, вторник ===", "=== Последние 7 дней: 23–29 сентября ===",
                 "=== Последние 30 дней: 31 августа – 29 сентября ==="):
        assert part in text
    assert "Сахарный диабет 1 типа, целевой диапазон сахара 4–8 ммоль/л" in text
    assert "Пол: женский" in text and "Возраст: 12 лет" in text
    assert "Молоко" in text
    me = analysis.build_input(family, family.profiles["me"], DAY, ReportSettings())
    assert "Цель по весу: 85 кг, темп 0,5 кг в неделю" in me and "Последний вес: 95 кг" in me
    assert "Нормы в день:" in me


def test_выгрузка_задание_на_каждого_старые_удаляются(adir, family):
    (adir / "in").mkdir()
    (adir / "in" / "old.json").write_text("{}")
    ids = analysis.export(family, adir, DAY, ReportSettings())
    assert sorted(ids) == ["kid", "me"]
    assert sorted(p.name for p in (adir / "in").iterdir()) == ["kid.json", "me.json"]
    task = json.loads((adir / "in" / "kid.json").read_text())
    assert task["date"] == "2026-09-29" and task["name"] == "Дочь" and "эндокринолог" in task["prompt"]


# ---------- запуск ----------

def fake_claude(answers):
    """answers: имя профиля → (код, stdout, stderr)."""
    calls = []

    def runner(cmd, stdin, cwd):
        calls.append((cmd, stdin, cwd))
        name = next(n for n in answers if f"Профиль: {n}" in stdin)
        return answers[name]
    return runner, calls


NOW = lambda: datetime(2026, 9, 30, 4, 5)  # noqa: E731


def test_запуск_без_инструментов_ответ_сохраняется_задание_удаляется(adir, family):
    analysis.export(family, adir, DAY, ReportSettings())
    runner, calls = fake_claude({"Я": (0, ANSWER, ""), "Дочь": (0, "Всё хорошо", "")})
    assert analysis.run(adir, "/home/ivan/.local/bin/claude", runner, NOW, log=lambda _: None) == 0
    cmd, stdin, cwd = calls[0]
    assert cmd[:2] == ["/home/ivan/.local/bin/claude", "-p"]
    assert cmd[cmd.index("--tools") + 1] == "" and "--strict-mcp-config" in cmd and "--no-session-persistence" in cmd
    assert cmd[cmd.index("--system-prompt") + 1].startswith("Ты — внимательный диетолог")
    assert cwd == adir / "work"
    assert list((adir / "in").iterdir()) == []
    saved = analysis.latest(adir, "me")
    assert saved["text"] == ANSWER.strip() and saved["date"] == "2026-09-29"
    assert analysis.failures(adir, family.profiles, DAY) == []


def test_сбой_прошлый_анализ_остаётся_причина_записана(adir, family):
    analysis.export(family, adir, DAY, ReportSettings())
    runner, _ = fake_claude({"Я": (0, ANSWER, ""), "Дочь": (0, "Старый", "")})
    analysis.run(adir, "claude", runner, NOW, log=lambda _: None)

    next_day = date(2026, 9, 30)
    analysis.export(family, adir, next_day, ReportSettings())
    runner, _ = fake_claude({"Я": (1, "", "загрузка\nClaude usage limit reached"), "Дочь": (0, "  ", "")})
    assert analysis.run(adir, "claude", runner, NOW, log=lambda _: None) == 2
    assert analysis.latest(adir, "me")["date"] == "2026-09-29"  # хранится последний удачный
    assert analysis.failures(adir, family.profiles, next_day) == [
        "Дочь: пустой ответ", "Я: Claude usage limit reached"]
    assert analysis.failures(adir, family.profiles, date(2026, 10, 1)) == ["Дочь: не запускался", "Я: не запускался"]


# ---------- ответ ----------

def test_markdown_в_отчёт_выжимка_разделы_жирное_списки():
    rep = analysis.to_report({"profile_id": "me", "name": "Я", "date": "2026-09-29", "text": ANSWER})
    assert rep["kind"] == "analysis" and rep["title"] == "по данным за 29 сентября, вторник"
    assert [[s["text"] for s in ln["spans"]] for ln in rep["summary"]] == [
        ["Главное: белка мало, ", "соль выше нормы", "."], ["Вес снижается по плану."]]
    assert rep["summary"][0]["spans"][1]["bold"] is True
    assert [s["title"] for s in rep["sections"]] == ["Вчера", "Неделя"]
    vchera = ["".join(s["text"] for s in ln["spans"]) for ln in rep["sections"][0]["lines"]]
    assert vchera == ["• Завтрак — молоко, 120 ккал", "• Ужин поздно"]


def test_эндпоинт_последний_анализ_или_пусто(client, settings, adir):
    r = client.get("/reports/analysis", params={"profile_id": "me"})
    assert r.status_code == 200 and r.json()["empty"] is True
    (adir / "out").mkdir()
    (adir / "out" / "me.json").write_text(json.dumps(
        {"profile_id": "me", "name": "Я", "date": "2026-09-29", "text": ANSWER}))
    rep = client.get("/reports/analysis", params={"profile_id": "me", "date": "2026-09-01"}).json()
    assert rep["kind"] == "analysis" and not rep["empty"] and rep["sections"][0]["title"] == "Вчера"
    assert client.get("/reports/analysis", params={"profile_id": "../me"}).json()["empty"] is True


# ---------- Telegram ----------

def test_анализ_после_отчётов_своего_профиля(adir, family, settings, tmp_path):
    analysis.export(family, adir, DAY, ReportSettings())
    runner, _ = fake_claude({"Я": (0, ANSWER, ""), "Дочь": (1, "", "Not logged in")})
    analysis.run(adir, "claude", runner, NOW, log=lambda _: None)
    tg = FakeTelegram()
    jobs = default_jobs(DAY, with_analysis=True)
    failed = send(Store(settings.db_path), [Recipient(1, "Иван", ("me", "kid"))], tg.bot(), jobs,
                  tmp_path / "sent.json", sleep=pytest.fail, log=lambda _: None, analysis_dir=adir)
    assert failed == 0
    heads = [text.split("\n", 1)[0] for _, text in tg.sent]
    assert heads == [
        "<b>Итоги дня · Я · 29 сентября, вторник</b>",
        "<b>Анализ Claude · Я · по данным за 29 сентября, вторник</b>",
        "<b>Итоги дня · Дочь · 29 сентября, вторник</b>",
    ]
    assert "<b>соль выше нормы</b>" in tg.sent[1][1]

    # Сбой — Ивану один раз
    store = Store(settings.db_path)
    notify_analysis_failures(store, tg.bot(), 1, adir, DAY, tmp_path / "sent.json", log=lambda _: None)
    notify_analysis_failures(store, tg.bot(), 1, adir, DAY, tmp_path / "sent.json", log=lambda _: None)
    errors = [t for _, t in tg.sent if t.startswith("Анализ Claude за")]
    assert len(errors) == 1 and "Дочь: Not logged in" in errors[0] and "Я:" not in errors[0]


def test_без_анализа_задания_нет():
    assert [j.kind for j in default_jobs(DAY)] == ["day"]
    assert [j.kind for j in default_jobs(DAY, with_analysis=True)] == ["day", "analysis"]
    assert [j.kind for j in default_jobs(DAY, week_only=True, with_analysis=True)] == ["week"]


def test_выгрузка_без_промтов_молча_выходит(settings, capsys):
    assert analysis.main(["--data-dir", str(settings.data_dir), "export"]) == 0
    assert "не включён" in capsys.readouterr().out
