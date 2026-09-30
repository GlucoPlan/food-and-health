"""Отчёт за период — неделя (ТЗ 17.6), затем месяц: выжимка со сравнением с прошлым периодом,
подробности и графики."""

import base64
import math
from collections import Counter
from dataclasses import dataclass, field
from datetime import date, timedelta

from ..daydata import Family, day_bounds, local, local_date
from ..norms import NUTRIENTS
from ..nutrition import ZERO, Nutrients, num
from ..report_settings import ReportSettings
from .daily import BODY_PARTS, DEFICIT_SHARE, EXCESS_CODES, HIGH_KCAL_SHARE, LOW_KCAL_SHARE, SLEEP_QUALITY
from .text import MONTHS, SHORT_WEEKDAYS, B, duration, fmt, line, percent, section, signed

QUALITY_SCORE = {"bad": 1, "normal": 2, "good": 3}
SCORE_LABEL = {1: "плохо", 2: "нормально", 3: "хорошо"}
MIN_PACE_DAYS = 3  # темп веса считается, если между первым и последним взвешиванием хотя бы 3 дня
TOP_PRODUCTS = 10
TOP_KCAL = 5


def week_of(day: date) -> tuple[date, date]:
    start = day - timedelta(days=day.weekday())
    return start, start + timedelta(days=6)


def period_title(first: date, last: date) -> str:
    if first.month == last.month:
        return f"{first.day}–{last.day} {MONTHS[last.month - 1]}"
    return f"{first.day} {MONTHS[first.month - 1]} – {last.day} {MONTHS[last.month - 1]}"


@dataclass
class Stats:
    """Всё посчитанное за период одного человека."""
    days: list[date]
    food: dict[date, Nutrients] = field(default_factory=dict)          # дни с едой
    kcal_norm: dict[date, float] = field(default_factory=dict)
    avg: Nutrients = ZERO
    avg_norm: dict[str, float | None] = field(default_factory=dict)    # kcal/protein/fat/carbs — средние нормы
    micro_norm: dict[str, float] = field(default_factory=dict)
    fiber_min: float | None = None
    over: int = 0
    under: int = 0
    weights: dict[date, float] = field(default_factory=dict)            # первый вес дня
    sleeps: dict[date, dict] = field(default_factory=dict)              # сон по дню пробуждения
    pressures: list[dict] = field(default_factory=list)
    water: dict[date, float] = field(default_factory=dict)
    water_norm: dict[date, float] = field(default_factory=dict)
    items: Counter = field(default_factory=Counter)
    item_kcal: Counter = field(default_factory=Counter)
    bodies: list[dict] = field(default_factory=list)
    heights: list[dict] = field(default_factory=list)
    carbs: dict[date, float] = field(default_factory=dict)
    slots: dict[str, list[float]] = field(default_factory=dict)          # углеводы приёмов по времени суток
    glucose: list[tuple[int, float]] = field(default_factory=list)

    @property
    def weight_change(self) -> float | None:
        if len(self.weights) < 2:
            return None
        xs = sorted(self.weights)
        return self.weights[xs[-1]] - self.weights[xs[0]]

    @property
    def sleep_avg(self) -> float | None:
        if not self.sleeps:
            return None
        return sum((s["woke_at"] - s["asleep_at"]) / 60000 for s in self.sleeps.values()) / len(self.sleeps)


def collect(family: Family, profile: dict, first: date, last: date, settings: ReportSettings) -> Stats:
    pid = profile["id"]
    days = [first + timedelta(days=i) for i in range((last - first).days + 1)]
    st = Stats(days)
    start, _ = day_bounds(first)
    _, end = day_bounds(last)

    kcal_norms, protein_norms, fat_norms, carbs_norms = [], [], [], []
    for d in days:
        s, e = day_bounds(d)
        meals = family.meals(pid, s, e)
        if not meals:
            continue
        total = ZERO
        for m in meals:
            total = total + m.total
            minutes = local(m.at).hour * 60 + local(m.at).minute
            st.slots.setdefault(settings.slot(minutes), []).append(m.total.carbs)
            if m.glucose is not None:
                st.glucose.append((m.at, m.glucose))
            for it in m.items:
                st.items[it.name] += 1
                st.item_kcal[it.name] += it.nutrients.kcal
        st.food[d] = total
        st.carbs[d] = total.carbs
        n = family.norms(profile, d)
        if n.final.kcal:
            st.kcal_norm[d] = n.final.kcal
            kcal_norms.append(n.final.kcal)
            if total.kcal > n.final.kcal * HIGH_KCAL_SHARE:
                st.over += 1
            elif total.kcal < n.final.kcal * LOW_KCAL_SHARE:
                st.under += 1
        for lst, v in ((protein_norms, n.final.protein), (fat_norms, n.final.fat), (carbs_norms, n.final.carbs)):
            if v:
                lst.append(v)
        st.micro_norm = n.micro or st.micro_norm
        st.fiber_min = n.fiber_min_g

    if st.food:
        total = ZERO
        for t in st.food.values():
            total = total + t
        st.avg = total.scaled(1 / len(st.food))

    def mean(values):
        return sum(values) / len(values) if values else None

    st.avg_norm = {"kcal": mean(kcal_norms), "protein": mean(protein_norms), "fat": mean(fat_norms),
                   "carbs": mean(carbs_norms)}

    for w in family.weights(pid):
        if start <= w["measured_at"] < end:
            st.weights.setdefault(local_date(w["measured_at"]), num(w.get("weight_kg")))
    for s in family.sleeps(pid, start, end):
        st.sleeps[local_date(s["woke_at"])] = s
    st.pressures = family.pressures(pid, start, end)
    if num(profile.get("water_enabled")):
        per_kg = num(profile.get("water_ml_per_kg"), 30.0)
        for w in family.waters(pid, start, end):
            d = local_date(w["drunk_at"])
            st.water[d] = st.water.get(d, 0.0) + num(w.get("ml"))
        for d in st.water:
            weight = Family.last_before(family.weights(pid), "measured_at", day_bounds(d)[1])
            if weight is not None:
                st.water_norm[d] = per_kg * num(weight.get("weight_kg"))
    st.bodies = family.bodies(pid, start, end)
    st.heights = [h for h in family.heights(pid) if start <= h["measured_at"] < end]
    return st


def _clock_mean(minutes: list[float]) -> tuple[float, float]:
    """Среднее время суток и разброс в минутах — по кругу, чтобы 23:30 и 00:30 давали 00:00, а не 12:00."""
    angles = [m / 1440 * 2 * math.pi for m in minutes]
    x = sum(math.cos(a) for a in angles) / len(angles)
    y = sum(math.sin(a) for a in angles) / len(angles)
    avg = (math.atan2(y, x) / (2 * math.pi) * 1440) % 1440
    diffs = [((m - avg + 720) % 1440) - 720 for m in minutes]
    spread = math.sqrt(sum(d * d for d in diffs) / len(diffs))
    return avg, spread


def _clock(minutes: float) -> str:
    m = round(minutes) % 1440
    return f"{m // 60:02d}:{m % 60:02d}"


def _minutes_of(ms: int) -> int:
    t = local(ms)
    return t.hour * 60 + t.minute


def _vs(current: float | None, previous: float | None, unit: str, digits: int = 0, word: str = "") -> str:
    """« · ↑120 к прошлой неделе»; нечего сравнивать — пусто."""
    if current is None or previous is None:
        return ""
    delta = current - previous
    rounded = round(delta, digits)
    arrow = "=" if rounded == 0 else ("↑" if delta > 0 else "↓")
    value = "" if rounded == 0 else fmt(abs(delta), digits) + unit
    return f" · {arrow}{value} {word}".rstrip()


def build(family: Family, profile: dict, first: date, last: date, settings: ReportSettings,
          kind: str = "week") -> dict:
    span = (last - first).days + 1
    prev_first = first - timedelta(days=span) if kind == "week" else _prev_month(first)
    prev_last = first - timedelta(days=1)
    st = collect(family, profile, first, last, settings)
    prev = collect(family, profile, prev_first, prev_last, settings)
    word = "к прошлой неделе" if kind == "week" else "к прошлому месяцу"
    sd1 = bool(num(profile.get("sd1_enabled")))

    report = {
        "kind": kind, "date": first.isoformat(), "last": last.isoformat(), "profile_id": profile["id"],
        "profile_name": profile.get("name", ""), "title": period_title(first, last),
        "incomplete": len(st.food) < len(st.days) / 2, "empty": False,
        "summary": [], "sections": [], "images": [],
    }
    has_any = st.food or st.weights or st.sleeps or st.pressures or st.water or st.bodies or st.heights
    if not has_any:
        report["empty"] = True
        report["summary"] = [line("Ничего не записано", style="muted")]
        return report

    summary = [line(f"Еда записана в {len(st.food)} днях из {len(st.days)}",
                    style="warn" if report["incomplete"] else "normal")]
    if sd1 and st.food:
        summary.append(line(B(f"Углеводы в день: {fmt(st.avg.carbs, 0)} г"),
                            _vs(st.avg.carbs, prev.avg.carbs if prev.food else None, " г", word=word), style="big"))
        summary += _glucose_summary(profile, st, prev, word)
    summary += _food_summary(st, prev, word)
    summary += _weight_summary(profile, st, prev, word)
    summary += _sleep_summary(st, prev, word)
    report["summary"] = summary

    sections = []
    if st.food:
        sections.append(section("Питание по дням", _day_lines(st)))
    if sd1 and st.slots:
        sections.append(section("Углеводы по времени приёма", [
            line(f"{name}: в среднем {fmt(sum(v) / len(v), 0)} г ({_plural(len(v), 'приём', 'приёма', 'приёмов')})")
            for name, v in _ordered_slots(st, settings)
        ]))
    if st.sleeps:
        sections.append(section("Сон", _sleep_lines(st)))
    if st.pressures:
        sections.append(section("Давление и пульс", _pressure_lines(st, prev, word)))
    if st.water:
        sections.append(section("Вода", _water_lines(st)))
    if st.food:
        sections.append(section("Витамины и минералы", _micro_lines(st)))
        sections.append(section("Частые продукты и блюда", _top_lines(st)))
    body = _body_lines(st)
    if body:
        sections.append(section("Обхваты", body))
    if st.heights:
        h = st.heights[-1]
        sections.append(section("Рост", [line(f"{fmt(num(h.get('height_cm')))} см")]))
    sections.append(section("Активность", [line("Пока не учитывается", style="muted")]))
    report["sections"] = sections
    report["images"] = _images(profile, st, sd1)
    return report


def _prev_month(first: date) -> date:
    return (first - timedelta(days=1)).replace(day=1)


def _ordered_slots(st: Stats, settings: ReportSettings):
    return [(s.name, st.slots[s.name]) for s in settings.meal_slots if s.name in st.slots]


def _food_summary(st: Stats, prev: Stats, word: str) -> list[dict]:
    if not st.food:
        return [line("Еда не записана", style="muted")]
    a, n = st.avg, st.avg_norm
    prev_kcal = prev.avg.kcal if prev.food else None
    parts = ["Калории в среднем: ", B(fmt(a.kcal, 0))]
    if n["kcal"]:
        parts.append(f" из {fmt(n['kcal'], 0)} ({percent(a.kcal, n['kcal'])} %)")
    parts.append(_vs(a.kcal, prev_kcal, "", word=word))

    def part(name, value, norm):
        return f"{name} {fmt(value, 0)}" + (f" из {fmt(norm, 0)}" if norm else "") + " г"

    lines = [
        line(*parts),
        line(" · ".join([part("Белки", a.protein, n["protein"]), part("Жиры", a.fat, n["fat"]),
                         part("Углеводы", a.carbs, n["carbs"])])),
    ]
    if st.over or st.under:
        lines.append(line(f"Перебор калорий: {_days(st.over)} · недобор: {_days(st.under)}",
                          style="warn" if st.over + st.under > len(st.food) / 2 else "normal"))
    return lines


def _plural(n: int, one: str, few: str, many: str) -> str:
    if n % 10 == 1 and n % 100 != 11:
        return f"{n} {one}"
    if n % 10 in (2, 3, 4) and n % 100 not in (12, 13, 14):
        return f"{n} {few}"
    return f"{n} {many}"


def _days(n: int) -> str:
    return _plural(n, "день", "дня", "дней")


def _weight_summary(profile: dict, st: Stats, prev: Stats, word: str) -> list[dict]:
    if not st.weights:
        return []
    xs = sorted(st.weights)
    last = st.weights[xs[-1]]
    if len(xs) == 1:
        return [line("Вес: ", B(f"{fmt(last)} кг"))]
    change = st.weight_change
    lines = [line(f"Вес: {fmt(st.weights[xs[0]])} → ", B(f"{fmt(last)} кг"), f" ({signed(change)})",
                  _vs(change, prev.weight_change, " кг", 1, word))]
    span = (xs[-1] - xs[0]).days
    target = num(profile.get("target_weight_kg"), None)
    if span >= MIN_PACE_DAYS:
        pace = change / span * 7
        text = f"Темп: {signed(pace)} кг в неделю"
        if target is not None:
            goal = num(profile.get("weight_pace_kg"), 0.5)
            left = last - target
            text += f", цель {'−' if left > 0 else '+'}{fmt(goal)}"
            if abs(left) < 0.05:
                text += " · цель достигнута"
            elif (left > 0 and pace < 0) or (left < 0 and pace > 0):
                eta = xs[-1] + timedelta(days=round(abs(left / pace) * 7))
                text += f" · до цели {fmt(abs(left))} кг, при таком темпе — к {eta.day} {MONTHS[eta.month - 1]} {eta.year}"
            else:
                text += f" · до цели {fmt(abs(left))} кг"
        lines.append(line(text))
    return lines


def _sleep_summary(st: Stats, prev: Stats, word: str) -> list[dict]:
    avg = st.sleep_avg
    if avg is None:
        return []
    bed, _ = _clock_mean([_minutes_of(s["asleep_at"]) for s in st.sleeps.values()])
    wake, _ = _clock_mean([_minutes_of(s["woke_at"]) for s in st.sleeps.values()])
    return [line("Сон в среднем: ", B(duration(round(avg))), f", отбой ~{_clock(bed)}, подъём ~{_clock(wake)}",
                 _vs(avg, prev.sleep_avg, " мин", word=word))]


def _glucose_summary(profile: dict, st: Stats, prev: Stats, word: str) -> list[dict]:
    if not st.glucose:
        return []
    values = [g for _, g in st.glucose]
    avg = sum(values) / len(values)
    text = [f"Сахар при приёмах: в среднем {fmt(avg)}"]
    low, high = num(profile.get("glucose_low"), None), num(profile.get("glucose_high"), None)
    if low is not None and high is not None:
        inside = sum(1 for g in values if low <= g <= high)
        share = percent(inside, len(values))
        prev_share = None
        if prev.glucose:
            prev_share = percent(sum(1 for _, g in prev.glucose if low <= g <= high), len(prev.glucose))
        text += [", в диапазоне ", B(f"{share} %"), f" ({inside} из {len(values)})", _vs(share, prev_share, " %", word=word)]
    return [line(*text)]


def _day_lines(st: Stats) -> list[dict]:
    lines = []
    for d in st.days:
        head = f"{SHORT_WEEKDAYS[d.weekday()]} {d.day:02d}.{d.month:02d} — "
        t = st.food.get(d)
        if t is None:
            lines.append(line(head + "не записано", style="muted"))
            continue
        norm = st.kcal_norm.get(d)
        pct = f" ({percent(t.kcal, norm)} %)" if norm else ""
        lines.append(line(head + f"{fmt(t.kcal, 0)} ккал{pct}, Б {fmt(t.protein, 0)} · Ж {fmt(t.fat, 0)} · "
                          f"У {fmt(t.carbs, 0)}"))
    return lines


def _sleep_lines(st: Stats) -> list[dict]:
    beds = [_minutes_of(s["asleep_at"]) for s in st.sleeps.values()]
    _, spread = _clock_mean(beds)
    lines = [line(f"Ночей записано: {len(st.sleeps)}"),
             line(f"Время отбоя гуляет в среднем на ±{fmt(spread, 0)} мин")]
    scores = [QUALITY_SCORE[s["quality"]] for s in st.sleeps.values() if s.get("quality") in QUALITY_SCORE]
    if scores:
        lines.append(line(f"Средняя оценка: {SCORE_LABEL[round(sum(scores) / len(scores))]}"))
    shortest = min(st.sleeps.items(), key=lambda kv: kv[1]["woke_at"] - kv[1]["asleep_at"])
    minutes = round((shortest[1]["woke_at"] - shortest[1]["asleep_at"]) / 60000)
    lines.append(line(f"Меньше всего — {duration(minutes)} (ночь на {shortest[0].day:02d}.{shortest[0].month:02d})"))
    return lines


def _pressure_lines(st: Stats, prev: Stats, word: str) -> list[dict]:
    p = st.pressures
    sys_ = [x["systolic"] for x in p]
    dia = [x["diastolic"] for x in p]
    pulses = [x["pulse"] for x in p if x.get("pulse") is not None]
    avg_sys = sum(sys_) / len(sys_)
    prev_sys = sum(x["systolic"] for x in prev.pressures) / len(prev.pressures) if prev.pressures else None
    lines = [
        line("В среднем ", B(f"{round(avg_sys)}/{round(sum(dia) / len(dia))}"), _vs(avg_sys, prev_sys, "", word=word)),
        line(f"Верхнее {min(sys_)}–{max(sys_)}, нижнее {min(dia)}–{max(dia)}, измерений {len(p)}"),
    ]
    if pulses:
        lines.append(line(f"Пульс в среднем {round(sum(pulses) / len(pulses))} ({min(pulses)}–{max(pulses)})"))
    return lines


def _water_lines(st: Stats) -> list[dict]:
    avg = sum(st.water.values()) / len(st.water)
    met = sum(1 for d, ml in st.water.items() if d in st.water_norm and ml >= st.water_norm[d])
    return [line(f"В среднем {fmt(avg / 1000)} л в день (записано в {_days(len(st.water))})"),
            line(f"Норма выпита: {_days(met)}")]


def _micro_lines(st: Stats) -> list[dict]:
    if not st.micro_norm:
        return [line("Нормы не рассчитаны: укажите пол и дату рождения в профиле", style="muted")]
    if st.avg.micro_kcal <= 0:
        return [line("Нет данных: у съеденных продуктов витамины и минералы не заполнены", style="muted")]
    lines = [line(f"Витамины известны для {percent(st.avg.micro_kcal, st.avg.kcal)} % калорий", style="muted")]
    lacking, fine, excess = [], [], []
    for code, norm in st.micro_norm.items():
        share = st.avg.micro.get(code, 0.0) / norm
        text = f"{NUTRIENTS.get(code, (code, ''))[0]} {round(share * 100)} %"
        if code in EXCESS_CODES and share > 1:
            excess.append(text)
        elif share < DEFICIT_SHARE:
            lacking.append(text)
        else:
            fine.append(text)
    if lacking:
        lines.append(line(B("Не хватает в среднем: "), ", ".join(lacking)))
    if fine:
        lines.append(line(B("В норме: "), ", ".join(fine)))
    if excess:
        lines.append(line(B("Перебор: "), ", ".join(excess), style="warn"))
    return lines


def _top_lines(st: Stats) -> list[dict]:
    lines = [line(B("Чаще всего: "), ", ".join(f"{name} — {_times(n)}" for name, n in st.items.most_common(TOP_PRODUCTS)))]
    total = sum(st.item_kcal.values())
    if total > 0:
        lines.append(line(B("Больше всего калорий: "), ", ".join(
            f"{name} {percent(k, total)} %" for name, k in st.item_kcal.most_common(TOP_KCAL))))
    return lines


def _times(n: int) -> str:
    return _plural(n, "раз", "раза", "раз")


def _body_lines(st: Stats) -> list[dict]:
    if len(st.bodies) < 2:
        return [line(_body_one(st.bodies[0]))] if st.bodies else []
    first, last = st.bodies[0], st.bodies[-1]
    parts = []
    for key, label in BODY_PARTS:
        a, b = first.get(key), last.get(key)
        if a is not None and b is not None:
            parts.append(f"{label} {fmt(num(a))} → {fmt(num(b))} ({signed(num(b) - num(a))})")
    return [line(p) for p in parts]


def _body_one(b: dict) -> str:
    return ", ".join(f"{label} {fmt(num(b.get(key)))}" for key, label in BODY_PARTS if b.get(key) is not None) + " см"


def _images(profile: dict, st: Stats, sd1: bool) -> list[dict]:
    # matplotlib грузится только здесь: сервис не держит его в памяти, пока не попросят график
    from . import charts

    target = num(profile.get("target_weight_kg"), None)
    candidates = [
        ("weight", "Вес", charts.weight(st.days, st.weights, target)),
        ("kcal", "Калории", charts.kcal(st.days, {d: t.kcal for d, t in st.food.items()}, st.kcal_norm)),
        ("sleep", "Сон", charts.sleep(st.days, {d: round((s["woke_at"] - s["asleep_at"]) / 60000)
                                                for d, s in st.sleeps.items()})),
        ("pressure", "Давление", charts.pressure(st.days, [
            (local(p["measured_at"]), p["systolic"], p["diastolic"], p.get("pulse")) for p in st.pressures])),
    ]
    if sd1:
        low, high = num(profile.get("glucose_low"), None), num(profile.get("glucose_high"), None)
        candidates += [
            ("carbs", "Углеводы", charts.carbs(st.days, st.carbs)),
            ("glucose", "Сахар", charts.glucose(st.days, [(local(t), g) for t, g in st.glucose], low, high)),
        ]
    return [{"id": i, "title": t, "png": base64.b64encode(png).decode()} for i, t, png in candidates if png]
