package com.glucoplan.foodhealth.ui.measure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.measure.MeasureSave
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
    /** Последние записи веса выбранного человека, новые сверху. */
    val weights: List<WeightRecord> = emptyList(),
)

/** Диалог веса: новая запись ([record] = null) или правка существующей. */
data class WeightDialogState(
    val record: WeightRecord? = null,
    val valueError: String? = null,
    val timeError: String? = null,
)

/** Экран «Замеры» (ТЗ 15.4): кому, виды замеров, ввод. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MeasuresViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    profiles: ProfileRepository,
    devicePrefs: DevicePrefs,
    private val weights: WeightRepository,
) : ViewModel() {

    /** null — тот, кто выбран на экране приёма пищи (аргумент), иначе владелец. */
    private val chosen = MutableStateFlow(savedStateHandle.get<String>(ARG_PROFILE))

    private val profileState = combine(profiles.observeProfiles(), devicePrefs.ownerId, chosen) { list, owner, c ->
        list to (c?.takeIf { id -> list.any { it.id == id } } ?: owner)
    }

    val state: StateFlow<MeasuresUi> = profileState.flatMapLatest { (list, profileId) ->
        val recent = profileId?.let { weights.observeRecent(it) } ?: flowOf(emptyList())
        combine(flowOf(list), recent) { l, w -> MeasuresUi(true, l, profileId, w) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasuresUi())

    private val _weightDialog = MutableStateFlow<WeightDialogState?>(null)
    val weightDialog: StateFlow<WeightDialogState?> = _weightDialog.asStateFlow()

    private val _recorded = MutableStateFlow<String?>(null)

    /** Новый замер записан — экран закрывается, сообщение показывает экран приёма пищи. */
    val recorded: StateFlow<String?> = _recorded.asStateFlow()

    fun onProfileSelected(id: String) {
        chosen.value = id
    }

    fun newWeight() {
        _weightDialog.value = WeightDialogState()
    }

    fun editWeight(record: WeightRecord) {
        _weightDialog.value = WeightDialogState(record)
    }

    fun dismissWeight() {
        _weightDialog.value = null
    }

    /** [at] null — «сейчас». */
    fun saveWeight(text: String, at: Long?) {
        val dialog = _weightDialog.value ?: return
        val profileId = state.value.profileId ?: return
        viewModelScope.launch {
            when (val r = weights.save(dialog.record?.id, profileId, text, at)) {
                is MeasureSave.Saved -> {
                    _weightDialog.value = null
                    // Новая запись — вернуться к еде; правка — остаться в списке
                    if (dialog.record == null) _recorded.value = r.message
                }
                is MeasureSave.Invalid -> _weightDialog.value = dialog.copy(valueError = r.valueError, timeError = r.timeError)
            }
        }
    }

    fun deleteWeight() {
        val id = _weightDialog.value?.record?.id ?: return
        viewModelScope.launch {
            weights.delete(id)
            _weightDialog.value = null
        }
    }

    fun onRecordedHandled() {
        _recorded.value = null
    }

    companion object {
        const val ARG_PROFILE = "profile"
    }
}
