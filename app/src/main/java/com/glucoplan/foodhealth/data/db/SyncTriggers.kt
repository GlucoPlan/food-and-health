package com.glucoplan.foodhealth.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.glucoplan.foodhealth.data.sync.SyncTables

/**
 * Триггеры очереди отправки: любая вставка или изменение синхронизируемой записи
 * ставит её в sync_outbox. Пока применяются изменения с сервера (sync_state.applying = 1),
 * триггеры молчат — полученное не отправляется обратно.
 *
 * Room триггеры не хранит в схеме, поэтому они создаются при каждом открытии базы
 * (CREATE TRIGGER IF NOT EXISTS) — и на новой базе, и после миграций.
 */
object SyncTriggers {

    val callback = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) = install(db)
    }

    fun install(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO sync_state (id, applying, cursor, initialized) VALUES (1, 0, 0, 0)")
        // Если приложение упало посреди применения, флаг мог остаться включённым
        db.execSQL("UPDATE sync_state SET applying = 0 WHERE id = 1")
        SyncTables.ALL.forEach { table ->
            listOf("INSERT", "UPDATE").forEach { event ->
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS sync_${table}_${event.lowercase()}
                    AFTER $event ON `$table`
                    WHEN (SELECT applying FROM sync_state WHERE id = 1) IS NOT 1
                    BEGIN
                        INSERT OR REPLACE INTO sync_outbox (tbl, id, version)
                        VALUES ('$table', NEW.id, (SELECT IFNULL(MAX(version), 0) + 1 FROM sync_outbox));
                    END
                    """.trimIndent()
                )
            }
        }
    }
}
