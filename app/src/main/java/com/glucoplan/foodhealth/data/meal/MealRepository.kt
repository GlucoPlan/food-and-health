package com.glucoplan.foodhealth.data.meal

import androidx.room.withTransaction
import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.db.ItemUsage
import com.glucoplan.foodhealth.data.db.MealDao
import com.glucoplan.foodhealth.data.db.MealEntity
import com.glucoplan.foodhealth.data.db.MealItemEntity
import com.glucoplan.foodhealth.data.dish.DishRepository
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.product.ProductRepository
import com.glucoplan.foodhealth.data.profile.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val profiles: ProfileRepository,
    private val devicePrefs: DevicePrefs,
) {
    /** Дневник СД1 включён у профиля — сахар и дозу сохраняем (раздел 7). */
    private suspend fun sd1Enabled(profileId: String) = profiles.get(profileId)?.sd1Enabled == true

    /** Приёмы профиля с позициями, новые сверху (ТЗ 4.2). */
    fun observeHistory(profileId: String): Flow<List<HistoryMeal>> = combine(
        dao.observeMealsOf(profileId),
        dao.observeItemsOf(profileId),
        products.observeAllIncludingDeleted(),
        dishes.observeVersionCatalog(),
    ) { meals, items, productList, versions ->
        HistoryDays.meals(meals, items, productList.associateBy { it.id }, versions)
    }

    /** Записанный приём как черновик для правки: ключи позиций — их id в базе. */
    suspend fun loadForEdit(mealId: String): MealDraft? {
        val meal = dao.getMeal(mealId)?.takeIf { !it.deleted } ?: return null
        return MealDraft(
            profileId = meal.profileId,
            eatenAt = meal.eatenAt,
            glucose = meal.glucose?.let { NumberText.format(it, 1) }.orEmpty(),
            dose = meal.insulinDose?.let { NumberText.format(it, 2) }.orEmpty(),
            items = dao.itemsOf(mealId).map { item ->
                DraftItem(
                    key = item.id,
                    type = MealItemType.fromCode(item.type) ?: MealItemType.PRODUCT,
                    refId = item.productId ?: item.dishVersionId.orEmpty(),
                    weight = NumberText.format(item.weightG, 1),
                    pieces = item.pieces?.let { NumberText.format(it, 2) },
                )
            },
        )
    }

    /**
     * Сохранить правку записанного приёма: профиль, время, состав, веса.
     * Снимки КБЖУ пересчитываются по текущим данным; убранные позиции помечаются удалёнными,
     * сахар и доза не трогаются.
     */
    suspend fun update(mealId: String, profileId: String, draft: MealDraft, now: Long = System.currentTimeMillis()): MealRecordResult {
        val meal = dao.getMeal(mealId) ?: return MealRecordResult.Invalid(mapOf(MealField.ITEMS to "Приём не найден"))
        val productMap = products.observeAllIncludingDeleted().first().associateBy { it.id }
        val versions = dishes.observeVersionCatalog().first()
        val resolved = MealCalculator.resolve(draft.items, productMap, versions)
        val eatenAt = draft.eatenAt ?: meal.eatenAt
        val sd1 = sd1Enabled(profileId)
        val errors = MealValidator.validate(resolved, eatenAt, now) +
            (if (sd1) Sd1.validate(draft.glucose, draft.dose) else emptyMap())
        if (errors.isNotEmpty()) return MealRecordResult.Invalid(errors)

        val device = devicePrefs.deviceId()
        val existing = dao.itemsOf(mealId).associateBy { it.id }
        val keep = resolved.map { it.key }.toSet()
        val removed = existing.values.filter { it.id !in keep }
            .map { it.copy(deleted = true, updatedAt = now, deviceId = device) }
        val upserted = resolved.map { item -> item.toEntity(existing[item.key]?.id ?: item.key, mealId, now, device) }

        db.withTransaction {
            dao.upsertMeal(
                meal.copy(
                    profileId = profileId,
                    eatenAt = eatenAt,
                    // Без дневника поля скрыты — прежние значения не стираем
                    glucose = if (sd1) Sd1.value(draft.glucose) else meal.glucose,
                    insulinDose = if (sd1) Sd1.value(draft.dose) else meal.insulinDose,
                    updatedAt = now,
                    deviceId = device,
                )
            )
            dao.upsertItems(removed + upserted)
        }
        return MealRecordResult.Recorded(mealId, MealCalculator.total(resolved))
    }

    /** Мягкое удаление приёма (ТЗ 4.2, 5.1). */
    suspend fun delete(mealId: String) {
        val meal = dao.getMeal(mealId) ?: return
        dao.upsertMeal(meal.copy(deleted = true, updatedAt = System.currentTimeMillis(), deviceId = devicePrefs.deviceId()))
    }

    /** «Повторить» (ТЗ 4.2): состав приёма как новый черновик, удалённое пропускается. */
    suspend fun repeat(mealId: String): RepeatResult? {
        val meal = dao.getMeal(mealId) ?: return null
        return RepeatMeal.build(
            profileId = meal.profileId,
            items = dao.itemsOf(mealId),
            products = products.observeAllIncludingDeleted().first().associateBy { it.id },
            versions = dishes.observeVersionCatalog().first(),
        )
    }

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
        val sd1 = sd1Enabled(profileId)
        val errors = MealValidator.validate(resolved, draft.eatenAt, now) +
            (if (sd1) Sd1.validate(draft.glucose, draft.dose) else emptyMap())
        if (errors.isNotEmpty()) return MealRecordResult.Invalid(errors)

        val device = devicePrefs.deviceId()
        val mealId = UUID.randomUUID().toString()
        val meal = MealEntity(
            id = mealId,
            profileId = profileId,
            eatenAt = draft.eatenAt ?: now,
            notes = null,
            glucose = if (sd1) Sd1.value(draft.glucose) else null,
            insulinDose = if (sd1) Sd1.value(draft.dose) else null,
            updatedAt = now,
            deleted = false,
            deviceId = device,
        )
        val items = resolved.map { it.toEntity(UUID.randomUUID().toString(), mealId, now, device) }
        db.withTransaction {
            dao.upsertMeal(meal)
            dao.upsertItems(items)
        }
        return MealRecordResult.Recorded(mealId, MealCalculator.total(resolved))
    }

    /** Позиция для базы со снимком КБЖУ порции (ТЗ 5.3). Вызывается только после проверки. */
    private fun ResolvedItem.toEntity(id: String, mealId: String, now: Long, device: String): MealItemEntity {
        val portion = portion!!
        return MealItemEntity(
            id = id,
            mealId = mealId,
            type = type.code,
            productId = refId.takeIf { type == MealItemType.PRODUCT },
            dishVersionId = refId.takeIf { type == MealItemType.DISH },
            weightG = grams!!,
            pieces = pieces,
            snapshotKcal = portion.kcal,
            snapshotProtein = portion.protein,
            snapshotFat = portion.fat,
            snapshotCarbs = portion.carbs,
            updatedAt = now,
            deleted = false,
            deviceId = device,
        )
    }

    private companion object {
        const val USAGE_LIMIT = 500
    }
}
