package com.glucoplan.foodhealth.data.dish

import androidx.room.withTransaction
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.db.DishDao
import com.glucoplan.foodhealth.data.db.DishEntity
import com.glucoplan.foodhealth.data.db.DishIngredientEntity
import com.glucoplan.foodhealth.data.db.DishVersionEntity
import com.glucoplan.foodhealth.data.db.MealDao
import com.glucoplan.foodhealth.data.db.PanDao
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.product.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Как сохранять блюдо из редактора. */
enum class DishSaveMode {
    /** Новое блюдо, его первая варка. */
    NEW,

    /** Правка текущей варки на месте (ТЗ 5.4: пока она не использована в приёмах пищи). */
    EDIT,

    /** «Сварил заново»: новая варка становится текущей, старые не меняются. */
    RECOOK,
}

@Singleton
class DishRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: DishDao,
    private val panDao: PanDao,
    private val mealDao: MealDao,
    private val products: ProductRepository,
    private val devicePrefs: DevicePrefs,
) {
    /** Список блюд без удалённых: КБЖУ на 100 г текущей варки считается от текущих продуктов. */
    fun observeSummaries(): Flow<List<DishSummary>> = combine(
        dao.observeActiveDishes(),
        dao.observeVersions(),
        dao.observeIngredients(),
        products.observeAllIncludingDeleted(),
    ) { dishes, versions, ingredients, productList ->
        val versionById = versions.associateBy { it.id }
        val ingredientsByVersion = ingredients.groupBy { it.dishVersionId }
        val productById = productList.associateBy { it.id }
        dishes.mapNotNull { dish ->
            val entity = versionById[dish.currentVersionId] ?: return@mapNotNull null
            val version = entity.toVersion(ingredientsByVersion[entity.id].orEmpty())
            DishSummary(
                id = dish.id,
                name = dish.name,
                currentVersionId = entity.id,
                lastCookedAt = version.createdAt,
                per100 = DishCalculator.per100(version, productById),
                byRawIngredients = version.byRawIngredients,
            )
        }
    }

    /** Все варки всех блюд (и удалённых) по id варки — для приёма пищи и истории. */
    fun observeVersionCatalog(): Flow<Map<String, VersionInfo>> = combine(
        dao.observeAllDishes(),
        dao.observeVersions(),
        dao.observeIngredients(),
        products.observeAllIncludingDeleted(),
    ) { dishes, versions, ingredients, productList ->
        val dishById = dishes.associateBy { it.id }
        val ingredientsByVersion = ingredients.groupBy { it.dishVersionId }
        val productById = productList.associateBy { it.id }
        versions.mapNotNull { entity ->
            val dish = dishById[entity.dishId] ?: return@mapNotNull null
            val version = entity.toVersion(ingredientsByVersion[entity.id].orEmpty())
            VersionInfo(
                versionId = version.id,
                dishId = dish.id,
                dishName = dish.name,
                dishDeleted = dish.deleted,
                createdAt = version.createdAt,
                per100 = DishCalculator.per100(version, productById),
                isCurrent = dish.currentVersionId == version.id,
            )
        }.associateBy { it.versionId }
    }

    /** Текущая варка уже записана в приёме пищи — её состав и вес не редактируются (ТЗ 5.4). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCurrentVersionUsed(dishId: String): Flow<Boolean> =
        dao.observeDish(dishId).flatMapLatest { dish ->
            dish?.let { mealDao.observeVersionUsed(it.currentVersionId) } ?: flowOf(false)
        }

    /** Варки блюда, новые сверху (ТЗ 5.4). */
    fun observeVersionSummaries(dishId: String): Flow<List<VersionSummary>> = combine(
        dao.observeDish(dishId),
        dao.observeVersionsOf(dishId),
        dao.observeIngredients(),
        products.observeAllIncludingDeleted(),
        panDao.observeAll(),
    ) { dish, versions, ingredients, productList, pans ->
        val ingredientsByVersion = ingredients.groupBy { it.dishVersionId }
        val productById = productList.associateBy { it.id }
        val panById = pans.associateBy { it.id }
        versions.map { entity ->
            val version = entity.toVersion(ingredientsByVersion[entity.id].orEmpty())
            val pan = version.panId?.let { panById[it] }
            VersionSummary(
                id = version.id,
                createdAt = version.createdAt,
                netWeightG = version.netWeightG,
                byRawIngredients = version.byRawIngredients,
                panName = pan?.name,
                panDeleted = pan?.deleted == true,
                per100 = DishCalculator.per100(version, productById),
                isCurrent = version.id == dish?.currentVersionId,
            )
        }
    }

    suspend fun getDish(id: String): Dish? =
        dao.getDish(id)?.let { Dish(it.id, it.name, it.currentVersionId, it.deleted) }

    suspend fun getVersion(id: String): DishVersion? =
        dao.getVersion(id)?.let { it.toVersion(dao.ingredientsOf(it.id)) }

    /**
     * Сохраняет форму редактора. [dishId] null только для [DishSaveMode.NEW].
     * [DishSaveMode.EDIT] для варки, уже записанной в приём пищи, меняет только название блюда.
     * Вес кастрюли берётся из базы (удалённая кастрюля тоже годится: её могли выбрать раньше).
     */
    suspend fun save(dishId: String?, mode: DishSaveMode, form: DishForm): DishValidation {
        val panWeight = form.panId?.let { panDao.getById(it)?.weightG }
        val result = DishValidator.validate(form.copy(panId = form.panId.takeIf { panWeight != null }), panWeight)
        if (result !is DishValidation.Valid) return result

        val now = System.currentTimeMillis()
        val device = devicePrefs.deviceId()

        fun newVersion(dishId: String) = DishVersionEntity(
            id = UUID.randomUUID().toString(),
            dishId = dishId,
            createdAt = now,
            panId = result.panId,
            grossWeightG = result.grossWeightG,
            netWeightG = result.netWeightG,
            updatedAt = now,
            deleted = false,
            deviceId = device,
        )

        fun newIngredients(versionId: String) = result.ingredients.map { (draft, weight) ->
            DishIngredientEntity(UUID.randomUUID().toString(), versionId, draft.productId, weight, now, false, device)
        }

        db.withTransaction {
            when {
                dishId == null || mode == DishSaveMode.NEW -> {
                    val id = UUID.randomUUID().toString()
                    val version = newVersion(id)
                    dao.upsertVersion(version)
                    dao.upsertIngredients(newIngredients(version.id))
                    dao.upsertDish(DishEntity(id, result.name, version.id, now, false, device))
                }

                mode == DishSaveMode.RECOOK -> {
                    val dish = dao.getDish(dishId) ?: return@withTransaction
                    val version = newVersion(dishId)
                    dao.upsertVersion(version)
                    dao.upsertIngredients(newIngredients(version.id))
                    dao.upsertDish(dish.copy(name = result.name, currentVersionId = version.id, updatedAt = now, deviceId = device))
                }

                else -> {
                    val dish = dao.getDish(dishId) ?: return@withTransaction
                    val version = dao.getVersion(dish.currentVersionId) ?: return@withTransaction
                    dao.upsertDish(dish.copy(name = result.name, updatedAt = now, deviceId = device))
                    // ТЗ 5.4: записанная в приём пищи варка не меняется, меняется только название блюда
                    if (mealDao.isVersionUsed(version.id)) return@withTransaction
                    dao.upsertVersion(
                        version.copy(
                            panId = result.panId,
                            grossWeightG = result.grossWeightG,
                            netWeightG = result.netWeightG,
                            updatedAt = now,
                            deviceId = device,
                        )
                    )
                    // Ингредиенты не удаляются физически (ТЗ 5.1): убранные помечаются deleted
                    val existing = dao.ingredientsOf(version.id).associateBy { it.id }
                    val keep = result.ingredients.map { it.first.key }.toSet()
                    val removed = existing.values.filter { it.id !in keep }
                        .map { it.copy(deleted = true, updatedAt = now, deviceId = device) }
                    val upserted = result.ingredients.map { (draft, weight) ->
                        existing[draft.key]?.copy(productId = draft.productId, weightG = weight, updatedAt = now, deviceId = device)
                            ?: DishIngredientEntity(draft.key, version.id, draft.productId, weight, now, false, device)
                    }
                    dao.upsertIngredients(removed + upserted)
                }
            }
        }
        return result
    }

    /** Мягкое удаление (ТЗ 5.5): блюдо пропадает из списков, варки остаются для прошлых приёмов. */
    suspend fun delete(dishId: String) {
        val dish = dao.getDish(dishId) ?: return
        dao.upsertDish(dish.copy(deleted = true, updatedAt = System.currentTimeMillis(), deviceId = devicePrefs.deviceId()))
    }

    private fun DishVersionEntity.toVersion(ingredients: List<DishIngredientEntity>) = DishVersion(
        id = id,
        dishId = dishId,
        createdAt = createdAt,
        panId = panId,
        grossWeightG = grossWeightG,
        netWeightG = netWeightG,
        ingredients = ingredients.filter { !it.deleted }.map { DishIngredient(it.id, it.productId, it.weightG) },
    )
}
