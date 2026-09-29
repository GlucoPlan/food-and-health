package com.glucoplan.foodhealth.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.profile.ProfileForm
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import com.glucoplan.foodhealth.data.profile.ProfileValidation
import com.glucoplan.foodhealth.data.profile.ProfileValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileEditState(
    val isNew: Boolean,
    val loaded: Boolean = false,
    val form: ProfileForm = ProfileForm(name = ""),
    val nameError: String? = null,
    val carbsError: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val id: String? = savedStateHandle.get<String>(ARG_ID)

    private val _state = MutableStateFlow(ProfileEditState(isNew = id == null, loaded = id == null))
    val state: StateFlow<ProfileEditState> = _state.asStateFlow()

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
                    ),
                )
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(form = it.form.copy(name = value), nameError = null) }
    fun onSd1Change(value: Boolean) = _state.update { it.copy(form = it.form.copy(sd1Enabled = value)) }
    fun onShowXeChange(value: Boolean) = _state.update { it.copy(form = it.form.copy(showXe = value), carbsError = null) }
    fun onCarbsChange(value: String) = _state.update { it.copy(form = it.form.copy(carbsPerXe = value), carbsError = null) }

    fun save() {
        viewModelScope.launch {
            when (val result = profiles.save(id, _state.value.form)) {
                is ProfileValidation.Valid -> _state.update { it.copy(saved = true) }
                is ProfileValidation.Invalid -> _state.update {
                    it.copy(nameError = result.nameError, carbsError = result.carbsError)
                }
            }
        }
    }

    companion object {
        const val ARG_ID = "id"
    }
}
