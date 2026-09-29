package com.glucoplan.foodhealth.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.db.DatabaseGuard
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.profile.Profile
import com.glucoplan.foodhealth.data.profile.ProfileForm
import com.glucoplan.foodhealth.data.profile.Sex
import java.time.LocalDate
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import com.glucoplan.foodhealth.data.profile.ProfileValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OwnerSelectViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val devicePrefs: DevicePrefs,
    private val guard: DatabaseGuard,
) : ViewModel() {

    val profileList: StateFlow<List<Profile>> =
        profiles.observeProfiles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** ТЗ 8.4: база была от более новой версии приложения и очищена. */
    val databaseWasReset: StateFlow<Boolean> = guard.wasReset

    fun select(profile: Profile) {
        viewModelScope.launch {
            devicePrefs.setOwner(profile.id)
            guard.dismissResetNotice()
        }
    }

    /**
     * Новый профиль с первого экрана: имя, пол и дата рождения (обязательны, ТЗ 15.3),
     * остальное — потом в настройках. Колбэк получает ошибки или null.
     */
    fun add(name: String, sex: Sex?, birthDate: LocalDate?, onResult: (ProfileValidation.Invalid?) -> Unit) {
        viewModelScope.launch {
            when (val result = profiles.save(null, ProfileForm(name, sex = sex, birthDate = birthDate))) {
                is ProfileValidation.Valid -> onResult(null)
                is ProfileValidation.Invalid -> onResult(result)
            }
        }
    }
}
