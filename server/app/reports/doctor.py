"""PDF для врача (ТЗ 17.11): дневник профиля с СД1 за выбранный период.

Сначала данные (их проверяют тесты), затем отрисовка: таблица приёмов по дням и сводка за период —
углеводы по времени суток, сахар при приёмах, вес и рост, график углеводов по дням.
"""

import io
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta

from ..daydata import MSK, Family, day_bounds, local, local_date
from ..nutrition import num
from ..report_settings import ReportSettings
from . import period
from .text import SHORT_WEEKDAYS, fmt, hhmm

MAX_DAYS = 366


@dataclass(frozen=True)
class Row:
    time: str
    food: str
    carbs: float
    glucose: float | None
    dose: float | None


@dataclass(frozen=True)
class Day:
    day: date
    rows: list[Row]
    carbs: float


@dataclass(frozen=True)
class Measure:
    """Последнее значение на конец периода; [first] — первое за период, если оно другое."""
    value: float
    at: date
    first: tuple[float, date] | None = None


@dataclass
class Doctor:
    name: str
    birth_date: str | None
    first: date
    last: date
    per_xe: float | None                      # граммов углеводов в ХЕ, если ХЕ включены
    low: float | None
    high: float | None
    days: list[Day] = field(default_factory=list)
    slots: list[tuple[str, float, int]] = field(default_factory=list)   # название, средние углеводы, приёмов
    glucose: list[float] = field(default_factory=list)
    weight: Measure | None = None
    height: Measure | None = None
    carbs_png: bytes | None = None

    @property
    def span(self) -> int:
        return (self.last - self.first).days + 1

    @property
    def avg_carbs(self) -> float | None:
        return sum(d.carbs for d in self.days) / len(self.days) if self.days else None

    @property
    def in_range(self) -> int | None:
        if self.low is None or self.high is None:
            return None
        return sum(1 for g in self.glucose if self.low <= g <= self.high)

    def xe(self, carbs: float) -> str:
        return fmt(carbs / self.per_xe) if self.per_xe else ""


def _food(meal) -> str:
    """Кратко состав: «Гречка 150 г, Котлета 100 г»."""
    # Неразрывный пробел: «г» не отрывается от числа при переносе
    return ", ".join(f"{i.name} {fmt(i.weight_g, 0)}\u00a0г" for i in meal.items) or "—"


def _measure(rows: list[dict], key: str, start: int, end: int) -> Measure | None:
    before = [r for r in rows if r["measured_at"] < end and r.get(key) is not None]
    if not before:
        return None
    last = before[-1]
    inside = [r for r in before if r["measured_at"] >= start]
    first = None
    if inside and inside[0] is not last and num(inside[0][key]) != num(last[key]):
        first = (num(inside[0][key]), local_date(inside[0]["measured_at"]))
    return Measure(num(last[key]), local_date(last["measured_at"]), first)


def collect(family: Family, profile: dict, first: date, last: date, settings: ReportSettings,
            chart: bool = True) -> Doctor:
    pid = profile["id"]
    per_xe = num(profile.get("carbs_per_xe"))
    doc = Doctor(
        name=profile.get("name", ""), birth_date=profile.get("birth_date") or None, first=first, last=last,
        per_xe=per_xe if num(profile.get("show_xe")) and per_xe > 0 else None,
        low=num(profile.get("glucose_low"), None), high=num(profile.get("glucose_high"), None),
    )
    d = first
    while d <= last:
        s, e = day_bounds(d)
        meals = family.meals(pid, s, e)
        if meals:
            rows = [Row(hhmm(local(m.at)), _food(m), m.total.carbs, m.glucose, m.dose) for m in meals]
            doc.days.append(Day(d, rows, sum(m.total.carbs for m in meals)))
        d += timedelta(days=1)

    st = period.collect(family, profile, first, last, settings)
    doc.slots = [(s.name, sum(st.slots[s.name]) / len(st.slots[s.name]), len(st.slots[s.name]))
                 for s in settings.meal_slots if s.name in st.slots]
    doc.glucose = [g for _, g in st.glucose]
    start, _ = day_bounds(first)
    _, end = day_bounds(last)
    doc.weight = _measure(family.weights(pid), "weight_kg", start, end)
    doc.height = _measure(family.heights(pid), "height_cm", start, end)
    if chart and st.carbs:
        from . import charts

        doc.carbs_png = charts.carbs(st.days, st.carbs)
    return doc


# ---------- отрисовка ----------

def _date(d: date) -> str:
    return f"{d.day:02d}.{d.month:02d}.{d.year}"


def _age(birth: str | None, on: date) -> str:
    try:
        b = date.fromisoformat(birth) if birth else None
    except ValueError:
        b = None
    if b is None:
        return ""
    years = on.year - b.year - ((on.month, on.day) < (b.month, b.day))
    return f"дата рождения {_date(b)} ({period._plural(years, 'год', 'года', 'лет')})"


def summary_lines(doc: Doctor) -> list[str]:
    """Сводка за период — строками, как в PDF."""
    lines = [f"Еда записана в {period._in_days(len(doc.days))} из {doc.span}"]
    if doc.avg_carbs is not None:
        text = f"Углеводы в среднем в день: {fmt(doc.avg_carbs, 0)} г"
        if doc.per_xe:
            text += f" ({doc.xe(doc.avg_carbs)} ХЕ)"
        lines.append(text)
    for name, avg, n in doc.slots:
        text = f"{name}: в среднем {fmt(avg, 0)} г"
        if doc.per_xe:
            text += f" ({doc.xe(avg)} ХЕ)"
        lines.append(text + f", приёмов {n}")
    if doc.glucose:
        text = f"Сахар при приёмах: в среднем {fmt(sum(doc.glucose) / len(doc.glucose))} ммоль/л, замеров {len(doc.glucose)}"
        if doc.in_range is not None:
            text += (f"; в диапазоне {fmt(doc.low)}–{fmt(doc.high)}: {doc.in_range} из {len(doc.glucose)} "
                     f"({round(doc.in_range / len(doc.glucose) * 100)} %)")
        lines.append(text)
    else:
        lines.append("Сахар при приёмах не записан")
    for label, m, unit in (("Вес", doc.weight, "кг"), ("Рост", doc.height, "см")):
        if m is None:
            continue
        if m.first:
            lines.append(f"{label}: {fmt(m.first[0])} {unit} ({_date(m.first[1])}) → {fmt(m.value)} {unit} ({_date(m.at)})")
        else:
            lines.append(f"{label}: {fmt(m.value)} {unit} ({_date(m.at)})")
    return lines


def render(doc: Doctor, now: datetime | None = None) -> bytes:
    import matplotlib
    from fpdf import FPDF, FontFace
    from fpdf.enums import XPos, YPos

    now = now or datetime.now(MSK)
    fonts = f"{matplotlib.get_data_path()}/fonts/ttf"

    class Pdf(FPDF):
        def footer(self):
            self.set_y(-12)
            self.set_font("DejaVu", size=8)
            self.set_text_color(120)
            self.cell(0, 6, f"{doc.name} · {_date(doc.first)}–{_date(doc.last)} · стр. {self.page_no()} из {{nb}}",
                      align="C")
            self.set_text_color(0)

    pdf = Pdf(format="A4")
    pdf.set_margins(12, 12, 12)
    pdf.set_auto_page_break(True, margin=16)
    pdf.add_font("DejaVu", "", f"{fonts}/DejaVuSans.ttf")
    pdf.add_font("DejaVu", "B", f"{fonts}/DejaVuSans-Bold.ttf")
    pdf.set_title(f"Дневник питания — {doc.name}")
    pdf.set_creation_date(now)
    pdf.add_page()

    def text(value: str, size: float = 10, bold: bool = False, gap: float = 5.5):
        pdf.set_font("DejaVu", "B" if bold else "", size)
        pdf.multi_cell(0, gap, value, new_x=XPos.LMARGIN, new_y=YPos.NEXT)

    text(f"Дневник питания: {doc.name}", 15, bold=True, gap=8)
    head = [f"Период {_date(doc.first)} – {_date(doc.last)}"]
    age = _age(doc.birth_date, doc.last)
    if age:
        head.append(age)
    if doc.low is not None and doc.high is not None:
        head.append(f"целевой сахар {fmt(doc.low)}–{fmt(doc.high)} ммоль/л")
    if doc.per_xe:
        head.append(f"1 ХЕ = {fmt(doc.per_xe)} г углеводов")
    text(" · ".join(head), 9)
    pdf.set_text_color(120)
    text(f"Сформировано {now.strftime('%d.%m.%Y %H:%M')} по записям приложения «Еда и здоровье»; "
         "сахар и доза — записанные при приёмах пищи", 8, gap=4.5)
    pdf.set_text_color(0)
    pdf.ln(3)

    text("За период", 12, bold=True, gap=7)
    for s in summary_lines(doc):
        text(s)
    if doc.carbs_png:
        pdf.ln(2)
        pdf.image(io.BytesIO(doc.carbs_png), w=pdf.epw)
    pdf.ln(3)

    text("По дням", 12, bold=True, gap=7)
    if not doc.days:
        text("Приёмов пищи за период не записано")
    headings = ["Время", "Состав", "Углев., г"] + (["ХЕ"] if doc.per_xe else []) + ["Сахар", "Доза, ед."]
    widths = [14, 0, 19] + ([12] if doc.per_xe else []) + [15, 20]
    widths[1] = pdf.epw - sum(widths)
    align = ["CENTER", "LEFT", "RIGHT"] + (["RIGHT"] if doc.per_xe else []) + ["RIGHT", "RIGHT"]
    bold = FontFace(emphasis="BOLD")
    def day_table(target, day: Day):
        title = f"{SHORT_WEEKDAYS[day.day.weekday()]} {_date(day.day)} — углеводы за день {fmt(day.carbs, 0)} г"
        if doc.per_xe:
            title += f" ({doc.xe(day.carbs)} ХЕ)"
        target.ln(1)
        target.set_font("DejaVu", "B", 10)
        target.multi_cell(0, 6, title, new_x=XPos.LMARGIN, new_y=YPos.NEXT)
        target.set_font("DejaVu", size=9)
        with target.table(col_widths=widths, text_align=align, line_height=5, width=target.epw,
                          headings_style=FontFace(emphasis="BOLD", fill_color=235)) as table:
            row = table.row()
            for h in headings:
                row.cell(h)
            for r in day.rows:
                row = table.row()
                row.cell(r.time)
                row.cell(r.food)
                row.cell(fmt(r.carbs, 0), style=bold)
                if doc.per_xe:
                    row.cell(doc.xe(r.carbs))
                row.cell(fmt(r.glucose) if r.glucose is not None else "")
                row.cell(fmt(r.dose) if r.dose is not None else "")

    for day in doc.days:
        # День не рвётся между страницами: не влез — с новой страницы (если он вообще влезает в страницу)
        with pdf.offset_rendering() as trial:
            day_table(trial, day)
        if trial.page_break_triggered and pdf.get_y() > pdf.t_margin + 1:
            pdf.add_page()
        day_table(pdf, day)
    return bytes(pdf.output())


def build(family: Family, profile: dict, first: date, last: date, settings: ReportSettings) -> bytes:
    return render(collect(family, profile, first, last, settings))
