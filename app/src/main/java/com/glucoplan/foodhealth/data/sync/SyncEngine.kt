package com.glucoplan.foodhealth.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.meal.MealDraft
import com.glucoplan.foodhealth.data.meal.MealDraftStore
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Первое подключение телефона с данными к непустому серверу (ТЗ 6.1, п. 5). */
enum class FirstSyncChoice {
    /** Отправить свои данные на сервер (объединить). */
    MERGE,

    /** Удалить данные телефона и загрузить с сервера. */
    REPLACE,
}

sealed interface SyncResult {
    data class Success(val sent: Int, val received: Int) : SyncResult
    data object NotConfigured : SyncResult

    /** Нужно спросить пользователя, объединять или заменять. */
    data class NeedsChoice(val serverRecords: Int) : SyncResult
    data class Failed(val message: String) : SyncResult
}

/**
 * Синхронизация (ТЗ 6): отправить очередь изменений, получить изменения после курсора.
 * Одновременно идёт только одна. Ошибки не бросаются наружу — пишутся в статус.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val db: AppDatabase,
    private val settings: SyncSettings,
    private val backend: SyncBackend,
    private val devicePrefs: DevicePrefs,
    private val draftStore: MealDraftStore,
) {
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Сколько изменений отправлять одним запросом (сервер принимает до 5000). */
    internal var batchSize = 500

    suspend fun sync(choice: FirstSyncChoice? = null, now: () -> Long = System::currentTimeMillis): SyncResult =
        mutex.withLock {
            val config = settings.currentConfig() ?: return SyncResult.NotConfigured
            _running.value = true
            try {
                val result = run(config, choice)
                if (result is SyncResult.Success) settings.markSuccess(now())
                result
            } catch (e: SyncException) {
                settings.markError(e.message ?: "Ошибка синхронизации", now())
                SyncResult.Failed(e.message ?: "Ошибка синхронизации")
            } finally {
                _running.value = false
            }
        }

    private suspend fun run(config: ServerConfig, choice: FirstSyncChoice?): SyncResult {
        if (!state().initialized) {
            val serverRecords = backend.health(config)
            if (serverRecords > 0 && localHasData()) {
                when (choice) {
                    null -> return SyncResult.NeedsChoice(serverRecords)
                    FirstSyncChoice.REPLACE -> clearLocal()
                    // Всё, что есть на телефоне, уже в очереди (миграция 4 → 5 и триггеры)
                    FirstSyncChoice.MERGE -> Unit
                }
            }
        }

        val deviceId = devicePrefs.deviceId()
        var sent = 0
        var received = 0
        // Ограничение на случай, если очередь всё время пополняется правками
        repeat(MAX_ROUNDS) {
            val batch = withContext(Dispatchers.IO) { readOutbox() }
            val response = backend.sync(config, SyncRequest(deviceId, state().cursor, batch.changes))
            received += withContext(Dispatchers.IO) { applyResponse(batch, response) }
            sent += batch.changes.size
            // Сервер отдал всё, а очередь ушла не полной порцией — значит, и её больше нет.
            // Правки, сделанные во время запроса, уйдут следующей синхронизацией.
            if (!response.hasMore && batch.versions.size < batchSize) {
                markInitialized()
                return SyncResult.Success(sent, received)
            }
        }
        markInitialized()
        return SyncResult.Success(sent, received)
    }

    /** Порция очереди: версии для последующего удаления и сами записи. */
    private class OutboxBatch(val versions: Map<Pair<String, String>, Long>, val changes: List<WireChange>)

    private fun readOutbox(): OutboxBatch {
        val sql = raw()
        val entries = sql.query("SELECT tbl, id, version FROM sync_outbox ORDER BY version LIMIT $batchSize").use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getLong(2))) }
        }
        val codec = RecordCodec(sql)
        val changes = entries.groupBy { it.first }.flatMap { (table, rows) ->
            if (table !in SyncTables.ALL) return@flatMap emptyList()
            codec.read(table, rows.map { it.second }).map { (id, data) ->
                WireChange(
                    table = table,
                    id = id,
                    data = data,
                    updatedAt = (data["updated_at"] as? JsonPrimitive)?.longOrNull ?: 0L,
                    deleted = (data["deleted"] as? JsonPrimitive)?.longOrNull == 1L,
                )
            }
        }
        return OutboxBatch(entries.associate { (it.first to it.second) to it.third }, changes)
    }

    /**
     * В одной транзакции: убрать отправленное из очереди (если не менялось во время запроса),
     * записать полученное и новый курсор. Запись с неотправленной правкой на телефоне
     * не перезаписывается — правка уйдёт следующей и победит как пришедшая позже.
     */
    private fun applyResponse(batch: OutboxBatch, response: SyncResponse): Int {
        var applied = 0
        db.runInTransaction {
            val sql = raw()
            batch.versions.forEach { (key, version) ->
                sql.execSQL("DELETE FROM sync_outbox WHERE tbl = ? AND id = ? AND version = ?", arrayOf(key.first, key.second, version))
            }
            sql.execSQL("UPDATE sync_state SET applying = 1 WHERE id = 1")
            try {
                val codec = RecordCodec(sql)
                response.changes.forEach { change ->
                    if (change.table !in SyncTables.ALL) return@forEach
                    val pending = sql.query(
                        "SELECT 1 FROM sync_outbox WHERE tbl = ? AND id = ?", arrayOf(change.table, change.id)
                    ).use { it.moveToFirst() }
                    if (!pending && codec.write(change.table, change.id, change.data)) applied++
                }
            } finally {
                sql.execSQL("UPDATE sync_state SET applying = 0 WHERE id = 1")
            }
            sql.execSQL("UPDATE sync_state SET cursor = ? WHERE id = 1", arrayOf(response.cursor))
        }
        return applied
    }

    /** «Заменить данными сервера»: данные телефона удаляются, загрузка пойдёт с нуля. */
    private suspend fun clearLocal() {
        withContext(Dispatchers.IO) {
            db.runInTransaction {
                val sql = raw()
                SyncTables.ALL.forEach { sql.execSQL("DELETE FROM `$it`") }
                sql.execSQL("DELETE FROM sync_outbox")
                sql.execSQL("DELETE FROM sync_extra")
                sql.execSQL("UPDATE sync_state SET cursor = 0 WHERE id = 1")
            }
        }
        // Черновик приёма ссылается на удалённые записи
        draftStore.replace(MealDraft())
    }

    private data class State(val cursor: Long, val initialized: Boolean)

    private suspend fun state(): State = withContext(Dispatchers.IO) {
        raw().query("SELECT cursor, initialized FROM sync_state WHERE id = 1").use { c ->
            if (c.moveToFirst()) State(c.getLong(0), c.getInt(1) == 1) else State(0, false)
        }
    }

    private suspend fun markInitialized() = withContext(Dispatchers.IO) {
        raw().execSQL("UPDATE sync_state SET initialized = 1 WHERE id = 1")
    }

    private suspend fun localHasData(): Boolean = withContext(Dispatchers.IO) {
        SyncTables.ALL.any { table -> raw().query("SELECT EXISTS(SELECT 1 FROM `$table`)").use { c -> c.moveToFirst(); c.getInt(0) == 1 } }
    }

    private fun raw(): SupportSQLiteDatabase = db.openHelper.writableDatabase

    private companion object {
        const val MAX_ROUNDS = 1000
    }
}
