"""Недельный отчёт (ТЗ 17.6): расчёт, текст, графики, настройки, рассылка."""

import base64
import json
from datetime import date, timedelta

import pytest

from app import report_settings
from app.db import Store
from app.report_settings import MealSlot, ReportSettings
from app.reports import period
from app.send_reports import Job, default_jobs, send
from app.telegram import Bot, Recipient

from .test_reports import ME, KID, at, item, meal, product, profile, put, section, summary, text
from .test_telegram import FakeTelegram

MON = date(2026, 9, 21)
SUN = date(2026, 9, 27)


def week(client, profile_id=ME, day=MON) -> dict:
    r = client.get("/reports/week", params={"profile_id": profile_id, "date": day.isoformat()})
    assert r.status_code == 200, r.text
    return r.json()


def weight(id_, day, kg, hm="07:00", pid=ME):
    return ("weight", id_, {"profile_id": pid, "measured_at": at(day, hm), "weight_kg": kg})


def food(client, day, kcal, carbs=0.0, pid=ME, hm="13:00", glucose=None, name="bread"):
    mid = f"m-{pid}-{day.isoformat()}-{hm}"
    put(client, meal(mid, at(day, hm), pid, glucose=glucose), item(f"i-{mid}", mid, name, 100, kcal, 10, 10, carbs))


@pytest.fixture
def me(client):
    # Норма «Я» при весе 95: 2356 ккал (shared/norm-cases.json)
    put(client, profile(ME, "Я", target_weight_kg=80.0),
        ("height", "h1", {"profile_id": ME, "measured_at": at(date(2026, 1, 1), "09:00"), "height_cm": 180.0}),
        weight("w0", date(2026, 9, 1), 95.0),
        product("bread", "Хлеб", 250, 8, 3, 48), product("milk", "Молоко", 60, 3, 3.2, 4.7))
    return client


def test_границы_недели():
    assert period.week_of(date(2026, 9, 24)) == (MON, SUN)
    assert period.week_of(MON) == (MON, SUN)
    assert period.week_of(SUN) == (MON, SUN)
    assert period.period_title(MON, SUN) == "21–27 сентября"
    assert period.period_title(date(2026, 9, 28), date(2026, 10, 4)) == "28 сентября – 4 октября"


def test_средние_только_по_дням_с_едой_перебор_и_недобор(me):
    food(me, MON, 2000)
    food(me, MON + timedelta(days=1), 3000)   # 127 % — перебор
    food(me, MON + timedelta(days=2), 1000)   # 42 % — недобор
    rep = week(me)
    s = summary(rep)
    assert s[0] == "Еда записана в 3 днях из 7"
    assert rep["incomplete"] is True
    assert "Калории в среднем: 2000 из 2356 (85 %)" in s
    assert "Перебор калорий: 1 день · недобор: 1 день" in s
    days = section(rep, "Питание по дням")
    assert days[0] == "Пн 21.09 — 2000 ккал (85 %), Б 10 · Ж 10 · У 0"
    assert days[3] == "Чт 24.09 — не записано"


def test_сравнение_с_прошлой_неделей(me):
    food(me, MON - timedelta(days=7), 1800)
    food(me, MON, 2000)
    assert "Калории в среднем: 2000 из 2356 (85 %) · ↑200 к прошлой неделе" in summary(week(me))


def test_вес_изменение_темп_и_прогноз(me):
    put(me, weight("a", MON, 95.0), weight("a2", MON, 96.0, "21:00"), weight("b", SUN, 94.0))
    s = summary(week(me))
    # Первый вес дня: 95 → 94 за 6 дней, темп −1 × 7/6 = −1,17 кг в неделю; до цели 14 кг → 84 дня
    assert "Вес: 95 → 94 кг (−1)" in s
    assert "Темп: −1,2 кг в неделю, цель −0,5 · до цели 14 кг, при таком темпе — к 20 декабря 2026" in s


def test_сон_среднее_время_через_полночь(me):
    put(me,
        ("sleep", "s1", {"profile_id": ME, "asleep_at": at(MON, "23:30"), "woke_at": at(MON + timedelta(days=1), "07:30"),
                         "quality": "good", "source": "manual"}),
        ("sleep", "s2", {"profile_id": ME, "asleep_at": at(MON + timedelta(days=2), "00:30"),
                         "woke_at": at(MON + timedelta(days=2), "07:30"), "quality": "bad", "source": "manual"}))
    rep = week(me)
    assert "Сон в среднем: 7 ч 30 мин, отбой ~00:00, подъём ~07:30" in summary(rep)
    lines = section(rep, "Сон")
    assert "Время отбоя гуляет в среднем на ±30 мин" in lines
    assert "Средняя оценка: нормально" in lines


def test_частые_продукты_и_источники_калорий(me):
    for i in range(3):
        food(me, MON + timedelta(days=i), 250)
    food(me, MON, 60, hm="08:00", name="milk")
    top = section(week(me), "Частые продукты и блюда")
    assert top[0] == "Чаще всего: Хлеб — 3 раза, Молоко — 1 раз"
    assert top[1] == "Больше всего калорий: Хлеб 93 %, Молоко 7 %"


def test_сд1_углеводы_по_времени_и_сахар_в_диапазоне(client):
    put(client, profile(KID, "Дочь", sex="female", birth_date="2014-05-20", sd1_enabled=1,
                        glucose_low=3.9, glucose_high=10.0),
        product("bread", "Хлеб", 250, 8, 3, 48))
    food(client, MON, 200, carbs=40, pid=KID, hm="08:00", glucose=6.0)
    food(client, MON, 300, carbs=60, pid=KID, hm="13:00", glucose=11.0)
    food(client, MON + timedelta(days=1), 200, carbs=20, pid=KID, hm="09:30", glucose=5.0)
    food(client, MON + timedelta(days=1), 100, carbs=10, pid=KID, hm="22:00")
    rep = week(client, KID)
    big = [text(ln) for ln in rep["summary"] if ln["style"] == "big"]
    assert big == ["Углеводы в день: 65 г"]
    assert "Сахар при приёмах: в среднем 7,3, в диапазоне 67 % (2 из 3)" in summary(rep)
    assert section(rep, "Углеводы по времени приёма") == [
        "Завтрак: в среднем 30 г (2 приёма)", "Обед: в среднем 60 г (1 приём)",
        "Поздний приём: в среднем 10 г (1 приём)",
    ]
    assert {i["id"] for i in rep["images"]} >= {"carbs", "glucose", "kcal"}


def test_время_приёмов_из_настроек(client, settings, tmp_path):
    from fastapi.testclient import TestClient
    from app.config import Settings
    from app.main import create_app
    from .conftest import KEY

    cfg = tmp_path / "reports.json"
    cfg.write_text(json.dumps({"meal_slots": [{"name": "Утро", "until": "12:00"}, {"name": "Остальное"}]}))
    custom = TestClient(create_app(Settings(KEY, settings.data_dir, cfg)), headers={"X-Family-Key": KEY})
    put(custom, profile(KID, "Дочь", sex="female", birth_date="2014-05-20", sd1_enabled=1),
        product("bread", "Хлеб", 250, 8, 3, 48))
    food(custom, MON, 200, carbs=40, pid=KID, hm="11:30")
    food(custom, MON, 200, carbs=20, pid=KID, hm="12:30")
    assert section(week(custom, KID), "Углеводы по времени приёма") == [
        "Утро: в среднем 40 г (1 приём)", "Остальное: в среднем 20 г (1 приём)",
    ]


def test_настройки_отчётов_ошибка_в_файле_значения_по_умолчанию(tmp_path, capsys):
    bad = tmp_path / "r.json"
    bad.write_text(json.dumps({"meal_slots": [{"name": "А", "until": "12:00"}, {"name": "Б", "until": "11:00"},
                                              {"name": "В"}]}))
    assert report_settings.load(bad) == ReportSettings()
    assert "по умолчанию" in capsys.readouterr().err
    assert report_settings.load(tmp_path / "нет.json") == ReportSettings()
    ok = report_settings.parse({"meal_slots": [{"name": "А", "until": "10:00"}, {"name": "Б"}]})
    assert ok.meal_slots == (MealSlot("А", 600), MealSlot("Б", None))
    assert ok.slot(599) == "А" and ok.slot(600) == "Б"


def test_графики_png_и_только_при_данных(me):
    assert week(me)["images"] == []
    food(me, MON, 2000)
    put(me, weight("a", MON, 95.0), weight("b", SUN, 94.0),
        ("blood_pressure", "bp", {"profile_id": ME, "measured_at": at(MON, "08:00"), "systolic": 125,
                                  "diastolic": 80, "pulse": 70}))
    images = {i["id"]: base64.b64decode(i["png"]) for i in week(me)["images"]}
    assert set(images) == {"weight", "kcal", "pressure"}  # сна нет — и графика нет
    assert all(png.startswith(b"\x89PNG") for png in images.values())


def test_пустая_неделя(me):
    rep = week(me, day=date(2026, 8, 3))
    assert rep["empty"] is True and rep["images"] == []


def test_ошибки_запроса(me):
    assert me.get("/reports/week", params={"profile_id": "нет", "date": "2026-09-21"}).status_code == 404


# ---------- рассылка ----------

def test_недельный_уходит_после_воскресенья():
    assert [j.kind for j in default_jobs(SUN, False)] == ["day", "week"]
    assert [j.kind for j in default_jobs(MON, False)] == ["day"]
    assert default_jobs(date(2026, 9, 24), True) == [Job("week", MON, SUN)]


def test_недельный_в_telegram_текст_и_альбом_без_дублей(me, settings, tmp_path):
    food(me, MON, 2000)
    put(me, weight("a", MON, 95.0), weight("b", SUN, 94.0))
    tg = FakeTelegram()
    photos = []

    def post(url, payload, files=None):
        if url.endswith("sendMediaGroup"):
            photos.append((payload["chat_id"], [m["caption"] for m in payload["media"]], sorted(files)))
            return 200, {"ok": True, "result": []}
        return tg.post(url, payload, files)

    store = Store(settings.db_path)
    who = [Recipient(1, "Иван", (ME,))]
    jobs = default_jobs(SUN, False)
    assert send(store, who, Bot("t", post=post), jobs, tmp_path / "sent.json", log=lambda _: None) == 0
    heads = [t.split("\n", 1)[0] for _, t in tg.sent]
    assert heads == ["<b>Итоги дня · Я · 27 сентября, воскресенье</b>", "<b>Итоги недели · Я · 21–27 сентября</b>"]
    assert photos == [("1", ["Вес", "Калории"], ["p0", "p1"])]

    send(store, who, Bot("t", post=post), jobs, tmp_path / "sent.json", log=lambda _: None)
    assert len(tg.sent) == 2 and len(photos) == 1


# ---------- месяц ----------

def month(client, profile_id=ME, day=date(2026, 9, 15)) -> dict:
    r = client.get("/reports/month", params={"profile_id": profile_id, "date": day.isoformat()})
    assert r.status_code == 200, r.text
    return r.json()


def test_границы_месяца():
    assert period.month_of(date(2026, 9, 15)) == (date(2026, 9, 1), date(2026, 9, 30))
    assert period.month_of(date(2026, 12, 31)) == (date(2026, 12, 1), date(2026, 12, 31))
    assert period.month_of(date(2026, 2, 10)) == (date(2026, 2, 1), date(2026, 2, 28))
    assert period.month_of(date(2028, 2, 10)) == (date(2028, 2, 1), date(2028, 2, 29))
    assert period.month_title(date(2026, 9, 1)) == "Сентябрь 2026"


def test_месяц_сравнение_с_прошлым_и_по_неделям(me):
    food(me, date(2026, 8, 20), 1500)
    food(me, date(2026, 9, 1), 2000)
    food(me, date(2026, 9, 2), 2400)
    food(me, date(2026, 9, 30), 1900)
    put(me, weight("a", date(2026, 9, 2), 95.0), weight("b", date(2026, 9, 6), 94.4),
        weight("c", date(2026, 9, 30), 93.0))
    rep = month(me)
    assert rep["title"] == "Сентябрь 2026"
    s = summary(rep)
    assert s[0] == "Еда записана в 3 днях из 30"
    # Норма — средняя по дням с едой: к 30-му вес 93 кг, и норма ниже (2356, 2356, 2325)
    assert "Калории в среднем: 2100 из 2346 (90 %) · ↑600 к прошлому месяцу" in s
    # 1 сентября 2026 — вторник: первая неделя 01–06, последняя — только 28–30
    weeks = section(rep, "По неделям")
    assert weeks[0] == "01.09–06.09 — 2200 ккал в день, вес 95 → 94,4"
    assert weeks[1] == "07.09–13.09 — еда не записана"
    assert weeks[-1] == "28.09–30.09 — 1900 ккал в день, вес 93"
    assert len(weeks) == 5
    assert len(section(rep, "Питание по дням")) == 30


def test_месяц_топ_15(me):
    names = [f"p{i}" for i in range(20)]
    put(me, *[product(n, f"Продукт {i:02d}", 100, 1, 1, 1) for i, n in enumerate(names)])
    for i, n in enumerate(names):
        for k in range(20 - i):
            food(me, date(2026, 9, 1) + timedelta(days=k), 100, hm=f"{8 + i // 6:02d}:{(i % 6) * 10:02d}", name=n)
    top = section(month(me), "Частые продукты и блюда")[0]
    assert top.count(" — ") == 15
    assert "Продукт 00 — 20 раз" in top and "Продукт 14" in top and "Продукт 15" not in top


def test_месячный_в_рассылке():
    kinds = [j.kind for j in default_jobs(date(2026, 9, 30))]
    assert kinds == ["day", "month"]
    # 31 мая 2026 — воскресенье: и неделя, и месяц — по порядку
    assert [j.kind for j in default_jobs(date(2026, 5, 31))] == ["day", "week", "month"]
    assert [j.kind for j in default_jobs(date(2026, 9, 15))] == ["day"]
    assert default_jobs(date(2026, 9, 15), month_only=True) == [Job("month", date(2026, 9, 1), date(2026, 9, 30))]


def test_месячный_в_telegram(me, settings, tmp_path):
    food(me, date(2026, 9, 1), 2000)
    tg = FakeTelegram()
    photos = []

    def post(url, payload, files=None):
        if url.endswith("sendPhoto"):
            photos.append(payload["caption"])
            return 200, {"ok": True, "result": {}}
        if url.endswith("sendMediaGroup"):
            photos.extend(m["caption"] for m in payload["media"])
            return 200, {"ok": True, "result": []}
        return tg.post(url, payload, files)

    jobs = [Job("month", date(2026, 9, 1), date(2026, 9, 30))]
    assert send(Store(settings.db_path), [Recipient(1, "Иван", (ME,))], Bot("t", post=post), jobs,
                tmp_path / "sent.json", log=lambda _: None) == 0
    assert tg.sent[0][1].startswith("<b>Итоги месяца · Я · Сентябрь 2026</b>")
    assert photos == ["Вес", "Калории"]  # взвешивание 1 сентября из фикстуры и еда
