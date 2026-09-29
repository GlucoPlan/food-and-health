package com.glucoplan.foodhealth.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Очередь отправки: какие записи изменены на телефоне и ещё не ушли на сервер.
 * Заполняется триггерами (SyncTriggers), поэтому изменение нельзя «забыть» отметить.
 * [version] растёт при каждом изменении: после отправки удаляются только неизменившиеся.
 */
@Entity(tableName = "sync_outbox", primaryKeys = ["tbl", "id"])
data class SyncOutboxEntity(val tbl: String, val id: String, val version: Long)

/**
 * Состояние синхронизации — одна строка (id = 1). Хранится в базе, а не в настройках:
 * курсор меняется в одной транзакции с полученными данными, а при очистке базы (ТЗ 8.4)
 * сбрасывается вместе с ней.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int,
    /** 1 — применяются изменения с сервера, триггеры очереди молчат. */
    val applying: Int,
    /** Последний полученный номер изменения на сервере. */
    val cursor: Long,
    /** Первая синхронизация прошла (выбор «объединить / заменить» уже не нужен). */
    val initialized: Boolean,
)

/** Поля записи, которых эта версия приложения не знает (от более новой): отправляются обратно без потерь. */
@Entity(tableName = "sync_extra", primaryKeys = ["tbl", "id"])
data class SyncExtraEntity(val tbl: String, val id: String, val json: String)
