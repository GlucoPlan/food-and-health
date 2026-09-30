package com.glucoplan.foodhealth.data.history

import com.glucoplan.foodhealth.data.meal.MealRepository
import com.glucoplan.foodhealth.data.measure.BloodPressureRepository
import com.glucoplan.foodhealth.data.measure.SleepRepository
import com.glucoplan.foodhealth.data.measure.WaterRepository
import com.glucoplan.foodhealth.data.measure.WeightRepository
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Всё, что показывает «История» одного человека: приёмы пищи и замеры (ТЗ 4.2, 15.4). */
@Singleton
class HistoryRepository @Inject constructor(
    private val meals: MealRepository,
    private val weights: WeightRepository,
    private val pressures: BloodPressureRepository,
    private val sleeps: SleepRepository,
    private val water: WaterRepository,
    private val profiles: ProfileRepository,
) {
    fun observe(profileId: String): Flow<FeedData> = combine(
        combine(meals.observeHistory(profileId), weights.observeAll(profileId), pressures.observeAll(profileId)) { m, w, p ->
            Triple(m, w, p)
        },
        sleeps.observeAll(profileId),
        water.observeAll(profileId),
        profiles.observeProfiles(),
    ) { (m, w, p), s, wt, list ->
        val profile = list.firstOrNull { it.id == profileId }
        FeedData(
            meals = m,
            weights = w,
            pressures = p,
            sleeps = s,
            waters = wt,
            waterMlPerKg = profile?.takeIf { it.waterEnabled }?.waterMlPerKg,
        )
    }
}
