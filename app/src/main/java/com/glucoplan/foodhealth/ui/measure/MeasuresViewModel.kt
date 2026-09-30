package com.glucoplan.foodhealth.ui.measure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.measure.BloodPressureRepository
import com.glucoplan.foodhealth.data.measure.MeasureSave
import com.glucoplan.foodhealth.data.measure.PressureRecord
import com.glucoplan.foodhealth.data.measure.WeightRecord
import com.glucoplan.foodhealth.data.measure.WeightRepository
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.profile.Profile
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MeasuresUi(
    val loaded: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    val profileId: String? = null,
    /** Последние записи выбранного человека, новые сверху. */
    val weights: List<WeightRecord> = emptyList(),
    val pressures: List<PressureRecord> = emptyList(),
)

enum class MeasureKind { WEIGHT, PRESSURE }

/** Открытый диалог замера: новая запись ([editingId] = null) или правка. */
data class MeasureDialogState(
    val kind: MeasureKind,
    val editingId: String? = null,
    val initial: List<String>,
    val initialAt: Long? = null,
    val errors: Map<String, String> = emptyMap(),
)

/** Экран «Замеры» (ТЗ 15.4): кому, виды замеров, ввод. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MeasuresViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    profiles: ProfileRepository,
    devicePrefs: DevicePrefs,
    private val weights: WeightRepository,
    private val pressures: BloodPressureRepository,
) : ViewModel() {

    /** null — тот, кто выбран на экране приёма пищи (аргумент), иначе владелец. */
    private val chosen = MutableStateFlow(savedStateHandle.get<String>(ARG_PROFILE))

    private val profileState = combine(profiles.observeProfiles(), devicePrefs.ownerId, chosen) { list, owner, c ->
        list to (c?.takeIf { id -> list.any { it.id == id } } ?: owner)
    }

    val state: StateFlow<MeasuresUi> = profileState.flatMapLatest { (list, profileId) ->
        if (profileId == null) return@flatMapLatest flowOf(MeasuresUi(true, list, null))
        combine(weights.observeRecent(profileId), pressures.observeRecent(profileId)) { w, p ->
            MeasuresUi(true, list, profileId, w, p)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasuresUi())

    private val _dialog = MutableStateFlow<MeasureDialogState?>(null)
    val dialog: StateFlow<MeasureDialogState?> = _dialog.asStateFlow()

    private val _recorded = MutableStateFlow<String?>(null)

    /** Новый замер записан — экран закрывается, сообщение показывает экран приёма пищи. */
    val recorded: StateFlow<String?> = _recorded.asStateFlow()

    fun onProfileSelected(id: String) {
        chosen.value = id
    }

    fun newMeasure(kind: MeasureKind) {
        _dialog.value = MeasureDialogState(
            kind,
            initial = when (kind) {
                MeasureKind.WEIGHT -> listOf("")
                MeasureKind.PRESSURE -> listOf("", "", "")
            },
        )
    }

    fun edit(kind: MeasureKind, id: String) {
        val s = state.value
        _dialog.value = when (kind) {
            MeasureKind.WEIGHT -> s.weights.firstOrNull { it.id == id }?.let {
                MeasureDialogState(kind, id, listOf(NumberText.format(it.kg, 1)), it.measuredAt)
            }
            MeasureKind.PRESSURE -> s.pressures.firstOrNull { it.id == id }?.let {
                MeasureDialogState(
                    kind, id, listOf(it.systolic.toString(), it.diastolic.toString(), it.pulse?.toString().orEmpty()),
                    it.measuredAt,
                )
            }
        }
    }

    fun dismiss() {
        _dialog.value = null
    }

    /** [at] null — «сейчас». */
    fun save(values: List<String>, at: Long?) {
        val dialog = _dialog.value ?: return
        val profileId = state.value.profileId ?: return
        viewModelScope.launch {
            val result = when (dialog.kind) {
                MeasureKind.WEIGHT -> weights.save(dialog.editingId, profileId, values[0], at)
                MeasureKind.PRESSURE -> pressures.save(dialog.editingId, profileId, values[0], values[1], values[2], at)
            }
            when (result) {
                is MeasureSave.Saved -> {
                    _dialog.value = null
                    // Новая запись — вернуться к еде (15.4); правка — остаться в списке
                    if (dialog.editingId == null) _recorded.value = result.message
                }
                is MeasureSave.Invalid -> _dialog.value = dialog.copy(errors = result.errors)
            }
        }
    }

    fun delete() {
        val dialog = _dialog.value ?: return
        val id = dialog.editingId ?: return
        viewModelScope.launch {
            when (dialog.kind) {
                MeasureKind.WEIGHT -> weights.delete(id)
                MeasureKind.PRESSURE -> pressures.delete(id)
            }
            _dialog.value = null
        }
    }

    fun onRecordedHandled() {
        _recorded.value = null
    }

    companion object {
        const val ARG_PROFILE = "profile"
    }
}
