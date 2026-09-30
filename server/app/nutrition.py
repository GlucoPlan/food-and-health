"""Нутриенты приёмов пищи для отчётов (ТЗ 5.3, 17.2).

КБЖУ пункта приёма — из снимка на момент записи (snapshot_*), как в «Истории».
Клетчатка, соль, витамины и минералы — по текущим продуктам: значение на 100 г × вес / 100;
у блюда — сумма по ингредиентам варки / вес нетто. Дополненный продукт пересчитывает прошлые дни.
"""

import json
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Nutrients:
    kcal: float = 0.0
    protein: float = 0.0
    fat: float = 0.0
    carbs: float = 0.0
    fiber: float = 0.0
    salt: float = 0.0
    micro: dict[str, float] = field(default_factory=dict)
    # Сколько калорий пришло из продуктов с заполненными витаминами — полнота данных о витаминах
    micro_kcal: float = 0.0

    def __add__(self, other: "Nutrients") -> "Nutrients":
        return Nutrients(
            self.kcal + other.kcal, self.protein + other.protein, self.fat + other.fat,
            self.carbs + other.carbs, self.fiber + other.fiber, self.salt + other.salt,
            {k: self.micro.get(k, 0.0) + other.micro.get(k, 0.0) for k in self.micro.keys() | other.micro.keys()},
            self.micro_kcal + other.micro_kcal,
        )

    def scaled(self, factor: float) -> "Nutrients":
        return Nutrients(
            self.kcal * factor, self.protein * factor, self.fat * factor, self.carbs * factor,
            self.fiber * factor, self.salt * factor, {k: v * factor for k, v in self.micro.items()},
            self.micro_kcal * factor,
        )


ZERO = Nutrients()


def num(value, default: float = 0.0) -> float:
    return float(value) if isinstance(value, (int, float)) and not isinstance(value, bool) else default


def parse_micro(value) -> dict[str, float]:
    """Поле micro приходит строкой JSON (колонка TEXT на телефоне)."""
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except ValueError:
            return {}
    if not isinstance(value, dict):
        return {}
    return {k: float(v) for k, v in value.items() if isinstance(v, (int, float)) and not isinstance(v, bool)}


def product_per100(product: dict) -> Nutrients:
    kcal = num(product.get("kcal"))
    micro = parse_micro(product.get("micro"))
    return Nutrients(
        kcal, num(product.get("protein")), num(product.get("fat")), num(product.get("carbs")),
        num(product.get("fiber")), num(product.get("salt")), micro, kcal if micro else 0.0,
    )


def dish_per100(version: dict, ingredients: list[dict], products: dict[str, dict]) -> Nutrients | None:
    """На 100 г варки; продукта нет в базе — пропускается (как на телефоне)."""
    net = num(version.get("net_weight_g"))
    if net <= 0:
        return None
    total = ZERO
    for ing in ingredients:
        product = products.get(ing.get("product_id"))
        if product is not None:
            total = total + product_per100(product).scaled(num(ing.get("weight_g")) / 100)
    return total.scaled(100 / net)


def item_nutrients(item: dict, products: dict[str, dict], versions: dict[str, dict],
                   ingredients: dict[str, list[dict]]) -> Nutrients:
    weight = num(item.get("weight_g"))
    if item.get("type") == "dish":
        version = versions.get(item.get("dish_version_id"))
        per100 = dish_per100(version, ingredients.get(item.get("dish_version_id"), []), products) if version else None
    else:
        product = products.get(item.get("product_id"))
        per100 = product_per100(product) if product else None
    extra = per100.scaled(weight / 100) if per100 else ZERO

    kcal = num(item.get("snapshot_kcal"))
    covered = kcal * (extra.micro_kcal / extra.kcal) if extra.kcal > 0 else 0.0
    return Nutrients(
        kcal, num(item.get("snapshot_protein")), num(item.get("snapshot_fat")), num(item.get("snapshot_carbs")),
        extra.fiber, extra.salt, extra.micro, covered,
    )
