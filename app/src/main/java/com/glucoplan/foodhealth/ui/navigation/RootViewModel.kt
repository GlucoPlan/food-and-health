package com.glucoplan.foodhealth.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class RootState { Loading, NeedsOwner, Ready }

/** Решает, показать ли экран первого запуска: владелец не выбран или его профиля больше нет. */
@HiltViewModel
class RootViewModel @Inject constructor(
    devicePrefs: DevicePrefs,
    profiles: ProfileRepository,
) : ViewModel() {

    val state: StateFlow<RootState> =
        combine(devicePrefs.ownerId, profiles.observeProfiles()) { owner, list ->
            if (owner != null && list.any { it.id == owner }) RootState.Ready else RootState.NeedsOwner
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)
}
