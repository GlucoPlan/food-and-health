package com.glucoplan.foodhealth.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.profile.Profile
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import com.glucoplan.foodhealth.update.UpdateRepository
import com.glucoplan.foodhealth.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val updates: UpdateRepository,
    private val devicePrefs: DevicePrefs,
    profiles: ProfileRepository,
) : ViewModel() {

    val update: StateFlow<UpdateState> = updates.state

    val profileList: StateFlow<List<Profile>> =
        profiles.observeProfiles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ownerId: StateFlow<String?> =
        devicePrefs.ownerId.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setOwner(profile: Profile) {
        viewModelScope.launch { devicePrefs.setOwner(profile.id) }
    }

    /** ТЗ 8.2: проверять обновление при каждом открытии настроек. */
    fun onOpened() = updates.checkNow()

    fun checkNow() = updates.checkNow()

    fun startUpdate() = updates.startUpdate()
}
