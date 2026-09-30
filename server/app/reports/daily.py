"""Ежедневный отчёт (ТЗ 17.5): краткая выжимка, под ней подробности."""

from datetime import date, timedelta

from ..daydata import Family, Meal, day_bounds, local
from ..norms import NUTRIENTS, DailyNorms
from ..nutrition import ZERO, num
from .text import B, day_title, duration, fmt, hhmm, line, percent, section, signed

# ТЗ 17.5, 17.2
DEFICIT_SHARE = 0.70         # витамина меньше 70 % нормы — «не хватило»
LOW_PROTEIN_SHARE = 0.80     # замечание «белков мало»
LOW_KCAL_SHARE = 0.80
HIGH_KCAL_SHARE = 1.10
INCOMPLETE_KCAL_SHARE = 0.5  # калорий меньше половины нормы — «возможно, данные неполные»
MAX_NOTES = 3
EXCESS_CODES = ("na",)       # верхняя граница есть только у натрия (соль)

SLEEP_QUALITY = {"bad": "плохо", "normal": "нормально", "good": "хорошо"}
BODY_PARTS = [
    ("neck", "шея"), ("chest", "грудь"), ("waist", "талия"), ("belly", "живот"), ("hips", "бёдра"),
    ("thigh", "бедро"), ("calf", "голень"), ("arm", "плечо"), ("wrist", "запястье"),
]


def build(family: Family, profile: dict, day: date) -> dict:
    pid = profile["id"]
    start, end = day_bounds(day)
    meals = family.meals(pid, start, end)
    pressures = family.pressures(pid, start, end)
    sleeps = family.sleeps(pid, start, end)
    waters = family.waters(pid, start, end)
    bodies = family.bodies(pid, start, end)
    heights = [h for h in family.heights(pid) if start <= h["measured_at"] < end]
    weights_today = [w for w in family.weights(pid) if start <= w["measured_at"] < end]
    norms = family.norms(profile, day)
    sd1 = bool(num(profile.get("sd1_enabled")))

    report = {
        "kind": "day",
        "date": day.isoformat(),
        "profile_id": pid,
        "profile_name": profile.get("name", ""),
        "title": day_title(day),
        "incomplete": False,
        "empty": False,
        "summary": [],
        "sections": [],
    }
    if not (meals or pressures or sleeps or waters or bodies or heights or weights_today):
        report["empty"] = True
        report["summary"] = [line("Ничего не записано", style="muted")]
        return report

    total = ZERO
    for m in meals:
        total = total + m.total
    kcal_norm = norms.final.kcal
    incomplete = len(meals) < 2 or (kcal_norm is not None and total.kcal < kcal_norm * INCOMPLETE_KCAL_SHARE)
    report["incomplete"] = incomplete

    summary = []
    if incomplete:
        summary.append(line("Возможно, данные неполные", style="warn"))
    if sd1:
        summary += _sd1_summary(profile, meals, total)
    summary += _food_summary(total, norms)
    summary += _weight_summary(family, profile, day, norms)
    summary += _sleep_lines(sleeps)
    summary += _water_summary(family, profile, day, waters)
    summary += _pressure_summary(pressures)
    summary += [line(n, style="warn") for n in _notes(total, norms, bool(meals))]
    report["summary"] = summary

    sections = []
    if meals:
        sections.append(section("Приёмы пищи", _meal_lines(profile, meals, sd1)))
    if meals:
        sections.append(section("Витамины и минералы", _micro_lines(total, norms)))
    if len(pressures) > 1:
        sections.append(section("Давление", [
            line(f"{hhmm(local(p['measured_at']))} — {_bp(p)}") for p in pressures
        ]))
    if bodies:
        sections.append(section("Обхваты", [_body_line(b) for b in bodies]))
    if heights:
        sections.append(section("Рост", [line(f"{fmt(num(heights[-1].get('height_cm')))} см")]))
    sections.append(section("Активность", [line("Пока не учитывается", style="muted")]))
    report["sections"] = sections
    return report


def _food_summary(total, norms: DailyNorms) -> list[dict]:
    if total.kcal <= 0 and not total.protein:
        return [line("Еда не записана", style="muted")]
    n = norms.final
    if n.kcal:
        kcal = line("Калории: ", B(fmt(total.kcal, 0)), f" из {fmt(n.kcal, 0)} ({percent(total.kcal, n.kcal)} %)")
    else:
        kcal = line("Калории: ", B(fmt(total.kcal, 0)))

    def part(name, value, norm):
        return f"{name} {fmt(value, 0)}" + (f" из {fmt(norm, 0)}" if norm else "") + " г"

    return [
        kcal,
        line(" · ".join([
            part("Белки", total.protein, n.protein), part("Жиры", total.fat, n.fat), part("Углеводы", total.carbs, n.carbs),
        ])),
    ]


def _sd1_summary(profile: dict, meals: list[Meal], total) -> list[dict]:
    carbs = [B(f"Углеводы за день: {fmt(total.carbs, 0)} г")]
    xe = _xe(profile, total.carbs)
    if xe:
        carbs.append(f" · {xe}")
    lines = [line(*carbs, style="big"), line(f"Приёмов пищи: {len(meals)}")]
    glucose = [m.glucose for m in meals if m.glucose is not None]
    if glucose:
        low, high = num(profile.get("glucose_low"), None), num(profile.get("glucose_high"), None)
        values = "; ".join(fmt(g) for g in glucose)
        if low is not None and high is not None:
            inside = sum(1 for g in glucose if low <= g <= high)
            lines.append(line(
                f"Сахар при приёмах: {values} — в диапазоне {fmt(low)}–{fmt(high)}: ", B(f"{inside} из {len(glucose)}"),
            ))
        else:
            lines.append(line(f"Сахар при приёмах: {values}"))
    return lines


def _xe(profile: dict, carbs: float) -> str | None:
    per_xe = num(profile.get("carbs_per_xe"))
    if not num(profile.get("show_xe")) or per_xe <= 0:
        return None
    return f"{fmt(carbs / per_xe)} ХЕ"


def _first(rows: list[dict], start: int, end: int) -> dict | None:
    return next((r for r in rows if start <= r["measured_at"] < end), None)


def _reference_weight(rows: list[dict], day: date) -> dict | None:
    """Вес для сравнения на [day]: первый за тот день, иначе последний до него."""
    start, end = day_bounds(day)
    return _first(rows, start, end) or Family.last_before(rows, "measured_at", start)


def _weight_summary(family: Family, profile: dict, day: date, norms: DailyNorms) -> list[dict]:
    rows = family.weights(profile["id"])
    start, end = day_bounds(day)
    today = _first(rows, start, end)
    if today is None:
        return []
    kg = num(today.get("weight_kg"))
    changes = []
    for label, ref_day in (("к вчера", day - timedelta(days=1)), ("за неделю", day - timedelta(days=7))):
        ref = _reference_weight(rows, ref_day)
        if ref is not None:
            changes.append(f"{signed(kg - num(ref.get('weight_kg')))} {label}")
    parts = ["Вес: ", B(f"{fmt(kg)} кг")]
    if changes:
        parts.append(f" ({', '.join(changes)})")
    target = num(profile.get("target_weight_kg"), None)
    if target is not None and not norms.child:
        left = kg - target
        if abs(left) < 0.05:
            parts.append(" · цель достигнута")
        else:
            parts.append(f" · до цели {fmt(abs(left))} кг")
    return [line(*parts)]


def _sleep_lines(sleeps: list[dict]) -> list[dict]:
    lines = []
    for s in sleeps:
        minutes = round((s["woke_at"] - s["asleep_at"]) / 60000)
        quality = SLEEP_QUALITY.get(s.get("quality"))
        lines.append(line(
            "Сон: ", B(duration(minutes)),
            f" ({hhmm(local(s['asleep_at']))}–{hhmm(local(s['woke_at']))})" + (f", {quality}" if quality else ""),
        ))
    return lines


def _water_summary(family: Family, profile: dict, day: date, waters: list[dict]) -> list[dict]:
    if not num(profile.get("water_enabled")):
        return []
    drunk = sum(num(w.get("ml")) for w in waters)
    _, end = day_bounds(day)
    weight = Family.last_before(family.weights(profile["id"]), "measured_at", end)
    per_kg = num(profile.get("water_ml_per_kg"), 30.0)
    if weight is None:
        return [line(f"Вода: {fmt(drunk / 1000)} л")]
    norm = round(per_kg * num(weight.get("weight_kg")))
    return [line("Вода: ", B(f"{fmt(drunk / 1000)}"), f" из {fmt(norm / 1000)} л")]


def _bp(p: dict) -> str:
    text = f"{p['systolic']}/{p['diastolic']}"
    return text + (f", пульс {p['pulse']}" if p.get("pulse") is not None else "")


def _pressure_summary(pressures: list[dict]) -> list[dict]:
    if not pressures:
        return []
    if len(pressures) == 1:
        return [line("Давление: ", B(_bp(pressures[0])))]
    sys = [p["systolic"] for p in pressures]
    dia = [p["diastolic"] for p in pressures]
    avg = f"{round(sum(sys) / len(sys))}/{round(sum(dia) / len(dia))}"
    return [line(
        "Давление: в среднем ", B(avg),
        f" ({min(sys)}–{max(sys)} / {min(dia)}–{max(dia)}, {len(pressures)} изм.)",
    )]


def _notes(total, norms: DailyNorms, has_meals: bool) -> list[str]:
    """До трёх замечаний, самые заметные первыми."""
    if not has_meals:
        return []
    n = norms.final
    found: list[tuple[float, str]] = []
    if n.protein and total.protein < n.protein * LOW_PROTEIN_SHARE:
        found.append((1 - total.protein / n.protein, f"Белков мало: {percent(total.protein, n.protein)} % нормы"))
    if n.kcal and total.kcal < n.kcal * LOW_KCAL_SHARE:
        found.append((1 - total.kcal / n.kcal, f"Калорий мало: {percent(total.kcal, n.kcal)} % нормы"))
    if n.kcal and total.kcal > n.kcal * HIGH_KCAL_SHARE:
        found.append((total.kcal / n.kcal - 1, f"Калорий больше нормы: {percent(total.kcal, n.kcal)} %"))
    if norms.fiber_min_g and total.fiber < norms.fiber_min_g:
        found.append((1 - total.fiber / norms.fiber_min_g,
                      f"Клетчатки {fmt(total.fiber, 0)} г из {fmt(norms.fiber_min_g, 0)}"))
    if norms.salt_max_g and total.salt > norms.salt_max_g:
        found.append((total.salt / norms.salt_max_g - 1,
                      f"Соли {fmt(total.salt)} г при норме до {fmt(norms.salt_max_g, 0)}"))
    found.sort(key=lambda f: -f[0])
    return [text for _, text in found[:MAX_NOTES]]


def _meal_lines(profile: dict, meals: list[Meal], sd1: bool) -> list[dict]:
    lines = []
    for m in meals:
        t = m.total
        head = [B(f"{hhmm(local(m.at))} — {fmt(t.kcal, 0)} ккал")]
        if sd1:
            head.append(" · ")
            head.append(B(f"углеводы {fmt(t.carbs, 0)} г"))
            xe = _xe(profile, t.carbs)
            if xe:
                head.append(f" ({xe})")
            if m.glucose is not None:
                head.append(f" · сахар {fmt(m.glucose)}")
            if m.dose is not None:
                head.append(f" · доза {fmt(m.dose)} ед.")
        lines.append(line(*head, style="big" if sd1 else "normal"))
        lines.append(line(f"Б {fmt(t.protein, 0)} · Ж {fmt(t.fat, 0)} · У {fmt(t.carbs, 0)}", style="muted"))
        for item in m.items:
            n = item.nutrients
            lines.append(line(
                f"{item.name} — {fmt(item.weight_g, 0)} г: {fmt(n.kcal, 0)} ккал, "
                f"Б {fmt(n.protein)} · Ж {fmt(n.fat)} · У {fmt(n.carbs)}"
            ))
        if m.notes:
            lines.append(line(f"Заметка: {m.notes}", style="muted"))
    return lines


def _micro_lines(total, norms: DailyNorms) -> list[dict]:
    if not norms.micro:
        return [line("Нормы не рассчитаны: укажите пол и дату рождения в профиле", style="muted")]
    if total.micro_kcal <= 0:
        return [line("Нет данных: у съеденных продуктов витамины и минералы не заполнены", style="muted")]
    lines = [line(f"Витамины известны для {percent(total.micro_kcal, total.kcal)} % калорий", style="muted")]
    lacking, fine, excess = [], [], []
    for code, norm in norms.micro.items():
        name = NUTRIENTS.get(code, (code, ""))[0]
        share = total.micro.get(code, 0.0) / norm
        text = f"{name} {round(share * 100)} %"
        if code in EXCESS_CODES and share > 1:
            excess.append(text)
        elif share < DEFICIT_SHARE:
            lacking.append(text)
        else:
            fine.append(text)
    if lacking:
        lines.append(line(B("Не хватило: "), ", ".join(lacking)))
    if fine:
        lines.append(line(B("В норме: "), ", ".join(fine)))
    if excess:
        lines.append(line(B("Перебор: "), ", ".join(excess), style="warn"))
    return lines


def _body_line(b: dict) -> dict:
    parts = [f"{label} {fmt(num(b.get(key)))}" for key, label in BODY_PARTS if b.get(key) is not None]
    return line(f"{hhmm(local(b['measured_at']))} — " + ", ".join(parts) + " см")
