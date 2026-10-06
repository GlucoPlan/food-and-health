"""API сервера «Еда и здоровье» (ТЗ 9, 17): /health, /sync, /photos, /reports/day, /reports/week, /reports/month,
/reports/analysis.

Каждый запрос — с заголовком X-Family-Key, без него 401.
"""

import hmac
import re
from datetime import date
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Query, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from . import analysis, config
from .daydata import TABLES as REPORT_TABLES
from .daydata import Family
from .db import TABLES, Store
from . import report_settings
from .reports import daily, period

PAGE_SIZE = 1000
MAX_CHANGES = 5000
MAX_PHOTO_BYTES = 5 * 1024 * 1024
# id фото задаёт телефон — это UUID из имени файла (одинаковое на всех телефонах)
PHOTO_ID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")


class Change(BaseModel):
    table: str
    id: Annotated[str, Field(min_length=1, max_length=64)]
    data: dict
    updated_at: int
    deleted: bool


class SyncRequest(BaseModel):
    device_id: Annotated[str, Field(min_length=1, max_length=64)]
    cursor: Annotated[int, Field(ge=0)]
    changes: Annotated[list[Change], Field(max_length=MAX_CHANGES)] = []


def create_app(settings: config.Settings | None = None) -> FastAPI:
    settings = settings or config.from_env()
    store = Store(settings.db_path)
    settings.photos_dir.mkdir(parents=True, exist_ok=True)

    def check_key(x_family_key: Annotated[str | None, Header()] = None) -> None:
        # compare_digest — сравнение без утечки по времени
        if x_family_key is None or not hmac.compare_digest(x_family_key.encode(), settings.family_key.encode()):
            raise HTTPException(status_code=401, detail="Неверный ключ семьи")

    app = FastAPI(title="Еда и здоровье", dependencies=[Depends(check_key)], docs_url=None, redoc_url=None)

    @app.get("/health")
    def health() -> dict:
        return {"status": "ok", "records": store.count()}

    @app.post("/sync")
    def sync(request: SyncRequest) -> dict:
        unknown = sorted({c.table for c in request.changes} - TABLES)
        if unknown:
            raise HTTPException(status_code=400, detail=f"Неизвестные таблицы: {', '.join(unknown)}")
        applied = store.apply(request.device_id, [c.model_dump() for c in request.changes])
        changes, cursor, has_more = store.changes_since(request.cursor, request.device_id, PAGE_SIZE, applied)
        return {"cursor": cursor, "has_more": has_more, "changes": changes}

    @app.put("/photos/{photo_id}")
    async def put_photo(photo_id: str, request: Request) -> dict:
        """Загрузить фото под id телефона. Повторная загрузка безопасна: фото неизменяемы."""
        if not PHOTO_ID.match(photo_id):
            raise HTTPException(status_code=422, detail="id фото — UUID")
        # Читаем по частям и обрываем на лимите: памяти на сервере мало (ТЗ 9)
        body = bytearray()
        async for chunk in request.stream():
            body += chunk
            if len(body) > MAX_PHOTO_BYTES:
                raise HTTPException(status_code=413, detail="Фото больше 5 МБ")
        if not bytes(body[:3]) == b"\xff\xd8\xff":
            raise HTTPException(status_code=415, detail="Нужен JPEG")
        path = settings.photos_dir / f"{photo_id}.jpg"
        if not path.exists():
            tmp = path.with_suffix(".part")
            tmp.write_bytes(body)
            tmp.replace(path)
        return {"id": photo_id}

    @app.get("/photos/{photo_id}")
    def get_photo(photo_id: str) -> FileResponse:
        path = settings.photos_dir / f"{photo_id}.jpg"
        if not PHOTO_ID.match(photo_id) or not path.is_file():
            raise HTTPException(status_code=404, detail="Фото не найдено")
        return FileResponse(path, media_type="image/jpeg")

    @app.get("/reports/day")
    def report_day(profile_id: str, date_: Annotated[date, Query(alias="date")]) -> dict:
        """Ежедневный отчёт (ТЗ 17.5) по тому, что дошло до сервера; не хранится, строится заново."""
        family = Family(store.records(REPORT_TABLES))
        profile = family.profiles.get(profile_id)
        if profile is None:
            raise HTTPException(status_code=404, detail="Профиль не найден")
        return daily.build(family, profile, date_)

    @app.get("/reports/week")
    def report_week(profile_id: str, date_: Annotated[date, Query(alias="date")]) -> dict:
        """Недельный отчёт (ТЗ 17.6) за неделю с [date] — любым её днём; с графиками."""
        family = Family(store.records(REPORT_TABLES))
        profile = family.profiles.get(profile_id)
        if profile is None:
            raise HTTPException(status_code=404, detail="Профиль не найден")
        first, last = period.week_of(date_)
        return period.build(family, profile, first, last, report_settings.load(settings.reports_config))

    @app.get("/reports/month")
    def report_month(profile_id: str, date_: Annotated[date, Query(alias="date")]) -> dict:
        """Месячный отчёт (ТЗ 17.6) за месяц с [date] — любым его днём; с графиками."""
        family = Family(store.records(REPORT_TABLES))
        profile = family.profiles.get(profile_id)
        if profile is None:
            raise HTTPException(status_code=404, detail="Профиль не найден")
        first, last = period.month_of(date_)
        return period.build(family, profile, first, last, report_settings.load(settings.reports_config), kind="month")

    @app.get("/reports/analysis")
    def report_analysis(profile_id: str) -> dict:
        """Последний ночной анализ Claude (ТЗ 17.9) в виде отчёта; прошлые не хранятся. Дата в запросе не нужна."""
        return (analysis.report_for(analysis.analysis_dir(settings.data_dir), profile_id)
                or analysis.empty_report(profile_id))

    return app
