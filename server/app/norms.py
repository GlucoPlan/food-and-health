"""Нормы питания (ТЗ 17.3) — копия расчёта телефона (data/norms/NormCalculator.kt, NormTables.kt).

Взрослым — Миффлин — Сан Жеор × активность с поправкой на цель, БЖУ 20/30/50 % калорий.
Детям — таблица 21 МР 2.3.1.0253-21. Витамины и минералы — МР для пола и возраста.
Совпадение с телефоном проверяется общими примерами shared/norm-cases.json.
"""

from dataclasses import dataclass, field
from datetime import date

DEFAULT_PACE = 0.5
KCAL_PER_KG = 7700.0
ADULT_AGE = 18
ADULT_FIBER_G = 25.0
ADULT_SALT_MAX_G = 5.0

ACTIVITY = {"sedentary": 1.2, "light": 1.375, "moderate": 1.55, "high": 1.725}

# Группы 1–2, 3–6, 7–10, 11–14, 15–17 лет: (ккал, белки, жиры, углеводы) мальчики / девочки, клетчатка
_CHILD_GROUPS = [
    (range(1, 3), (1300, 39, 44, 188), (1300, 39, 44, 188), 10),
    (range(3, 7), (1800, 54, 60, 261), (1800, 54, 60, 261), 12),
    (range(7, 11), (2100, 63, 70, 305), (2100, 63, 70, 305), 16),
    (range(11, 15), (2500, 75, 83, 363), (2300, 69, 77, 334), 20),
    (range(15, 18), (2900, 87, 97, 421), (2500, 75, 83, 363), 22),
]

# Нутриенты по тем же группам: мальчики, девочки (девочки не указаны — как мальчики)
_CHILD_MICRO = {
    "vit_a": ([450, 500, 700, 1000, 1000], [450, 500, 700, 800, 800]),
    "vit_c": ([45, 50, 60, 70, 90], [45, 50, 60, 60, 70]),
    "vit_d": ([15, 15, 15, 15, 15], None),
    "vit_e": ([4, 7, 10, 12, 15], None),
    "vit_k": ([30, 55, 60, 80, 120], [30, 55, 60, 70, 100]),
    "vit_b1": ([0.8, 0.9, 1.1, 1.3, 1.5], [0.8, 0.9, 1.1, 1.3, 1.3]),
    "vit_b2": ([0.9, 1.0, 1.2, 1.5, 1.8], [0.9, 1.0, 1.2, 1.5, 1.5]),
    "vit_b6": ([0.9, 1.2, 1.5, 1.7, 2.0], [0.9, 1.2, 1.5, 1.6, 1.6]),
    "vit_b9": ([100, 200, 300, 350, 400], None),
    "vit_b12": ([0.7, 1.5, 2.0, 3.0, 3.0], None),
    "ca": ([800, 900, 1100, 1200, 1200], None),
    "fe": ([10, 10, 12, 12, 15], [10, 10, 12, 15, 18]),
    "mg": ([80, 200, 250, 300, 400], None),
    "k": ([1000, 1500, 2000, 2500, 3200], None),
    "na": ([500, 700, 1000, 1100, 1300], None),
    "zn": ([5, 8, 10, 12, 12], None),
    "p": ([600, 700, 800, 900, 900], None),
    "i": ([90, 90, 130, 150, 150], None),
}

_ADULT_MALE = {
    "vit_a": 900, "vit_c": 100, "vit_d": 15, "vit_e": 15, "vit_k": 120,
    "vit_b1": 1.5, "vit_b2": 1.8, "vit_b6": 2.0, "vit_b9": 400, "vit_b12": 3.0,
    "ca": 1000, "fe": 10, "mg": 420, "k": 3500, "na": 1300, "zn": 12, "p": 700, "i": 150,
}
_ADULT_FEMALE = {**_ADULT_MALE, "vit_a": 800, "fe": 18}
_SENIOR = {"vit_d": 20, "ca": 1200}

# Справочник нутриентов телефона (data/product/Nutrients.kt): код → (название, единица)
NUTRIENTS = {
    "vit_a": ("Витамин A", "мкг"), "vit_c": ("Витамин C", "мг"), "vit_d": ("Витамин D", "мкг"),
    "vit_e": ("Витамин E", "мг"), "vit_k": ("Витамин K", "мкг"), "vit_b1": ("Витамин B1", "мг"),
    "vit_b2": ("Витамин B2", "мг"), "vit_b6": ("Витамин B6", "мг"), "vit_b9": ("Витамин B9 (фолаты)", "мкг"),
    "vit_b12": ("Витамин B12", "мкг"),
    "ca": ("Кальций", "мг"), "fe": ("Железо", "мг"), "mg": ("Магний", "мг"), "k": ("Калий", "мг"),
    "na": ("Натрий", "мг"), "zn": ("Цинк", "мг"), "p": ("Фосфор", "мг"), "i": ("Йод", "мкг"),
}


@dataclass(frozen=True)
class NormSet:
    kcal: float | None = None
    protein: float | None = None
    fat: float | None = None
    carbs: float | None = None


@dataclass(frozen=True)
class NormInput:
    sex: str | None
    birth_date: date | None
    height_cm: float | None
    weight_kg: float | None
    activity: str | None = "moderate"
    target_weight_kg: float | None = None
    pace_kg_per_week: float = DEFAULT_PACE
    manual: NormSet = field(default_factory=NormSet)


@dataclass(frozen=True)
class DailyNorms:
    auto: NormSet
    final: NormSet
    missing: list[str]
    child: bool
    fiber_min_g: float | None
    salt_max_g: float | None
    micro: dict[str, float]


def age_on(birth: date, day: date) -> int:
    """Полных лет на дату (как Period.between на телефоне)."""
    return day.year - birth.year - ((day.month, day.day) < (birth.month, birth.day))


def _group(age: int) -> int:
    return next((i for i, g in enumerate(_CHILD_GROUPS) if age in g[0]), -1)


def child_macros(age: int, sex: str) -> NormSet | None:
    i = _group(age)
    if i < 0:
        return None
    g = _CHILD_GROUPS[i]
    return NormSet(*map(float, g[1] if sex == "male" else g[2]))


def micro(age: int, sex: str) -> dict[str, float]:
    if age >= ADULT_AGE:
        base = _ADULT_MALE if sex == "male" else _ADULT_FEMALE
        result = {**base, **_SENIOR} if age > 65 else dict(base)
        return {k: float(v) for k, v in result.items()}
    i = _group(age)
    if i < 0:
        return {}
    return {k: float((f if sex != "male" and f else m)[i]) for k, (m, f) in _CHILD_MICRO.items()}


def bmr(sex: str, age: int, height_cm: float, weight_kg: float) -> float:
    return 10 * weight_kg + 6.25 * height_cm - 5 * age + (5 if sex == "male" else -161)


def calculate(inp: NormInput, day: date) -> DailyNorms:
    age = age_on(inp.birth_date, day) if inp.birth_date else None
    child = age is not None and age < ADULT_AGE
    sex = inp.sex if inp.sex in ("male", "female") else None

    missing = []
    if sex is None:
        missing.append("sex")
    if age is None:
        missing.append("birth_date")
    if not child:
        if inp.height_cm is None:
            missing.append("height")
        if inp.weight_kg is None:
            missing.append("weight")
    if age is not None and age < 1:
        missing.append("infant")

    table = child_macros(age, sex) if child and sex else None
    if missing:
        auto_kcal = None
    elif child:
        auto_kcal = table.kcal if table else None
    else:
        auto_kcal = _adult_kcal(inp, sex, age)

    kcal = inp.manual.kcal if inp.manual.kcal is not None else auto_kcal
    derived = _macros(kcal, table) if kcal is not None else NormSet()
    auto = NormSet(auto_kcal, derived.protein, derived.fat, derived.carbs)

    def pick(manual, fallback):
        return manual if manual is not None else fallback

    return DailyNorms(
        auto=auto,
        final=NormSet(
            kcal,
            pick(inp.manual.protein, auto.protein),
            pick(inp.manual.fat, auto.fat),
            pick(inp.manual.carbs, auto.carbs),
        ),
        missing=missing,
        child=child,
        fiber_min_g=(float(_CHILD_GROUPS[_group(age)][3]) if _group(age) >= 0 else None) if child else ADULT_FIBER_G,
        salt_max_g=None if child else ADULT_SALT_MAX_G,
        micro=micro(age, sex) if sex and age is not None else {},
    )


def _adult_kcal(inp: NormInput, sex: str, age: int) -> float:
    weight = inp.weight_kg
    base = bmr(sex, age, inp.height_cm, weight)
    tdee = base * ACTIVITY.get(inp.activity or "moderate", ACTIVITY["moderate"])
    target = inp.target_weight_kg
    if target is None:
        return tdee
    shift = inp.pace_kg_per_week * KCAL_PER_KG / 7
    if target < weight:
        return max(tdee - shift, base)
    if target > weight:
        return tdee + shift
    return tdee


def _macros(kcal: float, table: NormSet | None) -> NormSet:
    if table is not None:
        k = kcal / table.kcal
        return NormSet(kcal, table.protein * k, table.fat * k, table.carbs * k)
    return NormSet(kcal, kcal * 0.20 / 4, kcal * 0.30 / 9, kcal * 0.50 / 4)
