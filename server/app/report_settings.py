"""Настройки отчётов, которые меняются без новой версии (ТЗ 17.6): /etc/foodhealth/reports.json.

    {"meal_slots": [{"name": "Завтрак", "until": "11:00"}, {"name": "Обед", "until": "16:00"},
                    {"name": "Ужин", "until": "21:00"}, {"name": "Поздний приём"}]}

Приём относится к первому интервалу, у которого время приёма раньше «until»; у последнего «until» нет.
Файл читается при каждом отчёте: правка действует сразу. Ошибка в файле — берутся значения по умолчанию.
"""

import json
import sys
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class MealSlot:
    name: str
    until: int | None  # минуты от полуночи; None — до конца суток


DEFAULT_SLOTS = (
    MealSlot("Завтрак", 11 * 60), MealSlot("Обед", 16 * 60), MealSlot("Ужин", 21 * 60), MealSlot("Поздний приём", None),
)


@dataclass(frozen=True)
class ReportSettings:
    meal_slots: tuple[MealSlot, ...] = DEFAULT_SLOTS

    def slot(self, minutes: int) -> str:
        for s in self.meal_slots:
            if s.until is None or minutes < s.until:
                return s.name
        return self.meal_slots[-1].name


def _minutes(text: str) -> int:
    h, m = text.split(":")
    value = int(h) * 60 + int(m)
    if not 0 < value <= 24 * 60:
        raise ValueError(f"время {text}")
    return value


def parse(data: dict) -> ReportSettings:
    raw = data.get("meal_slots")
    if raw is None:
        return ReportSettings()
    slots = tuple(MealSlot(str(s["name"]), _minutes(s["until"]) if s.get("until") else None) for s in raw)
    if not slots or slots[-1].until is not None or any(s.until is None for s in slots[:-1]):
        raise ValueError("у последнего интервала не должно быть «until», у остальных — должно")
    bounds = [s.until for s in slots[:-1]]
    if bounds != sorted(set(bounds)):
        raise ValueError("границы «until» должны идти по возрастанию")
    return ReportSettings(slots)


def load(path: Path | None) -> ReportSettings:
    if path is None or not path.is_file():
        return ReportSettings()
    try:
        return parse(json.loads(path.read_text()))
    except (ValueError, KeyError, TypeError, AttributeError) as e:
        print(f"{path}: ошибка в настройках отчётов ({e}) — беру значения по умолчанию", file=sys.stderr)
        return ReportSettings()
