package com.glucoplan.foodhealth.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.profile.Profile
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import com.glucoplan.foodhealth.data.report.DoctorPdfPeriod
import com.glucoplan.foodhealth.data.report.DoctorPdfShare
import com.glucoplan.foodhealth.data.report.PdfResult
import com.glucoplan.foodhealth.data.report.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Диалог «PDF для врача» (ТЗ 17.11); [profileId] — выбранный профиль с СД1. */
data class DoctorPdfDialog(
    val profileId: String,
    val first: LocalDate,
    val last: LocalDate,
    val loading: Boolean = false,
    val error: String? = null,
    val warning: String? = null,
)

/**
 * PDF для врача (ТЗ 17.11): кнопка есть, если в семье есть профиль с «Дневником СД1», — на любом телефоне,
 * а не только у его владельца.
 */
@HiltViewModel
class DoctorPdfViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val reports: ReportRepository,
    private val share: DoctorPdfShare,
) : ViewModel() {

    val sd1Profiles: StateFlow<List<Profile>> = profiles.observeProfiles()
        .map { list -> list.filter { it.sd1Enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dialog = MutableStateFlow<DoctorPdfDialog?>(null)
    val dialog: StateFlow<DoctorPdfDialog?> = _dialog.asStateFlow()

    fun open() {
        val profile = sd1Profiles.value.firstOrNull() ?: return
        val (first, last) = DoctorPdfPeriod.default(LocalDate.now())
        _dialog.value = DoctorPdfDialog(profile.id, first, last)
    }

    fun dismiss() {
        if (_dialog.value?.loading != true) _dialog.value = null
    }

    fun selectProfile(id: String) = _dialog.update { it?.copy(profileId = id, error = null) }

    fun setFirst(date: LocalDate) = _dialog.update { it?.copy(first = date, error = null) }

    fun setLast(date: LocalDate) = _dialog.update { it?.copy(last = date, error = null) }

    fun create() {
        val d = _dialog.value ?: return
        if (d.loading) return
        DoctorPdfPeriod.error(d.first, d.last, LocalDate.now())?.let { error ->
            _dialog.update { it?.copy(error = error) }
            return
        }
        val profile = sd1Profiles.value.firstOrNull { it.id == d.profileId } ?: return
        _dialog.update { it?.copy(loading = true, error = null, warning = null) }
        viewModelScope.launch {
            when (val result = reports.doctorPdf(profile.id, d.first, d.last)) {
                is PdfResult.Ok -> {
                    val shared = share.share(DoctorPdfPeriod.fileName(profile.name, d.first, d.last), result.pdf)
                    _dialog.update {
                        when {
                            !shared -> it?.copy(loading = false, error = "Нет приложения, чтобы отправить PDF")
                            // Синхронизация не удалась — PDF ушёл, но о пропуске записей стоит сказать
                            result.syncWarning != null -> it?.copy(loading = false, warning = result.syncWarning)
                            else -> null
                        }
                    }
                }
                PdfResult.NotConfigured -> _dialog.update { it?.copy(loading = false, error = "Сервер не настроен") }
                is PdfResult.Failed -> _dialog.update { it?.copy(loading = false, error = result.message) }
            }
        }
    }
}
