"""Настройки отчётов, которые меняются без новой версии (ТЗ 17.6): /etc/foodhealth/reports.json.

    {"meal_slots": [{"name": "Завтрак", "until": "11:00"}, {"name": "Обед", "until": "16:00"},
                    {"name": "Ужин", "until": "21:00"}, {"name": "Поздний приём"}],
     "analysis_off": ["Дочь"]}

Приём относится к первому интервалу, у которого время приёма раньше «until»; у последнего «until» нет.
«analysis_off» — имена профилей, по которым Claude не запускается (ТЗ 17.9).
Файл читается при каждом отчёте: правка действует сразу. Ошибка в настройке — для неё берётся значение
по умолчанию, остальные настройки файла действуют.
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
    analysis_off: frozenset[str] = frozenset()

    def analysis_on(self, profile: dict) -> bool:
        return profile.get("name", "") not in self.analysis_off

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


def _meal_slots(raw) -> tuple[MealSlot, ...]:
    slots = tuple(MealSlot(str(s["name"]), _minutes(s["until"]) if s.get("until") else None) for s in raw)
    if not slots or slots[-1].until is not None or any(s.until is None for s in slots[:-1]):
        raise ValueError("у последнего интервала не должно быть «until», у остальных — должно")
    bounds = [s.until for s in slots[:-1]]
    if bounds != sorted(set(bounds)):
        raise ValueError("границы «until» должны идти по возрастанию")
    return slots


def _analysis_off(raw) -> frozenset[str]:
    if not isinstance(raw, list) or not all(isinstance(n, str) for n in raw):
        raise ValueError("«analysis_off» — список имён профилей")
    return frozenset(n.strip() for n in raw)


FIELDS = {"meal_slots": _meal_slots, "analysis_off": _analysis_off}


def parse(data: dict, errors: list[str] | None = None) -> ReportSettings:
    """Каждая настройка отдельно: ошибка в одной не сбрасывает остальные. Без [errors] ошибка — исключение."""
    if not isinstance(data, dict):
        raise ValueError("ожидается объект JSON")
    values = {}
    for key, read in FIELDS.items():
        if data.get(key) is None:
            continue
        try:
            values[key] = read(data[key])
        except (ValueError, KeyError, TypeError, AttributeError) as e:
            if errors is None:
                raise
            errors.append(f"{key}: {e}")
    return ReportSettings(**values)


def load(path: Path | None) -> ReportSettings:
    if path is None or not path.is_file():
        return ReportSettings()
    errors: list[str] = []
    try:
        result = parse(json.loads(path.read_text()), errors)
    except ValueError as e:
        errors, result = [str(e)], ReportSettings()
    for e in errors:
        print(f"{path}: ошибка в настройках отчётов ({e}) — беру значение по умолчанию", file=sys.stderr)
    return result
