package com.glucoplan.foodhealth.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val saved: Boolean = false,
)

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val heights: HeightRepository,
) : ViewModel() {

    private val id: String? = savedStateHandle.get<String>(ARG_ID)

    private val _state = MutableStateFlow(ProfileEditState(isNew = id == null, loaded = id == null))
    val state: StateFlow<ProfileEditState> = _state.asStateFlow()

    /** История роста (ТЗ 15.3); у нового профиля пусто — рост записывается после сохранения. */
    val heightHistory: StateFlow<List<HeightRecord>> =
        (id?.let(heights::observeHistory) ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                    ),
                )
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(form = it.form.copy(name = value), nameError = null) }
    fun onSd1Change(value: Boolean) = _state.update { it.copy(form = it.form.copy(sd1Enabled = value)) }
    fun onShowXeChange(value: Boolean) = _state.update { it.copy(form = it.form.copy(showXe = value), carbsError = null) }
    fun onCarbsChange(value: String) = _state.update { it.copy(form = it.form.copy(carbsPerXe = value), carbsError = null) }
    fun onSexChange(value: Sex) = _state.update { it.copy(form = it.form.copy(sex = value), sexError = null) }
    fun onBirthDateChange(value: LocalDate) =
        _state.update { it.copy(form = it.form.copy(birthDate = value), birthDateError = null) }
    fun onWaterEnabledChange(value: Boolean) =
        _state.update { it.copy(form = it.form.copy(waterEnabled = value), waterError = null) }
    fun onWaterNormChange(value: String) =
        _state.update { it.copy(form = it.form.copy(waterMlPerKg = value), waterError = null) }

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
