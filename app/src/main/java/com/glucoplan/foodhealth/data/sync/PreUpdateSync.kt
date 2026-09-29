package com.glucoplan.foodhealth.data.sync

import com.glucoplan.foodhealth.data.db.SyncDao
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PreUpdateResult {
    /** Можно обновляться. */
    data object Proceed : PreUpdateResult

    /** Синхронизация не удалась, а неотправленные изменения есть — спросить пользователя (ТЗ 8.2). */
    data class Warn(val pending: Int, val reason: String) : PreUpdateResult
}

/** ТЗ 8.2: перед обновлением приложения — синхронизация с сервером. */
@Singleton
class PreUpdateSync @Inject constructor(
    private val engine: SyncEngine,
    private val syncDao: SyncDao,
) {
    suspend fun run(): PreUpdateResult {
        val reason = when (val result = engine.sync()) {
            // Сервера нет — синхронизировать некуда, обновляемся
            SyncResult.NotConfigured, is SyncResult.Success -> return PreUpdateResult.Proceed
            is SyncResult.NeedsChoice -> "не выбрано, объединять ли данные телефона с сервером (Настройки → Синхронизация)"
            is SyncResult.Failed -> result.message
        }
        val pending = syncDao.outbox().size
        // Отправлять нечего — терять тоже нечего
        return if (pending == 0) PreUpdateResult.Proceed else PreUpdateResult.Warn(pending, reason)
    }
}
