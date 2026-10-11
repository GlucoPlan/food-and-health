"""PDF для врача (ТЗ 17.11): данные за период, эндпоинт."""

from datetime import date, timedelta

import pytest

from app.daydata import TABLES, Family
from app.db import Store
from app.report_settings import ReportSettings
from app.reports import doctor

from .test_reports import KID, ME, at, item, meal, product, profile, put

FIRST = date(2026, 9, 1)
LAST = date(2026, 9, 30)


def kid(**extra):
    return profile(KID, "Дочь", sd1_enabled=1, show_xe=1, carbs_per_xe=12.0, sex="female",
                   birth_date="2015-05-20", glucose_low=4.0, glucose_high=8.0, **extra)


@pytest.fixture
def family(client):
    put(client, kid(), profile(ME, "Я"),
        product("bread", "Хлеб", 250, 8, 3, 48), product("milk", "Молоко", 60, 3, 3.2, 4.7),
        ("height", "h0", {"profile_id": KID, "measured_at": at(date(2026, 6, 1), "09:00"), "height_cm": 140.0}),
        ("weight", "w1", {"profile_id": KID, "measured_at": at(date(2026, 9, 2), "08:00"), "weight_kg": 33.0}),
        ("weight", "w2", {"profile_id": KID, "measured_at": at(date(2026, 9, 28), "08:00"), "weight_kg": 33.6}),
        # Завтрак: хлеб 50 г (24 г углеводов) и молоко 200 г (9,4 г); сахар 6,5, доза 3
        meal("m1", at(date(2026, 9, 3), "08:15"), KID, glucose=6.5, dose=3.0),
        item("i1", "m1", "bread", 50, 125, 4, 1.5, 24),
        item("i2", "m1", "milk", 200, 120, 6, 6.4, 9.4),
        # Обед того же дня без сахара и дозы
        meal("m2", at(date(2026, 9, 3), "13:00"), KID),
        item("i3", "m2", "bread", 100, 250, 8, 3, 48),
        # Ужин в другой день, сахар вне диапазона
        meal("m3", at(date(2026, 9, 10), "19:30"), KID, glucose=11.3, dose=4.5),
        item("i4", "m3", "bread", 25, 62, 2, 0.8, 12),
        # Вне периода — не попадает
        meal("m4", at(date(2026, 10, 1), "08:00"), KID, glucose=5.0),
        item("i5", "m4", "bread", 100, 250, 8, 3, 48))
    return client


@pytest.fixture
def collect(settings):
    def run(first=FIRST, last=LAST) -> doctor.Doctor:
        fam = Family(Store(settings.db_path).records(TABLES))
        return doctor.collect(fam, fam.profiles[KID], first, last, ReportSettings(), chart=False)
    return run


def test_приёмы_по_дням_состав_углеводы_сахар_доза(family, collect):
    doc = collect()
    assert [d.day for d in doc.days] == [date(2026, 9, 3), date(2026, 9, 10)]
    breakfast, lunch = doc.days[0].rows
    assert breakfast == doctor.Row("08:15", "Хлеб 50\u00a0г, Молоко 200\u00a0г", pytest.approx(33.4), 6.5, 3.0)
    assert lunch.glucose is None and lunch.dose is None
    assert doc.days[0].carbs == pytest.approx(81.4)
    assert doc.xe(doc.days[0].carbs) == "6,8"


def test_сводка_за_период(family, collect):
    lines = doctor.summary_lines(collect())
    assert lines[0] == "Еда записана в 2 днях из 30"
    # (81,4 + 12) / 2 дня
    assert lines[1] == "Углеводы в среднем в день: 47 г (3,9 ХЕ)"
    assert "Завтрак: в среднем 33 г (2,8 ХЕ), приёмов 1" in lines
    assert "Обед: в среднем 48 г (4 ХЕ), приёмов 1" in lines
    assert "Ужин: в среднем 12 г (1 ХЕ), приёмов 1" in lines
    assert ("Сахар при приёмах: в среднем 8,9 ммоль/л, замеров 2; в диапазоне 4–8: 1 из 2 (50 %)") in lines
    assert "Вес: 33 кг (02.09.2026) → 33,6 кг (28.09.2026)" in lines
    # Рост записан до периода — последний известный
    assert "Рост: 140 см (01.06.2026)" in lines


def test_без_хе_колонки_нет_и_пустой_период(client, collect):
    put(client, profile(KID, "Дочь", sd1_enabled=1, show_xe=0))
    doc = collect()
    assert doc.per_xe is None and doc.days == []
    assert doctor.summary_lines(doc) == ["Еда записана в 0 днях из 30", "Сахар при приёмах не записан"]
    assert doctor.render(doc).startswith(b"%PDF")


def test_эндпоинт_отдаёт_pdf(family):
    r = family.get("/reports/doctor", params={"profile_id": KID, "first": "2026-09-01", "last": "2026-09-30"})
    assert r.status_code == 200, r.text
    assert r.headers["content-type"] == "application/pdf"
    assert r.content.startswith(b"%PDF")
    assert 'filename="doctor-2026-09-01-2026-09-30.pdf"' in r.headers["content-disposition"]


def test_эндпоинт_проверяет_профиль_и_период(family):
    def get(**params):
        return family.get("/reports/doctor", params={"profile_id": KID, "first": "2026-09-01", "last": "2026-09-30",
                                                     **params})

    assert get(profile_id=ME).status_code == 400          # не СД1
    assert get(profile_id="nobody").status_code == 404
    assert get(first="2026-09-30", last="2026-09-01").status_code == 400
    assert get(first="2025-01-01", last="2026-09-30").status_code == 400
    assert get(first="2025-10-01", last="2026-09-30").status_code == 200   # ровно 365 дней — можно


def test_удалённые_приёмы_не_попадают(client, collect):
    put(client, kid(), product("bread", "Хлеб", 250, 8, 3, 48),
        meal("m1", at(FIRST + timedelta(days=1), "08:00"), KID), item("i1", "m1", "bread", 50, 125, 4, 1.5, 24),
        deleted=("m1",))
    assert collect().days == []
