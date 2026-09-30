package com.glucoplan.foodhealth.ui.measure

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.measure.BloodPressureRepository
import com.glucoplan.foodhealth.data.measure.BodyMeasureRepository
import com.glucoplan.foodhealth.data.measure.BodyPart
import com.glucoplan.foodhealth.data.measure.BodyRecord
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
    val bodies: List<BodyRecord> = emptyList(),
    /** У выбранного профиля включено «Считать воду» (15.3); иначе карточки воды нет. */
    val waterEnabled: Boolean = false,
    val waterToday: List<WaterRecord> = emptyList(),
    val waterLastVolume: Int = WaterNorm.DEFAULT_ML,
    /** Дневная норма, мл; null — вес ещё не записан. */
    val waterNormMl: Int? = null,
)

enum class MeasureKind { WEIGHT, PRESSURE, WATER, BODY }

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
    private val bodies: BodyMeasureRepository,
) : ViewModel() {

    /** null — тот, кто выбран на экране приёма пищи (аргумент), иначе владелец. */
    private val chosen = MutableStateFlow(savedStateHandle.get<String>(ARG_PROFILE))

    /** Открыт из «Истории» на конкретной записи («weight:<id>»): после правки — сразу назад. */
    private val editArg: String? = savedStateHandle.get<String>(ARG_EDIT)

    private val _closed = MutableStateFlow(false)

    /** Правка из «Истории» закончена — экран закрывается. */
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

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
            combine(water.observeLastVolume(profileId), bodies.observeRecent(profileId)) { l, b -> l to b },
        ) { w, p, sl, wt, (last, b) ->
            MeasuresUi(
                loaded = true,
                profiles = list,
                profileId = profileId,
                weights = w,
                pressures = p,
                sleeps = sl,
                bodies = b,
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
                MeasureKind.BODY -> BodyPart.entries.map { "" }
            },
        )
    }

    /** Правка записи по id — в том числе старой, не из последних пяти (открытой из «Истории»). */
    fun edit(kind: MeasureKind, id: String) {
        viewModelScope.launch {
            _dialog.value = when (kind) {
                MeasureKind.WEIGHT -> weights.get(id)?.let {
                    MeasureDialogState(kind, id, listOf(NumberText.format(it.kg, 1)), it.measuredAt)
                }
                MeasureKind.BODY -> bodies.get(id)?.let { r ->
                    MeasureDialogState(kind, id, BodyPart.entries.map { p -> r.values[p]?.let { NumberText.format(it, 1) }.orEmpty() }, r.measuredAt)
                }
                MeasureKind.WATER -> water.get(id)?.let {
                    MeasureDialogState(kind, id, listOf(it.ml.toString()), it.drunkAt)
                }
                MeasureKind.PRESSURE -> pressures.get(id)?.let {
                    MeasureDialogState(
                        kind, id, listOf(it.systolic.toString(), it.diastolic.toString(), it.pulse?.toString().orEmpty()),
                        it.measuredAt,
                    )
                }
            }
            if (_dialog.value == null) closeIfFromHistory()
        }
    }

    /** Открыт из «Истории» — после правки, удаления или отмены сразу назад. */
    private fun closeIfFromHistory() {
        if (editArg != null) _closed.value = true
    }

    fun dismiss() {
        _dialog.value = null
        closeIfFromHistory()
    }

    /** [at] null — «сейчас». */
    fun save(values: List<String>, at: Long?) {
        val dialog = _dialog.value ?: return
        // При правке запись остаётся у своего человека — выбранный профиль не нужен
        val profileId = state.value.profileId ?: if (dialog.editingId != null) "" else return
        viewModelScope.launch {
            val result = when (dialog.kind) {
                MeasureKind.WEIGHT -> weights.save(dialog.editingId, profileId, values[0], at)
                MeasureKind.PRESSURE -> pressures.save(dialog.editingId, profileId, values[0], values[1], values[2], at)
                MeasureKind.WATER -> water.save(dialog.editingId, profileId, values[0], at)
                MeasureKind.BODY -> bodies.save(dialog.editingId, profileId, values, at)
            }
            when (result) {
                is MeasureSave.Saved -> {
                    _dialog.value = null
                    // Новая запись — вернуться к еде (15.4); правка — остаться в списке (или назад в «Историю»)
                    if (dialog.editingId == null) _recorded.value = result.message else closeIfFromHistory()
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
                MeasureKind.BODY -> bodies.delete(id)
            }
            _dialog.value = null
            closeIfFromHistory()
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
        viewModelScope.launch {
            val r = sleeps.get(id) ?: return@launch closeIfFromHistory()
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
    }

    fun dismissSleep() {
        _sleepDialog.value = null
        closeIfFromHistory()
    }

    fun saveSleep(wakeDate: LocalDate, wakeTime: LocalTime, asleepTime: LocalTime, quality: SleepQuality?) {
        val dialog = _sleepDialog.value ?: return
        val profileId = state.value.profileId ?: if (dialog.editingId != null) "" else return
        val (asleepAt, wokeAt) = SleepTime.moments(wakeDate, wakeTime, asleepTime)
        viewModelScope.launch {
            when (val r = sleeps.save(dialog.editingId, profileId, asleepAt, wokeAt, quality)) {
                is MeasureSave.Saved -> {
                    _sleepDialog.value = null
                    if (dialog.editingId == null) _recorded.value = r.message else closeIfFromHistory()
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
            closeIfFromHistory()
        }
    }

    fun onRecordedHandled() {
        _recorded.value = null
    }

    // В конце класса: все поля выше уже созданы, открыть можно любую запись, в том числе сон
    init {
        // «weight:<id>», «pressure:<id>», «water:<id>», «sleep:<id>»
        editArg?.split(':', limit = 2)?.takeIf { it.size == 2 }?.let { (kind, id) ->
            when (kind) {
                KIND_WEIGHT -> edit(MeasureKind.WEIGHT, id)
                KIND_PRESSURE -> edit(MeasureKind.PRESSURE, id)
                KIND_WATER -> edit(MeasureKind.WATER, id)
                KIND_SLEEP -> editSleep(id)
                KIND_BODY -> edit(MeasureKind.BODY, id)
            }
        }
    }

    companion object {
        const val ARG_PROFILE = "profile"
        const val ARG_EDIT = "edit"
        const val KIND_WEIGHT = "weight"
        const val KIND_PRESSURE = "pressure"
        const val KIND_WATER = "water"
        const val KIND_SLEEP = "sleep"
        const val KIND_BODY = "body"

        /** Аргумент [ARG_EDIT]: какую запись открыть. */
        fun editArg(kind: String, id: String) = "$kind:$id"
    }
}
