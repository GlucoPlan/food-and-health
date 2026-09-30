"""Ежедневный отчёт (ТЗ 17.5) и расчёт нутриентов для него (5.3, 17.2)."""

import json
from datetime import date, datetime
from zoneinfo import ZoneInfo

import pytest

from app.nutrition import item_nutrients

MSK = ZoneInfo("Europe/Moscow")
DAY = date(2026, 9, 29)
ME = "p-me"
KID = "p-kid"


def at(day: date, hm: str) -> int:
    h, m = map(int, hm.split(":"))
    return int(datetime(day.year, day.month, day.day, h, m, tzinfo=MSK).timestamp() * 1000)


def put(client, *records, deleted=()):
    changes = [
        {"table": t, "id": i, "data": {"id": i, **d}, "updated_at": 1, "deleted": i in deleted}
        for t, i, d in records
    ]
    r = client.post("/sync", json={"device_id": "T", "cursor": 0, "changes": changes})
    assert r.status_code == 200, r.text


def report(client, profile=ME, day=DAY) -> dict:
    r = client.get("/reports/day", params={"profile_id": profile, "date": day.isoformat()})
    assert r.status_code == 200, r.text
    return r.json()


def text(line: dict) -> str:
    return "".join(s["text"] for s in line["spans"])


def summary(rep: dict) -> list[str]:
    return [text(line) for line in rep["summary"]]


def section(rep: dict, title: str) -> list[str]:
    return next([text(line) for line in s["lines"]] for s in rep["sections"] if s["title"] == title)


def profile(id_, name, **extra):
    return ("profile", id_, {
        "name": name, "sd1_enabled": 0, "show_xe": 0, "carbs_per_xe": 10.0, "deleted": 0,
        "sex": "male", "birth_date": "1985-03-15", "water_enabled": 0, "water_ml_per_kg": 30.0,
        "activity": "moderate", "weight_pace_kg": 0.5, **extra,
    })


def product(id_, name, kcal, protein, fat, carbs, micro=None, fiber=None, salt=None):
    return ("product", id_, {
        "name": name, "kcal": kcal, "protein": protein, "fat": fat, "carbs": carbs, "fiber": fiber, "salt": salt,
        "micro": json.dumps(micro or {}), "deleted": 0,
    })


def meal(id_, eaten_at, profile_id=ME, glucose=None, dose=None):
    return ("meal", id_, {"profile_id": profile_id, "eaten_at": eaten_at, "glucose": glucose,
                          "insulin_dose": dose, "notes": None, "deleted": 0})


def item(id_, meal_id, product_id, weight, kcal, protein=0.0, fat=0.0, carbs=0.0):
    return ("meal_item", id_, {
        "meal_id": meal_id, "type": "product", "product_id": product_id, "dish_version_id": None,
        "weight_g": weight, "pieces": None, "snapshot_kcal": kcal, "snapshot_protein": protein,
        "snapshot_fat": fat, "snapshot_carbs": carbs, "deleted": 0,
    })


def body(profile_id=ME):
    return [
        ("height", "h1", {"profile_id": profile_id, "measured_at": at(date(2026, 1, 1), "09:00"), "height_cm": 180.0}),
        ("weight", "w1", {"profile_id": profile_id, "measured_at": at(DAY, "07:00"), "weight_kg": 95.0}),
    ]


@pytest.fixture
def family(client):
    put(client, profile(ME, "Я", target_weight_kg=80.0), *body(),
        product("milk", "Молоко", 60, 3, 3.2, 4.7, micro={"ca": 120, "vit_b12": 0.4}, fiber=0, salt=0.1),
        product("bread", "Хлеб", 250, 8, 3, 48, fiber=6, salt=1.2))
    return client


# ---------- нутриенты ----------

def test_пункт_блюда_считает_витамины_по_варке():
    products = {
        "a": {"kcal": 100, "micro": json.dumps({"fe": 2}), "fiber": 1, "salt": 0},
        "b": {"kcal": 50, "micro": "{}", "fiber": 3, "salt": 1},
    }
    versions = {"v": {"id": "v", "net_weight_g": 500}}
    ingredients = {"v": [{"product_id": "a", "weight_g": 200}, {"product_id": "b", "weight_g": 400}]}
    it = {"type": "dish", "dish_version_id": "v", "weight_g": 250, "snapshot_kcal": 200, "snapshot_protein": 5,
          "snapshot_fat": 1, "snapshot_carbs": 30}
    n = item_nutrients(it, products, versions, ingredients)
    # Варка: fe 4, клетчатка 2 + 12 = 14, соль 4 на 500 г; порция — половина
    assert n.kcal == 200  # КБЖУ — из снимка
    assert n.micro["fe"] == pytest.approx(2.0)
    assert n.fiber == pytest.approx(7.0)
    assert n.salt == pytest.approx(2.0)
    # Витамины известны у продукта «a»: 200 из 400 ккал варки → половина калорий пункта
    assert n.micro_kcal == pytest.approx(100.0)


def test_продукта_нет_в_базе_только_снимок():
    it = {"type": "product", "product_id": "нет", "weight_g": 100, "snapshot_kcal": 90}
    n = item_nutrients(it, {}, {}, {})
    assert n.kcal == 90 and n.micro == {} and n.micro_kcal == 0


# ---------- отчёт ----------

def test_калории_и_бжу_с_нормой(family):
    put(family, meal("m1", at(DAY, "08:00")), item("i1", "m1", "milk", 200, 120, 6, 6.4, 9.4),
        meal("m2", at(DAY, "13:00")), item("i2", "m2", "bread", 100, 250, 8, 3, 48))
    rep = report(family)
    s = summary(rep)
    # Норма: 2356 ккал (пример из shared/norm-cases.json)
    assert "Калории: 370 из 2356 (16 %)" in s
    assert "Белки 14 из 118 г · Жиры 9 из 79 г · Углеводы 57 из 295 г" in s
    assert rep["incomplete"] is True  # меньше половины нормы
    assert s[0] == "Возможно, данные неполные"
    meals = section(rep, "Приёмы пищи")
    assert meals[0] == "08:00 — 120 ккал"
    assert "Молоко — 200 г: 120 ккал, Б 6 · Ж 6,4 · У 9,4" in meals


def test_сутки_по_москве(family):
    put(family, meal("late", at(DAY, "23:30")), item("i", "late", "milk", 100, 60))
    assert "23:30 — 60 ккал" in section(report(family), "Приёмы пищи")
    next_day = report(family, day=date(2026, 9, 30))
    assert all(s["title"] != "Приёмы пищи" for s in next_day["sections"])


def test_удалённые_приём_и_пункт_не_считаются_удалённый_продукт_называется(family):
    put(family, meal("m1", at(DAY, "08:00")), item("i1", "m1", "milk", 200, 120),
        item("i2", "m1", "bread", 100, 250), meal("gone", at(DAY, "09:00")), item("i3", "gone", "milk", 100, 60),
        deleted={"i2", "gone"})
    put(family, product("milk", "Молоко", 60, 3, 3.2, 4.7), deleted={"milk"})
    meals = section(report(family), "Приёмы пищи")
    assert meals[0] == "08:00 — 120 ккал"
    assert any(line.startswith("Молоко — 200 г") for line in meals)
    assert not any("09:00" in line for line in meals)


def test_вес_первый_за_день_и_изменения(family):
    put(family,
        ("weight", "w-evening", {"profile_id": ME, "measured_at": at(DAY, "21:00"), "weight_kg": 96.0}),
        ("weight", "w-yesterday", {"profile_id": ME, "measured_at": at(date(2026, 9, 28), "07:00"), "weight_kg": 95.4}),
        ("weight", "w-week", {"profile_id": ME, "measured_at": at(date(2026, 9, 21), "07:30"), "weight_kg": 96.1}))
    s = summary(report(family))
    assert "Вес: 95 кг (−0,4 к вчера, −1,1 за неделю) · до цели 15 кг" in s


def test_сон_закончившийся_в_этот_день(family):
    put(family, ("sleep", "s1", {"profile_id": ME, "asleep_at": at(date(2026, 9, 28), "23:40"),
                                 "woke_at": at(DAY, "06:50"), "quality": "good", "source": "manual"}))
    assert "Сон: 7 ч 10 мин (23:40–06:50), хорошо" in summary(report(family))
    assert not any(line.startswith("Сон") for line in summary(report(family, day=date(2026, 9, 28))))


def test_вода_с_нормой_по_весу(family):
    put(family, profile(ME, "Я", water_enabled=1, water_ml_per_kg=30.0),
        ("water", "a", {"profile_id": ME, "drunk_at": at(DAY, "10:00"), "ml": 1000}),
        ("water", "b", {"profile_id": ME, "drunk_at": at(DAY, "15:00"), "ml": 800}))
    assert "Вода: 1,8 из 2,9 л" in summary(report(family))


def test_давление_одно_и_несколько(family):
    put(family, ("blood_pressure", "b1", {"profile_id": ME, "measured_at": at(DAY, "08:10"),
                                          "systolic": 128, "diastolic": 82, "pulse": 70}))
    assert "Давление: 128/82, пульс 70" in summary(report(family))
    put(family, ("blood_pressure", "b2", {"profile_id": ME, "measured_at": at(DAY, "20:00"),
                                          "systolic": 120, "diastolic": 78, "pulse": None}))
    rep = report(family)
    assert "Давление: в среднем 124/80 (120–128 / 78–82, 2 изм.)" in summary(rep)
    assert section(rep, "Давление") == ["08:10 — 128/82, пульс 70", "20:00 — 120/78"]


def test_замечания_самые_заметные_первыми_не_больше_трёх(family):
    # 2 приёма, 1200 ккал из 2356 (51 %), белка 10 г (8 %), соли 12 г, клетчатки 30 г
    put(family, meal("m1", at(DAY, "08:00")), item("i1", "m1", "bread", 500, 600, 5, 10, 100),
        meal("m2", at(DAY, "19:00")), item("i2", "m2", "bread", 500, 600, 5, 10, 100))
    notes = [text(line) for line in report(family)["summary"] if line["style"] == "warn"]
    # Заметность: соль 12 / 5 − 1 = 1,4; белки 1 − 0,08 = 0,92; калории 1 − 0,51 = 0,49
    assert notes == ["Соли 12 г при норме до 5", "Белков мало: 8 % нормы", "Калорий мало: 51 % нормы"]


def test_витамины_полнота_и_нехватка(family):
    put(family, meal("m1", at(DAY, "08:00")), item("i1", "m1", "milk", 500, 300),
        meal("m2", at(DAY, "13:00")), item("i2", "m2", "bread", 120, 300))
    lines = section(report(family), "Витамины и минералы")
    assert lines[0] == "Витамины известны для 50 % калорий"
    # Кальций 600 мг из 1000 — 60 %, B12 2 мкг из 3 — 67 %
    lacking = next(line for line in lines if line.startswith("Не хватило"))
    assert "Кальций 60 %" in lacking and "Витамин B12 67 %" in lacking


def test_сд1_углеводы_крупно_сахар_в_диапазоне(client):
    put(client, profile(KID, "Дочь", sex="female", birth_date="2014-05-20", sd1_enabled=1, show_xe=1,
                        carbs_per_xe=12.0, glucose_low=3.9, glucose_high=10.0),
        product("juice", "Сок", 45, 0, 0, 11),
        meal("m1", at(DAY, "12:00"), KID, glucose=6.1, dose=3.5), item("i1", "m1", "juice", 200, 90, 0, 0, 22),
        meal("m2", at(DAY, "18:00"), KID, glucose=11.2), item("i2", "m2", "juice", 200, 90, 0, 0, 22))
    rep = report(client, KID)
    big = [text(line) for line in rep["summary"] if line["style"] == "big"]
    assert big == ["Углеводы за день: 44 г · 3,7 ХЕ"]
    assert "Сахар при приёмах: 6,1; 11,2 — в диапазоне 3,9–10: 1 из 2" in summary(rep)
    meals = section(rep, "Приёмы пищи")
    assert meals[0] == "12:00 — 90 ккал · углеводы 22 г (1,8 ХЕ) · сахар 6,1 · доза 3,5 ед."
    # Жирным — время и углеводы
    head = next(s for s in rep["sections"] if s["title"] == "Приёмы пищи")["lines"][0]
    assert [s["text"] for s in head["spans"] if s["bold"]] == ["12:00 — 90 ккал", "углеводы 22 г"]


def test_пустой_день(family):
    rep = report(family, day=date(2026, 9, 20))
    assert rep["empty"] is True
    assert summary(rep) == ["Ничего не записано"]
    assert rep["sections"] == []


def test_ошибки_запроса(client, anonymous, family):
    assert anonymous.get("/reports/day", params={"profile_id": ME, "date": "2026-09-29"}).status_code == 401
    assert client.get("/reports/day", params={"profile_id": "нет", "date": "2026-09-29"}).status_code == 404
    assert client.get("/reports/day", params={"profile_id": ME, "date": "вчера"}).status_code == 422
    put(client, profile(ME, "Я"), deleted={ME})
    assert client.get("/reports/day", params={"profile_id": ME, "date": "2026-09-29"}).status_code == 404
