"""Хранилище записей (вариант А): одна таблица, данные сущности — JSON.

Сервер не знает полей продуктов и блюд: новые поля и миграции Room его не касаются.
seq — монотонный номер изменения на сервере, он же курсор синхронизации (ТЗ 6).
"""

import json
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Iterable, Iterator

# Таблицы, которые синхронизируются (ТЗ 5.2). Новая таблица на телефоне = одна строка здесь.
TABLES = frozenset({
    "profile", "product", "pan", "dish", "dish_version", "dish_ingredient", "meal", "meal_item",
    # Этап 2: замеры (SPEC.md, раздел 15) — все сразу, чтобы сервер обновлялся один раз
    "height", "weight", "blood_pressure", "sleep", "water", "body_measure",
})

SCHEMA = """
CREATE TABLE IF NOT EXISTS record (
    tbl         TEXT    NOT NULL,
    id          TEXT    NOT NULL,
    data        TEXT    NOT NULL,
    updated_at  INTEGER NOT NULL,
    deleted     INTEGER NOT NULL,
    device_id   TEXT    NOT NULL,
    seq         INTEGER NOT NULL,
    received_at INTEGER NOT NULL,
    PRIMARY KEY (tbl, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS record_seq ON record (seq);
"""


class Store:
    def __init__(self, path: Path):
        self.path = path
        path.parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as conn:
            conn.execute("PRAGMA journal_mode=WAL")
            conn.executescript(SCHEMA)

    def _connect(self) -> sqlite3.Connection:
        # isolation_level=None: транзакции открываем сами (BEGIN IMMEDIATE)
        conn = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        conn.row_factory = sqlite3.Row
        return conn

    @contextmanager
    def _write(self) -> Iterator[sqlite3.Connection]:
        conn = self._connect()
        try:
            conn.execute("BEGIN IMMEDIATE")
            yield conn
            conn.execute("COMMIT")
        except BaseException:
            conn.execute("ROLLBACK")
            raise
        finally:
            conn.close()

    def apply(self, device_id: str, changes: list[dict]) -> range:
        """Записать изменения телефона. Побеждает пришедшее позже (ТЗ 6): запись просто заменяется.

        Возвращает номера (seq), которые получили эти изменения.
        """
        now = int(time.time() * 1000)
        with self._write() as conn:
            seq = conn.execute("SELECT COALESCE(MAX(seq), 0) FROM record").fetchone()[0]
            first = seq + 1
            for change in changes:
                seq += 1
                conn.execute(
                    """
                    INSERT INTO record (tbl, id, data, updated_at, deleted, device_id, seq, received_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tbl, id) DO UPDATE SET
                        data = excluded.data, updated_at = excluded.updated_at, deleted = excluded.deleted,
                        device_id = excluded.device_id, seq = excluded.seq, received_at = excluded.received_at
                    """,
                    (
                        change["table"], change["id"], json.dumps(change["data"], ensure_ascii=False),
                        change["updated_at"], int(change["deleted"]), device_id, seq, now,
                    ),
                )
        return range(first, seq + 1)

    def changes_since(
        self, cursor: int, device_id: str, limit: int, just_applied: range = range(0),
    ) -> tuple[list[dict], int, bool]:
        """Изменения после курсора: (изменения, новый курсор, есть ли ещё).

        Не отдаются записи, присланные этим же запросом ([just_applied]), а при cursor > 0 —
        и все, последним изменённые этим телефоном: они у него уже есть.
        cursor = 0 — полная загрузка (новый телефон, очистка базы): свои прежние записи тоже отдаются.
        """
        conn = self._connect()
        try:
            rows = conn.execute(
                """
                SELECT tbl, id, data, updated_at, deleted, device_id, seq FROM record
                WHERE seq > ? AND (? = 0 OR device_id != ?) AND seq NOT BETWEEN ? AND ?
                ORDER BY seq LIMIT ?
                """,
                (cursor, cursor, device_id, just_applied.start, just_applied.stop - 1, limit + 1),
            ).fetchall()
            has_more = len(rows) > limit
            rows = rows[:limit]
            if has_more:
                new_cursor = rows[-1]["seq"]
            else:
                # Всё отдано: курсор — последний номер на сервере, включая свои пропущенные записи
                new_cursor = max(cursor, conn.execute("SELECT COALESCE(MAX(seq), 0) FROM record").fetchone()[0])
        finally:
            conn.close()
        changes = [
            {
                "table": r["tbl"], "id": r["id"], "data": json.loads(r["data"]),
                "updated_at": r["updated_at"], "deleted": bool(r["deleted"]), "device_id": r["device_id"],
            }
            for r in rows
        ]
        return changes, new_cursor, has_more

    def records(self, tables: Iterable[str]) -> list[dict]:
        """Все записи таблиц, включая удалённые: {"table", "id", "data", "deleted"} (для отчётов)."""
        tables = list(tables)
        conn = self._connect()
        try:
            rows = conn.execute(
                f"SELECT tbl, id, data, deleted FROM record WHERE tbl IN ({','.join('?' * len(tables))})",
                tables,
            ).fetchall()
        finally:
            conn.close()
        return [
            {"table": r["tbl"], "id": r["id"], "data": json.loads(r["data"]), "deleted": bool(r["deleted"])}
            for r in rows
        ]

    def count(self) -> int:
        conn = self._connect()
        try:
            return conn.execute("SELECT COUNT(*) FROM record").fetchone()[0]
        finally:
            conn.close()
