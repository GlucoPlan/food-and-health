"""Настройки сервера — только из переменных окружения, без путей конкретной машины.

FH_FAMILY_KEY  ключ семьи (обязателен). В репозиторий не попадает (ТЗ 8.5).
FH_DATA_DIR    папка данных: база fh.db и фото в photos/. Переезд = перенос этой папки.
FH_REPORTS_CONFIG  настройки отчётов (время приёмов пищи), по умолчанию /etc/foodhealth/reports.json.
"""

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    family_key: str
    data_dir: Path
    # None — настройки отчётов по умолчанию (тесты, разработка)
    reports_config: Path | None = None

    @property
    def db_path(self) -> Path:
        return self.data_dir / "fh.db"

    @property
    def photos_dir(self) -> Path:
        return self.data_dir / "photos"


def from_env() -> Settings:
    key = os.environ.get("FH_FAMILY_KEY", "").strip()
    if len(key) < 16:
        raise RuntimeError("FH_FAMILY_KEY не задан или короче 16 символов")
    data_dir = Path(os.environ.get("FH_DATA_DIR", "data")).resolve()
    reports = Path(os.environ.get("FH_REPORTS_CONFIG", "/etc/foodhealth/reports.json"))
    return Settings(family_key=key, data_dir=data_dir, reports_config=reports)
