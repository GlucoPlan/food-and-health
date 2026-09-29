package com.glucoplan.foodhealth.data.meal

import androidx.room.withTransaction
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.db.ItemUsage
import com.glucoplan.foodhealth.data.db.MealDao
import com.glucoplan.foodhealth.data.db.MealEntity
import com.glucoplan.foodhealth.data.db.MealItemEntity
import com.glucoplan.foodhealth.data.dish.DishRepository
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.product.ProductRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MealRecordResult {
    data class Recorded(val mealId: String, val total: Nutrition) : MealRecordResult
    data class Invalid(val errors: Map<String, String>) : MealRecordResult
}

@Singleton
class MealRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: MealDao,
    private val products: ProductRepository,
    private val dishes: DishRepository,
    private val devicePrefs: DevicePrefs,
) {
    /** Что профиль ел, новые сверху — для недавних и частых. */
    suspend fun usages(profileId: String): List<ItemUsage> = dao.usages(profileId, USAGE_LIMIT)

    /**
     * «Записать» (ТЗ 4.1): приём с позициями и КБЖУ на момент записи (snapshot_*, ТЗ 5.3).
     * Время null — «сейчас» ([now]).
     */
    suspend fun record(profileId: String, draft: MealDraft, now: Long = System.currentTimeMillis()): MealRecordResult {
        val productMap = products.observeAllIncludingDeleted().first().associateBy { it.id }
        val versions = dishes.observeVersionCatalog().first()
        val resolved = MealCalculator.resolve(draft.items, productMap, versions)
        val errors = MealValidator.validate(resolved, draft.eatenAt, now)
        if (errors.isNotEmpty()) return MealRecordResult.Invalid(errors)

        val device = devicePrefs.deviceId()
        val mealId = UUID.randomUUID().toString()
        val meal = MealEntity(
            id = mealId,
            profileId = profileId,
            eatenAt = draft.eatenAt ?: now,
            notes = null,
            glucose = null,
            insulinDose = null,
            updatedAt = now,
            deleted = false,
            deviceId = device,
        )
        val items = resolved.map { item ->
            val portion = item.portion!!
            MealItemEntity(
                id = UUID.randomUUID().toString(),
                mealId = mealId,
                type = item.type.code,
                productId = item.refId.takeIf { item.type == MealItemType.PRODUCT },
                dishVersionId = item.refId.takeIf { item.type == MealItemType.DISH },
                weightG = item.grams!!,
                pieces = item.pieces,
                snapshotKcal = portion.kcal,
                snapshotProtein = portion.protein,
                snapshotFat = portion.fat,
                snapshotCarbs = portion.carbs,
                updatedAt = now,
                deleted = false,
                deviceId = device,
            )
        }
        db.withTransaction {
            dao.upsertMeal(meal)
            dao.upsertItems(items)
        }
        return MealRecordResult.Recorded(mealId, MealCalculator.total(resolved))
    }

    private companion object {
        const val USAGE_LIMIT = 500
    }
}
