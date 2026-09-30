"""Данные семьи для отчётов: записи из хранилища, разложенные по людям и дням (ТЗ 17.2).

Сутки — по Москве для всех (17.2). Время записей — миллисекунды UTC, как на телефоне.
"""

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

from . import norms
from .nutrition import ZERO, Nutrients, item_nutrients, num

MSK = ZoneInfo("Europe/Moscow")

TABLES = (
    "profile", "product", "dish", "dish_version", "dish_ingredient", "meal", "meal_item",
    "height", "weight", "blood_pressure", "sleep", "water", "body_measure",
)


def day_bounds(day: date) -> tuple[int, int]:
    """Начало и конец суток по Москве, мс UTC; конец не входит."""
    start = datetime.combine(day, time(), MSK)
    end = datetime.combine(day + timedelta(days=1), time(), MSK)
    return int(start.timestamp() * 1000), int(end.timestamp() * 1000)


def local(ms: int) -> datetime:
    return datetime.fromtimestamp(ms / 1000, MSK)


def local_date(ms: int) -> date:
    return local(ms).date()


@dataclass(frozen=True)
class MealItem:
    name: str
    weight_g: float
    nutrients: Nutrients


@dataclass(frozen=True)
class Meal:
    at: int
    items: list[MealItem]
    total: Nutrients
    glucose: float | None
    dose: float | None
    notes: str | None


class Family:
    """Все записи семьи. Удалённые приёмы, пункты и замеры пропускаются; удалённые продукты и блюда —
    нет: прошлые приёмы показывают их с прежним составом (ТЗ 5.5)."""

    def __init__(self, records: list[dict]):
        by_table: dict[str, list[dict]] = {t: [] for t in TABLES}
        alive: dict[str, list[dict]] = {t: [] for t in TABLES}
        for r in records:
            data = {**r["data"], "id": r["id"]}
            by_table.setdefault(r["table"], []).append(data)
            if not r["deleted"] and not num(data.get("deleted")):
                alive.setdefault(r["table"], []).append(data)

        self.profiles = {p["id"]: p for p in alive["profile"]}
        self.products = {p["id"]: p for p in by_table["product"]}
        self.dishes = {d["id"]: d for d in by_table["dish"]}
        self.versions = {v["id"]: v for v in by_table["dish_version"]}
        self.ingredients: dict[str, list[dict]] = {}
        for i in alive["dish_ingredient"]:
            self.ingredients.setdefault(i.get("dish_version_id"), []).append(i)
        self._items: dict[str, list[dict]] = {}
        for i in alive["meal_item"]:
            self._items.setdefault(i.get("meal_id"), []).append(i)
        self._alive = alive

    def _of(self, table: str, profile_id: str, time_key: str) -> list[dict]:
        rows = [r for r in self._alive[table] if r.get("profile_id") == profile_id and r.get(time_key) is not None]
        return sorted(rows, key=lambda r: r[time_key])

    def _between(self, table: str, profile_id: str, time_key: str, start: int, end: int) -> list[dict]:
        return [r for r in self._of(table, profile_id, time_key) if start <= r[time_key] < end]

    def meals(self, profile_id: str, start: int, end: int) -> list[Meal]:
        result = []
        for m in self._between("meal", profile_id, "eaten_at", start, end):
            items = [
                MealItem(self._item_name(i), num(i.get("weight_g")),
                         item_nutrients(i, self.products, self.versions, self.ingredients))
                for i in self._items.get(m["id"], [])
            ]
            total = ZERO
            for i in items:
                total = total + i.nutrients
            result.append(Meal(
                m["eaten_at"], items, total,
                num(m.get("glucose"), None), num(m.get("insulin_dose"), None), m.get("notes") or None,
            ))
        return result

    def _item_name(self, item: dict) -> str:
        if item.get("type") == "dish":
            version = self.versions.get(item.get("dish_version_id"))
            dish = self.dishes.get(version.get("dish_id")) if version else None
            return dish["name"] if dish else "Неизвестное блюдо"
        product = self.products.get(item.get("product_id"))
        return product["name"] if product else "Неизвестный продукт"

    def weights(self, profile_id: str) -> list[dict]:
        return self._of("weight", profile_id, "measured_at")

    def heights(self, profile_id: str) -> list[dict]:
        return self._of("height", profile_id, "measured_at")

    def pressures(self, profile_id: str, start: int, end: int) -> list[dict]:
        return self._between("blood_pressure", profile_id, "measured_at", start, end)

    def bodies(self, profile_id: str, start: int, end: int) -> list[dict]:
        return self._between("body_measure", profile_id, "measured_at", start, end)

    def waters(self, profile_id: str, start: int, end: int) -> list[dict]:
        return self._between("water", profile_id, "drunk_at", start, end)

    def sleeps(self, profile_id: str, start: int, end: int) -> list[dict]:
        """Сон по дню пробуждения, как в «Истории»."""
        return self._between("sleep", profile_id, "woke_at", start, end)

    @staticmethod
    def last_before(rows: list[dict], time_key: str, end: int) -> dict | None:
        """Последняя запись раньше [end]."""
        before = [r for r in rows if r[time_key] < end]
        return before[-1] if before else None

    def norms(self, profile: dict, day: date) -> norms.DailyNorms:
        """Нормы на день: рост и вес, записанные в этот день или раньше (ТЗ 17.3)."""
        _, end = day_bounds(day)
        height = self.last_before(self.heights(profile["id"]), "measured_at", end)
        weight = self.last_before(self.weights(profile["id"]), "measured_at", end)
        birth = profile.get("birth_date")
        try:
            birth_date = date.fromisoformat(birth) if birth else None
        except ValueError:
            birth_date = None
        return norms.calculate(
            norms.NormInput(
                sex=profile.get("sex"),
                birth_date=birth_date,
                height_cm=num(height.get("height_cm"), None) if height else None,
                weight_kg=num(weight.get("weight_kg"), None) if weight else None,
                activity=profile.get("activity"),
                target_weight_kg=num(profile.get("target_weight_kg"), None),
                pace_kg_per_week=num(profile.get("weight_pace_kg"), norms.DEFAULT_PACE),
                manual=norms.NormSet(
                    num(profile.get("norm_kcal"), None), num(profile.get("norm_protein"), None),
                    num(profile.get("norm_fat"), None), num(profile.get("norm_carbs"), None),
                ),
            ),
            day,
        )
