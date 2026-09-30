package com.glucoplan.foodhealth.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.measure.WeightRepository
import com.glucoplan.foodhealth.data.norms.Activity
import com.glucoplan.foodhealth.data.norms.DailyNorms
import com.glucoplan.foodhealth.data.norms.NormCalculator
import com.glucoplan.foodhealth.data.norms.NormInput
import com.glucoplan.foodhealth.data.norms.NormSet
import com.glucoplan.foodhealth.data.profile.HeightRecord
import com.glucoplan.foodhealth.data.profile.HeightRepository
import com.glucoplan.foodhealth.data.profile.ProfileForm
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import com.glucoplan.foodhealth.data.profile.ProfileValidation
import com.glucoplan.foodhealth.data.profile.ProfileValidator
import com.glucoplan.foodhealth.data.profile.Sex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ProfileEditState(
    val isNew: Boolean,
    val loaded: Boolean = false,
    val form: ProfileForm = ProfileForm(name = ""),
    val nameError: String? = null,
    val carbsError: String? = null,
    val sexError: String? = null,
    val birthDateError: String? = null,
    val waterError: String? = null,
    val targetError: String? = null,
    val paceError: String? = null,
    val kcalError: String? = null,
    val proteinError: String? = null,
    val fatError: String? = null,
    val normCarbsError: String? = null,
    val glucoseError: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val heights: HeightRepository,
    weights: WeightRepository,
) : ViewModel() {

    private val id: String? = savedStateHandle.get<String>(ARG_ID)

    private val _state = MutableStateFlow(ProfileEditState(isNew = id == null, loaded = id == null))
    val state: StateFlow<ProfileEditState> = _state.asStateFlow()

    /** История роста (ТЗ 15.3); у нового профиля пусто — рост записывается после сохранения. */
    val heightHistory: StateFlow<List<HeightRecord>> =
        (id?.let(heights::observeHistory) ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Нормы по тому, что сейчас в форме, последним росту и весу (ТЗ 17.3): подсказки «расчёт» у полей норм.
     * Недописанные числа считаются пустыми.
     */
    val norms: StateFlow<DailyNorms?> =
        combine(
            _state,
            heightHistory,
            id?.let { weights.observeRecent(it, 1) } ?: flowOf(emptyList()),
        ) { state, heightList, weightList ->
            val form = state.form
            NormCalculator.calculate(
                NormInput(
                    sex = form.sex,
                    birthDate = form.birthDate,
                    heightCm = heightList.firstOrNull()?.heightCm,
                    weightKg = weightList.firstOrNull()?.kg,
                    activity = form.activity,
                    targetWeightKg = NumberText.parse(form.targetWeightKg),
                    paceKgPerWeek = NumberText.parse(form.weightPaceKg) ?: NormCalculator.DEFAULT_PACE,
                    manual = NormSet(
                        NumberText.parse(form.normKcal),
                        NumberText.parse(form.normProtein),
                        NumberText.parse(form.normFat),
                        NumberText.parse(form.normCarbs),
                    ),
                ),
                LocalDate.now(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (id != null) viewModelScope.launch {
            val p = profiles.get(id)
            _state.update {
                it.copy(
                    loaded = true,
                    form = if (p == null) it.form else ProfileForm(
                        name = p.name,
                        sd1Enabled = p.sd1Enabled,
                        showXe = p.showXe,
                        carbsPerXe = ProfileValidator.formatCarbs(p.carbsPerXe),
                        sex = p.sex,
                        birthDate = p.birthDate,
                        waterEnabled = p.waterEnabled,
                        waterMlPerKg = ProfileValidator.formatCarbs(p.waterMlPerKg),
                        activity = p.activity,
                        targetWeightKg = p.targetWeightKg?.let(ProfileValidator::formatCarbs).orEmpty(),
                        weightPaceKg = ProfileValidator.formatCarbs(p.weightPaceKg),
                        normKcal = p.manualNorms.kcal?.let(ProfileValidator::formatCarbs).orEmpty(),
                        normProtein = p.manualNorms.protein?.let(ProfileValidator::formatCarbs).orEmpty(),
                        normFat = p.manualNorms.fat?.let(ProfileValidator::formatCarbs).orEmpty(),
                        normCarbs = p.manualNorms.carbs?.let(ProfileValidator::formatCarbs).orEmpty(),
                        glucoseLow = p.glucoseLow?.let(ProfileValidator::formatCarbs).orEmpty(),
                        glucoseHigh = p.glucoseHigh?.let(ProfileValidator::formatCarbs).orEmpty(),
                    ),
                )
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(form = it.form.copy(name = value), nameError = null) }
    /** При включении «Дневника СД1» с пустым диапазоном сахара подставляется стандартный (ТЗ 17.3). */
    fun onSd1Change(value: Boolean) = _state.update {
        val form = it.form
        val prefill = value && form.glucoseLow.isBlank() && form.glucoseHigh.isBlank()
        it.copy(
            form = if (prefill) form.copy(
                sd1Enabled = true,
                glucoseLow = ProfileValidator.formatCarbs(ProfileValidator.DEFAULT_GLUCOSE_LOW),
                glucoseHigh = ProfileValidator.formatCarbs(ProfileValidator.DEFAULT_GLUCOSE_HIGH),
            ) else form.copy(sd1Enabled = value),
            glucoseError = null,
        )
    }
    fun onShowXeChange(value: Boolean) = _state.update { it.copy(form = it.form.copy(showXe = value), carbsError = null) }
    fun onCarbsChange(value: String) = _state.update { it.copy(form = it.form.copy(carbsPerXe = value), carbsError = null) }
    fun onSexChange(value: Sex) = _state.update { it.copy(form = it.form.copy(sex = value), sexError = null) }
    fun onBirthDateChange(value: LocalDate) =
        _state.update { it.copy(form = it.form.copy(birthDate = value), birthDateError = null) }
    fun onWaterEnabledChange(value: Boolean) =
        _state.update { it.copy(form = it.form.copy(waterEnabled = value), waterError = null) }
    fun onWaterNormChange(value: String) =
        _state.update { it.copy(form = it.form.copy(waterMlPerKg = value), waterError = null) }

    fun onActivityChange(value: Activity) = _state.update { it.copy(form = it.form.copy(activity = value)) }
    fun onTargetChange(value: String) =
        _state.update { it.copy(form = it.form.copy(targetWeightKg = value), targetError = null, paceError = null) }
    fun onPaceChange(value: String) = _state.update { it.copy(form = it.form.copy(weightPaceKg = value), paceError = null) }
    fun onNormKcalChange(value: String) = _state.update { it.copy(form = it.form.copy(normKcal = value), kcalError = null) }
    fun onNormProteinChange(value: String) =
        _state.update { it.copy(form = it.form.copy(normProtein = value), proteinError = null) }
    fun onNormFatChange(value: String) = _state.update { it.copy(form = it.form.copy(normFat = value), fatError = null) }
    fun onNormCarbsChange(value: String) =
        _state.update { it.copy(form = it.form.copy(normCarbs = value), normCarbsError = null) }
    fun onGlucoseLowChange(value: String) =
        _state.update { it.copy(form = it.form.copy(glucoseLow = value), glucoseError = null) }
    fun onGlucoseHighChange(value: String) =
        _state.update { it.copy(form = it.form.copy(glucoseHigh = value), glucoseError = null) }

    fun save() {
        viewModelScope.launch {
            when (val result = profiles.save(id, _state.value.form)) {
                is ProfileValidation.Valid -> _state.update { it.copy(saved = true) }
                is ProfileValidation.Invalid -> _state.update {
                    it.copy(
                        nameError = result.nameError,
                        carbsError = result.carbsError,
                        sexError = result.sexError,
                        birthDateError = result.birthDateError,
                        waterError = result.waterError,
                        targetError = result.targetError,
                        paceError = result.paceError,
                        kcalError = result.kcalError,
                        proteinError = result.proteinError,
                        fatError = result.fatError,
                        normCarbsError = result.normCarbsError,
                        glucoseError = result.glucoseError,
                    )
                }
            }
        }
    }

    /** Записать рост; колбэк получает текст ошибки или null. */
    fun addHeight(text: String, date: LocalDate, onResult: (String?) -> Unit) {
        val profileId = id ?: return
        viewModelScope.launch { onResult(heights.add(profileId, text, date)) }
    }

    fun deleteHeight(recordId: String) {
        viewModelScope.launch { heights.delete(recordId) }
    }

    companion object {
        const val ARG_ID = "id"
    }
}
