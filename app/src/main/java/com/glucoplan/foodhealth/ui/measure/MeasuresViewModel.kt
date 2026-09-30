package com.glucoplan.foodhealth.ui.measure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.measure.BloodPressureRepository
import com.glucoplan.foodhealth.data.measure.MeasureSave
import com.glucoplan.foodhealth.data.measure.PressureRecord
import com.glucoplan.foodhealth.data.measure.SleepQuality
import com.glucoplan.foodhealth.data.measure.SleepRecord
import com.glucoplan.foodhealth.data.measure.SleepRepository
import com.glucoplan.foodhealth.data.measure.SleepTime
import com.glucoplan.foodhealth.data.measure.WaterNorm
import com.glucoplan.foodhealth.data.measure.WaterRecord
import com.glucoplan.foodhealth.data.measure.WaterRepository
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class MeasuresUi(
    val loaded: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    val profileId: String? = null,
    /** Последние записи выбранного человека, новые сверху. */
    val weights: List<WeightRecord> = emptyList(),
    val pressures: List<PressureRecord> = emptyList(),
    val sleeps: List<SleepRecord> = emptyList(),
    /** У выбранного профиля включено «Считать воду» (15.3); иначе карточки воды нет. */
    val waterEnabled: Boolean = false,
    val waterToday: List<WaterRecord> = emptyList(),
    val waterLastVolume: Int = WaterNorm.DEFAULT_ML,
    /** Дневная норма, мл; null — вес ещё не записан. */
    val waterNormMl: Int? = null,
)

enum class MeasureKind { WEIGHT, PRESSURE, WATER }

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
    private val sleeps: SleepRepository,
    private val water: WaterRepository,
) : ViewModel() {

    /** null — тот, кто выбран на экране приёма пищи (аргумент), иначе владелец. */
    private val chosen = MutableStateFlow(savedStateHandle.get<String>(ARG_PROFILE))

    private val profileState = combine(profiles.observeProfiles(), devicePrefs.ownerId, chosen) { list, owner, c ->
        list to (c?.takeIf { id -> list.any { it.id == id } } ?: owner)
    }

    val state: StateFlow<MeasuresUi> = profileState.flatMapLatest { (list, profileId) ->
        if (profileId == null) return@flatMapLatest flowOf(MeasuresUi(true, list, null))
        val profile = list.firstOrNull { it.id == profileId }
        combine(
            weights.observeRecent(profileId),
            pressures.observeRecent(profileId),
            sleeps.observeRecent(profileId),
            water.observeToday(profileId),
            water.observeLastVolume(profileId),
        ) { w, p, sl, wt, last ->
            MeasuresUi(
                loaded = true,
                profiles = list,
                profileId = profileId,
                weights = w,
                pressures = p,
                sleeps = sl,
                waterEnabled = profile?.waterEnabled == true,
                waterToday = wt,
                waterLastVolume = last,
                waterNormMl = profile?.let { WaterNorm.dailyNormMl(it.waterMlPerKg, w.firstOrNull()?.kg) },
            )
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
                MeasureKind.WATER -> listOf(state.value.waterLastVolume.toString())
            },
        )
    }

    fun edit(kind: MeasureKind, id: String) {
        val s = state.value
        _dialog.value = when (kind) {
            MeasureKind.WEIGHT -> s.weights.firstOrNull { it.id == id }?.let {
                MeasureDialogState(kind, id, listOf(NumberText.format(it.kg, 1)), it.measuredAt)
            }
            MeasureKind.WATER -> s.waterToday.firstOrNull { it.id == id }?.let {
                MeasureDialogState(kind, id, listOf(it.ml.toString()), it.drunkAt)
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
                MeasureKind.WATER -> water.save(dialog.editingId, profileId, values[0], at)
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
                MeasureKind.WATER -> water.delete(id)
            }
            _dialog.value = null
        }
    }

    /** «+ 250 мл»: вода последним объёмом одним нажатием — и обратно к еде (15.4). */
    fun quickWater() {
        val s = state.value
        val profileId = s.profileId ?: return
        viewModelScope.launch {
            val r = water.save(null, profileId, s.waterLastVolume.toString(), null)
            if (r is MeasureSave.Saved) _recorded.value = r.message
        }
    }

    private val _sleepDialog = MutableStateFlow<SleepDialogState?>(null)
    val sleepDialog: StateFlow<SleepDialogState?> = _sleepDialog.asStateFlow()

    /** Новый сон: проснулся «сейчас», заснул — как в прошлый раз или в 23:00. */
    fun newSleep() {
        val now = LocalDateTime.now()
        _sleepDialog.value = SleepDialogState(
            wakeDate = now.toLocalDate(),
            wakeTime = now.toLocalTime().withSecond(0).withNano(0),
            asleepTime = SleepTime.defaultAsleep(state.value.sleeps.firstOrNull()?.asleepAt),
        )
    }

    fun editSleep(id: String) {
        val r = state.value.sleeps.firstOrNull { it.id == id } ?: return
        val zone = ZoneId.systemDefault()
        val woke = Instant.ofEpochMilli(r.wokeAt).atZone(zone)
        _sleepDialog.value = SleepDialogState(
            editingId = id,
            wakeDate = woke.toLocalDate(),
            wakeTime = woke.toLocalTime().withSecond(0).withNano(0),
            asleepTime = Instant.ofEpochMilli(r.asleepAt).atZone(zone).toLocalTime().withSecond(0).withNano(0),
            quality = r.quality,
        )
    }

    fun dismissSleep() {
        _sleepDialog.value = null
    }

    fun saveSleep(wakeDate: LocalDate, wakeTime: LocalTime, asleepTime: LocalTime, quality: SleepQuality?) {
        val dialog = _sleepDialog.value ?: return
        val profileId = state.value.profileId ?: return
        val (asleepAt, wokeAt) = SleepTime.moments(wakeDate, wakeTime, asleepTime)
        viewModelScope.launch {
            when (val r = sleeps.save(dialog.editingId, profileId, asleepAt, wokeAt, quality)) {
                is MeasureSave.Saved -> {
                    _sleepDialog.value = null
                    if (dialog.editingId == null) _recorded.value = r.message
                }
                is MeasureSave.Invalid -> _sleepDialog.value = dialog.copy(
                    wakeDate = wakeDate, wakeTime = wakeTime, asleepTime = asleepTime, quality = quality, errors = r.errors,
                )
            }
        }
    }

    fun deleteSleep() {
        val id = _sleepDialog.value?.editingId ?: return
        viewModelScope.launch {
            sleeps.delete(id)
            _sleepDialog.value = null
        }
    }

    fun onRecordedHandled() {
        _recorded.value = null
    }

    companion object {
        const val ARG_PROFILE = "profile"
    }
}
