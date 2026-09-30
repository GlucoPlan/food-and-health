package com.glucoplan.foodhealth.data.report

import com.glucoplan.foodhealth.data.sync.SyncEngine
import com.glucoplan.foodhealth.data.sync.SyncException
import com.glucoplan.foodhealth.data.sync.SyncResult
import com.glucoplan.foodhealth.data.sync.SyncSettings
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ReportResult {
    /** [syncWarning] — синхронизация перед отчётом не удалась: в отчёте нет свежих записей этого телефона. */
    data class Ok(val report: Report, val syncWarning: String? = null) : ReportResult
    data object NotConfigured : ReportResult
    data class Failed(val message: String) : ReportResult
}

@Singleton
class ReportRepository @Inject constructor(
    private val backend: ReportBackend,
    private val settings: SyncSettings,
    private val engine: SyncEngine,
) {
    /**
     * Отчёт [kind] за день [date] или его период. [syncFirst] — «Сформировать сейчас» (ТЗ 17.10): сначала отправить
     * записи телефона, чтобы они попали в отчёт. Если синхронизация не удалась, отчёт всё равно запрашивается.
     */
    suspend fun report(kind: ReportKind, profileId: String, date: LocalDate, syncFirst: Boolean): ReportResult {
        val config = settings.currentConfig() ?: return ReportResult.NotConfigured
        val warning = if (syncFirst) (engine.sync() as? SyncResult.Failed)?.message else null
        return try {
            ReportResult.Ok(backend.report(config, kind, profileId, date), warning)
        } catch (e: SyncException) {
            ReportResult.Failed(
                if (e is SyncException.Network) "Отчёты доступны только при связи с сервером" else e.message ?: "Ошибка"
            )
        }
    }
}
