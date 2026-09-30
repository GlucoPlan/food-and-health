package com.glucoplan.foodhealth.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.report.Report
import com.glucoplan.foodhealth.data.report.ReportKind
import com.glucoplan.foodhealth.data.report.ReportRepository
import com.glucoplan.foodhealth.data.report.ReportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Выбор периода отчёта (ТЗ 17.10). День: по умолчанию вчера, вперёд — до сегодня.
 * Неделя (с понедельника): по умолчанию прошлая полная, вперёд — до текущей.
 */
object ReportDates {
    fun default(kind: ReportKind, today: LocalDate): LocalDate = when (kind) {
        ReportKind.DAY -> today.minusDays(1)
        ReportKind.WEEK -> weekStart(today).minusWeeks(1)
    }

    fun canGoForward(kind: ReportKind, date: LocalDate, today: LocalDate): Boolean = when (kind) {
        ReportKind.DAY -> date < today
        ReportKind.WEEK -> date < weekStart(today)
    }

    fun step(kind: ReportKind, date: LocalDate, forward: Boolean): LocalDate {
        val days = if (kind == ReportKind.DAY) 1L else 7L
        return if (forward) date.plusDays(days) else date.minusDays(days)
    }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

data class ReportsState(
    val kind: ReportKind = ReportKind.DAY,
    /** Выбранный день или понедельник выбранной недели. */
    val date: LocalDate,
    val canGoForward: Boolean = true,
    val loading: Boolean = false,
    val report: Report? = null,
    val error: String? = null,
    val warning: String? = null,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reports: ReportRepository,
    private val devicePrefs: DevicePrefs,
) : ViewModel() {

    private val today: LocalDate get() = LocalDate.now()

    /** Выбранная дата на каждой вкладке: переключение вкладок её не сбрасывает. */
    private val dates = ReportKind.entries.associateWith { ReportDates.default(it, LocalDate.now()) }.toMutableMap()

    private val _state = MutableStateFlow(ReportsState(date = dates.getValue(ReportKind.DAY)))
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load(syncFirst = false)
    }

    fun selectKind(kind: ReportKind) {
        if (kind == _state.value.kind) return
        _state.update { it.copy(kind = kind, date = dates.getValue(kind), report = null) }
        load(syncFirst = false)
    }

    fun previous() = setDate(ReportDates.step(_state.value.kind, _state.value.date, forward = false))

    fun next() {
        val s = _state.value
        if (ReportDates.canGoForward(s.kind, s.date, today)) setDate(ReportDates.step(s.kind, s.date, forward = true))
    }

    /** «Сформировать сейчас»: сначала синхронизация, чтобы свежие записи попали в отчёт. */
    fun refresh() = load(syncFirst = true)

    private fun setDate(date: LocalDate) {
        dates[_state.value.kind] = date
        _state.update { it.copy(date = date, report = null) }
        load(syncFirst = false)
    }

    private fun load(syncFirst: Boolean) {
        job?.cancel()
        val kind = _state.value.kind
        val date = _state.value.date
        _state.update {
            it.copy(loading = true, error = null, warning = null, canGoForward = ReportDates.canGoForward(kind, date, today))
        }
        job = viewModelScope.launch {
            val owner = devicePrefs.ownerId.first()
            val result = if (owner == null) {
                ReportResult.Failed("Выберите владельца телефона в настройках")
            } else {
                reports.report(kind, owner, date, syncFirst)
            }
            _state.update {
                when (result) {
                    is ReportResult.Ok -> it.copy(loading = false, report = result.report, warning = result.syncWarning)
                    ReportResult.NotConfigured -> it.copy(loading = false, error = "Сервер не настроен")
                    is ReportResult.Failed -> it.copy(loading = false, error = result.message)
                }
            }
        }
    }
}
