package com.glucoplan.foodhealth.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.report.Report
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
import java.time.LocalDate
import javax.inject.Inject

/** Выбор дня отчёта (ТЗ 17.10): по умолчанию вчера, вперёд — не дальше сегодня. */
object ReportDates {
    fun default(today: LocalDate): LocalDate = today.minusDays(1)
    fun canGoForward(date: LocalDate, today: LocalDate): Boolean = date < today
}

data class ReportsState(
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

    private val _state = MutableStateFlow(ReportsState(date = ReportDates.default(LocalDate.now())))
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load(syncFirst = false)
    }

    fun previous() = setDate(_state.value.date.minusDays(1))

    fun next() {
        val date = _state.value.date
        if (ReportDates.canGoForward(date, today)) setDate(date.plusDays(1))
    }

    /** «Сформировать сейчас»: сначала синхронизация, чтобы свежие записи попали в отчёт. */
    fun refresh() = load(syncFirst = true)

    private fun setDate(date: LocalDate) {
        _state.update { it.copy(date = date, report = null) }
        load(syncFirst = false)
    }

    private fun load(syncFirst: Boolean) {
        job?.cancel()
        val date = _state.value.date
        _state.update {
            it.copy(loading = true, error = null, warning = null, canGoForward = ReportDates.canGoForward(date, today))
        }
        job = viewModelScope.launch {
            val owner = devicePrefs.ownerId.first()
            val result = if (owner == null) {
                ReportResult.Failed("Выберите владельца телефона в настройках")
            } else {
                reports.day(owner, date, syncFirst)
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
