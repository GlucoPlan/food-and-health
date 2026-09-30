package com.glucoplan.foodhealth.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.foodhealth.data.history.FeedData
import com.glucoplan.foodhealth.data.history.FeedDay
import com.glucoplan.foodhealth.data.history.HistoryFeed
import com.glucoplan.foodhealth.data.history.HistoryRepository
import com.glucoplan.foodhealth.data.meal.HistoryDays
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
    val days: List<FeedDay> = emptyList(),
    /** Дни с раскрытой строкой воды (местная полночь). */
    val waterExpanded: Set<Long> = emptySet(),
    /** Выбранный профиль, если у него включён дневник СД1: в приёмах видны сахар, доза, углеводы. */
    val sd1Profile: Profile? = null,
)

/** Экран «История» (ТЗ 4.2, 15.4): приёмы пищи и замеры по дням, фильтр по профилю и дате. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    profiles: ProfileRepository,
    devicePrefs: DevicePrefs,
) : ViewModel() {

    /** null — владелец телефона. */
    private val chosenProfile = MutableStateFlow<String?>(null)
    private val day = MutableStateFlow<Long?>(null)
    private val waterExpanded = MutableStateFlow<Set<Long>>(emptySet())

    private val profileState = combine(profiles.observeProfiles(), devicePrefs.ownerId, chosenProfile) { list, owner, chosen ->
        list to (chosen?.takeIf { id -> list.any { it.id == id } } ?: owner)
    }

    val state: StateFlow<HistoryUi> = profileState.flatMapLatest { (list, profileId) ->
        val feed = profileId?.let(history::observe) ?: flowOf(FeedData())
        combine(feed, day, waterExpanded) { data, d, expanded ->
            HistoryUi(
                loaded = true,
                profiles = list,
                profileId = profileId,
                day = d,
                days = HistoryFeed.build(data, d),
                waterExpanded = expanded,
                sd1Profile = list.firstOrNull { it.id == profileId }?.takeIf { it.sd1Enabled },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun onProfileSelected(id: String) {
        chosenProfile.value = id
    }

    /** Раскрыть или свернуть записи воды за день. */
    fun toggleWater(dayStart: Long) {
        waterExpanded.value = waterExpanded.value.let { if (dayStart in it) it - dayStart else it + dayStart }
    }

    /** null — снять фильтр по дате. */
    fun onDaySelected(millis: Long?) {
        day.value = millis?.let(HistoryDays::dayStart)
    }
}
