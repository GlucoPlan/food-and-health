package com.glucoplan.foodhealth.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.meal.HistoryDay
import com.glucoplan.foodhealth.data.meal.HistoryDays
import com.glucoplan.foodhealth.data.meal.MealRepository
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.profile.Profile
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryUi(
    val loaded: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    val profileId: String? = null,
    /** Выбранный день (местная полночь) или null — все дни. */
    val day: Long? = null,
    val days: List<HistoryDay> = emptyList(),
    /** Выбранный профиль, если у него включён дневник СД1: в приёмах видны сахар, доза, углеводы. */
    val sd1Profile: Profile? = null,
)

/** Экран «История» (ТЗ 4.2): приёмы по дням, фильтр по профилю и дате. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val meals: MealRepository,
    profiles: ProfileRepository,
    devicePrefs: DevicePrefs,
) : ViewModel() {

    /** null — владелец телефона. */
    private val chosenProfile = MutableStateFlow<String?>(null)
    private val day = MutableStateFlow<Long?>(null)

    private val profileState = combine(profiles.observeProfiles(), devicePrefs.ownerId, chosenProfile) { list, owner, chosen ->
        list to (chosen?.takeIf { id -> list.any { it.id == id } } ?: owner)
    }

    val state: StateFlow<HistoryUi> = profileState.flatMapLatest { (list, profileId) ->
        val history = profileId?.let(meals::observeHistory) ?: flowOf(emptyList())
        combine(history, day) { mealList, d ->
            HistoryUi(
                loaded = true,
                profiles = list,
                profileId = profileId,
                day = d,
                days = HistoryDays.group(mealList, d),
                sd1Profile = list.firstOrNull { it.id == profileId }?.takeIf { it.sd1Enabled },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun onProfileSelected(id: String) {
        chosenProfile.value = id
    }

    /** null — снять фильтр по дате. */
    fun onDaySelected(millis: Long?) {
        day.value = millis?.let(HistoryDays::dayStart)
    }
}
