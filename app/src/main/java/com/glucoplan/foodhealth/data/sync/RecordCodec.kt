package com.glucoplan.foodhealth.data.sync

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Запись таблицы ↔ JSON для сервера — одинаково для всех таблиц, по колонкам из PRAGMA table_info.
 * Ключи JSON — имена колонок. Поля, которых эта версия не знает (от более новой), хранятся
 * в sync_extra и возвращаются в JSON при отправке.
 */
class RecordCodec(private val db: SupportSQLiteDatabase) {

    private data class Column(val name: String, val type: String)

    private val columns = mutableMapOf<String, List<Column>>()

    private fun columnsOf(table: String): List<Column> = columns.getOrPut(table) {
        db.query("PRAGMA table_info(`$table`)").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Column(c.getString(c.getColumnIndexOrThrow("name")), c.getString(c.getColumnIndexOrThrow("type"))))
                }
            }
        }
    }

    /** Записи по id: JSON вместе с сохранёнными неизвестными полями. Отсутствующих id в ответе нет. */
    fun read(table: String, ids: Collection<String>): Map<String, JsonObject> {
        if (ids.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, JsonObject>()
        ids.chunked(500).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            db.query("SELECT * FROM `$table` WHERE id IN ($marks)", chunk.toTypedArray()).use { c ->
                while (c.moveToNext()) {
                    val row = rowToJson(c)
                    result[(row["id"] as JsonPrimitive).content] = row
                }
            }
        }
        extras(table, result.keys).forEach { (id, extra) ->
            result[id] = JsonObject(extra + result.getValue(id))
        }
        return result
    }

    private fun rowToJson(c: Cursor): JsonObject = JsonObject(
        (0 until c.columnCount).associate { i ->
            c.getColumnName(i) to when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> JsonNull
                Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(c.getDouble(i))
                else -> JsonPrimitive(c.getString(i))
            }
        }
    )

    private fun extras(table: String, ids: Collection<String>): Map<String, Map<String, JsonElement>> {
        if (ids.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Map<String, JsonElement>>()
        ids.chunked(500).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            db.query("SELECT id, json FROM sync_extra WHERE tbl = ? AND id IN ($marks)", (listOf(table) + chunk).toTypedArray())
                .use { c ->
                    while (c.moveToNext()) {
                        runCatching { Json.parseToJsonElement(c.getString(1)).jsonObject }
                            .getOrNull()?.let { result[c.getString(0)] = it }
                    }
                }
        }
        return result
    }

    /**
     * Записать запись с сервера: известные колонки — в таблицу, остальное — в sync_extra.
     * Сначала UPDATE, потом INSERT: UPSERT в SQLite появился позже Android 10 (minSdk 29).
     * Возвращает false, если записать не удалось (например, не хватает обязательного поля).
     */
    fun write(table: String, id: String, data: JsonObject): Boolean {
        val known = columnsOf(table).associateBy { it.name }
        val values = ContentValues()
        data.forEach { (key, value) -> known[key]?.let { put(values, it, value) } }
        values.put("id", id)
        val extra = data.filterKeys { it !in known }

        val ok = try {
            db.update(table, SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf(id)) > 0 ||
                db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values) != -1L
        } catch (e: android.database.SQLException) {
            false
        }
        if (!ok) return false

        if (extra.isEmpty()) {
            db.delete("sync_extra", "tbl = ? AND id = ?", arrayOf(table, id))
        } else {
            db.insert(
                "sync_extra",
                SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("tbl", table)
                    put("id", id)
                    put("json", JsonObject(extra).toString())
                },
            )
        }
        return true
    }

    private fun put(values: ContentValues, column: Column, value: JsonElement) {
        if (value is JsonNull) {
            values.putNull(column.name)
            return
        }
        val p = value as? JsonPrimitive ?: run {
            // Вложенный объект или массив в известной колонке — храним текстом
            values.put(column.name, value.toString())
            return
        }
        when {
            column.type.equals("INTEGER", ignoreCase = true) ->
                (p.longOrNull ?: p.booleanOrNull?.let { if (it) 1L else 0L } ?: p.doubleOrNull?.toLong())
                    ?.let { values.put(column.name, it) } ?: values.put(column.name, p.content)
            column.type.equals("REAL", ignoreCase = true) ->
                p.doubleOrNull?.let { values.put(column.name, it) } ?: values.put(column.name, p.content)
            else -> values.put(column.name, p.content)
        }
    }
}
