"""Графики отчётов за период (ТЗ 17.6): PNG в памяти, без окна. Нет данных — нет графика (None)."""

import io
import os
import tempfile
from datetime import date, datetime

# Кэш шрифтов matplotlib — во временной папке, а не в домашней пользователя сервиса (это папка данных).
# Своя папка у каждого пользователя: папку, созданную другим (например, тестами), не записать
os.environ.setdefault("MPLCONFIGDIR", os.path.join(tempfile.gettempdir(), f"foodhealth-matplotlib-{os.getuid()}"))

import matplotlib  # noqa: E402

matplotlib.use("Agg")
import matplotlib.dates as mdates  # noqa: E402
import matplotlib.pyplot as plt  # noqa: E402

from .text import SHORT_WEEKDAYS  # noqa: E402

WIDTH, HEIGHT, DPI = 8, 4, 100
MAIN = "#2e7d32"
SECOND = "#1565c0"
NORM = "#c62828"
BAND = "#a5d6a7"
TARGET_MARGIN = 3  # кг: цель дальше этого от веса не рисуется линией


def _figure(title: str):
    fig, ax = plt.subplots(figsize=(WIDTH, HEIGHT), dpi=DPI)
    ax.set_title(title, fontsize=13)
    ax.grid(True, alpha=0.3)
    ax.spines[["top", "right"]].set_visible(False)
    return fig, ax


def _png(fig) -> bytes:
    buf = io.BytesIO()
    fig.tight_layout()
    fig.savefig(buf, format="png")
    plt.close(fig)
    return buf.getvalue()


def _day_axis(ax, days: list[date]) -> None:
    """Подписи дней: неделя — «Пн 22», месяц — числа."""
    ax.set_xlim(days[0].toordinal() - 0.6, days[-1].toordinal() + 0.6)
    if len(days) <= 7:
        ax.set_xticks([d.toordinal() for d in days], [f"{SHORT_WEEKDAYS[d.weekday()]} {d.day}" for d in days])
    else:
        step = 1 if len(days) <= 16 else 2
        shown = days[::step]
        ax.set_xticks([d.toordinal() for d in shown], [str(d.day) for d in shown])


def weight(days: list[date], values: dict[date, float], target: float | None) -> bytes | None:
    if not values:
        return None
    fig, ax = _figure("Вес, кг")
    xs = sorted(values)
    ys = [values[d] for d in xs]
    ax.plot([d.toordinal() for d in xs], ys, "o-", color=MAIN)
    if len(xs) >= 2:
        n = len(xs)
        x = [d.toordinal() for d in xs]
        mx, my = sum(x) / n, sum(ys) / n
        den = sum((a - mx) ** 2 for a in x)
        if den:
            k = sum((a - mx) * (b - my) for a, b in zip(x, ys)) / den
            ends = [days[0].toordinal(), days[-1].toordinal()]
            ax.plot(ends, [my + k * (e - mx) for e in ends], "--", color=SECOND, label="тренд")
    if target is not None:
        low, high = min(ys), max(ys)
        if low - TARGET_MARGIN <= target <= high + TARGET_MARGIN:
            ax.axhline(target, color=NORM, linestyle=":", label=f"цель {target:g}")
        else:
            # Далёкая цель сплющила бы график в линию — только подпись
            ax.plot([], [], " ", label=f"цель {target:g} кг")
    if len(ax.get_legend_handles_labels()[0]):
        ax.legend(loc="best", fontsize=9)
    _day_axis(ax, days)
    return _png(fig)


def kcal(days: list[date], values: dict[date, float], norms: dict[date, float]) -> bytes | None:
    if not values:
        return None
    fig, ax = _figure("Калории по дням")
    ax.bar([d.toordinal() for d in days], [values.get(d, 0) for d in days], color=MAIN, width=0.7)
    if norms:
        # Норма у каждого дня своя (зависит от веса) — отрезок над каждым столбиком
        for i, d in enumerate(sorted(norms)):
            x = d.toordinal()
            ax.hlines(norms[d], x - 0.45, x + 0.45, color=NORM, linewidth=2, label="норма" if i == 0 else None)
        ax.legend(loc="best", fontsize=9)
    _day_axis(ax, days)
    return _png(fig)


def sleep(days: list[date], minutes: dict[date, int]) -> bytes | None:
    if not minutes:
        return None
    fig, ax = _figure("Сон, часов")
    ax.bar([d.toordinal() for d in days], [minutes.get(d, 0) / 60 for d in days], color=SECOND, width=0.7)
    ax.axhline(7, color=NORM, linestyle=":", label="7 часов")
    ax.legend(loc="best", fontsize=9)
    _day_axis(ax, days)
    return _png(fig)


def pressure(days: list[date], points: list[tuple[datetime, int, int, int | None]]) -> bytes | None:
    if not points:
        return None
    fig, ax = _figure("Давление и пульс")
    t = [p[0] for p in points]
    ax.plot(t, [p[1] for p in points], "o-", color=NORM, label="верхнее")
    ax.plot(t, [p[2] for p in points], "o-", color=SECOND, label="нижнее")
    pulse = [(p[0], p[3]) for p in points if p[3] is not None]
    if pulse:
        ax.plot([p[0] for p in pulse], [p[1] for p in pulse], "x", color="gray", label="пульс")
    ax.legend(loc="best", fontsize=9)
    ax.xaxis.set_major_formatter(mdates.DateFormatter("%d.%m", tz=t[0].tzinfo))
    return _png(fig)


def carbs(days: list[date], values: dict[date, float]) -> bytes | None:
    if not values:
        return None
    fig, ax = _figure("Углеводы по дням, г")
    ax.bar([d.toordinal() for d in days], [values.get(d, 0) for d in days], color="#ef6c00", width=0.7)
    _day_axis(ax, days)
    return _png(fig)


def glucose(days: list[date], points: list[tuple[datetime, float]], low: float | None,
            high: float | None) -> bytes | None:
    if not points:
        return None
    fig, ax = _figure("Сахар при приёмах пищи, ммоль/л")
    if low is not None and high is not None:
        ax.axhspan(low, high, color=BAND, alpha=0.5, label=f"цель {low:g}–{high:g}")
    ax.plot([p[0] for p in points], [p[1] for p in points], "o", color=NORM)
    ax.xaxis.set_major_formatter(mdates.DateFormatter("%d.%m", tz=points[0][0].tzinfo))
    if low is not None:
        ax.legend(loc="best", fontsize=9)
    return _png(fig)
