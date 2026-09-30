"""Нормы на сервере совпадают с телефоном: общие контрольные примеры (ТЗ 17.3)."""

import json
from datetime import date
from pathlib import Path

import pytest

from app import norms

CASES = json.loads((Path(__file__).resolve().parents[2] / "shared" / "norm-cases.json").read_text())["cases"]


def run(case: dict) -> norms.DailyNorms:
    i = case["input"]
    m = i.get("manual", {})
    return norms.calculate(
        norms.NormInput(
            sex=i.get("sex"),
            birth_date=date.fromisoformat(i["birth_date"]) if i.get("birth_date") else None,
            height_cm=i.get("height_cm"),
            weight_kg=i.get("weight_kg"),
            activity=i.get("activity"),
            target_weight_kg=i.get("target_weight_kg"),
            pace_kg_per_week=i.get("pace_kg_per_week", norms.DEFAULT_PACE),
            manual=norms.NormSet(m.get("kcal"), m.get("protein"), m.get("fat"), m.get("carbs")),
        ),
        date.fromisoformat(i["date"]),
    )


def actual(n: norms.DailyNorms, key: str):
    return {
        "child": n.child, "missing": n.missing, "auto_kcal": n.auto.kcal, "auto_protein": n.auto.protein,
        "kcal": n.final.kcal, "protein": n.final.protein, "fat": n.final.fat, "carbs": n.final.carbs,
        "fiber_min_g": n.fiber_min_g, "salt_max_g": n.salt_max_g, "micro": n.micro,
    }[key]


@pytest.mark.parametrize("case", CASES, ids=[c["name"] for c in CASES])
def test_общие_примеры(case):
    n = run(case)
    for key, expected in case["expect"].items():
        got = actual(n, key)
        if key == "micro":
            if not expected:
                assert got == {}, key
            for code, value in expected.items():
                assert got[code] == pytest.approx(value, abs=1e-3), f"micro.{code}"
        elif isinstance(expected, bool) or expected is None or isinstance(expected, list):
            assert got == expected, key
        else:
            assert got == pytest.approx(expected, abs=1e-3), key


def test_у_всех_возрастов_от_года_есть_все_нутриенты():
    for age in range(1, 101):
        for sex in ("male", "female"):
            assert set(norms.micro(age, sex)) == set(norms.NUTRIENTS), (age, sex)
