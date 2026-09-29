package com.glucoplan.foodhealth.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Upsert
    suspend fun upsertMeal(meal: MealEntity)

    @Upsert
    suspend fun upsertItems(items: List<MealItemEntity>)

    @Query("SELECT * FROM meal WHERE id = :id")
    suspend fun getMeal(id: String): MealEntity?

    @Query("SELECT * FROM meal_item WHERE meal_id = :mealId AND deleted = 0")
    suspend fun itemsOf(mealId: String): List<MealItemEntity>

    /** Вместе с удалёнными позициями — правка ничего не стирает физически (ТЗ 5.1). */
    @Query("SELECT * FROM meal_item WHERE meal_id = :mealId")
    suspend fun allItemsOf(mealId: String): List<MealItemEntity>

    /** История профиля (ТЗ 4.2), новые сверху. */
    @Query("SELECT * FROM meal WHERE profile_id = :profileId AND deleted = 0 ORDER BY eaten_at DESC")
    fun observeMealsOf(profileId: String): Flow<List<MealEntity>>

    @Query(
        "SELECT i.* FROM meal_item i JOIN meal m ON m.id = i.meal_id " +
            "WHERE m.profile_id = :profileId AND m.deleted = 0 AND i.deleted = 0"
    )
    fun observeItemsOf(profileId: String): Flow<List<MealItemEntity>>

    /** ТЗ 5.4: варка, записанная хотя бы в один приём, не редактируется. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM meal_item i JOIN meal m ON m.id = i.meal_id " +
            "WHERE i.dish_version_id = :versionId AND i.deleted = 0 AND m.deleted = 0)"
    )
    suspend fun isVersionUsed(versionId: String): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM meal_item i JOIN meal m ON m.id = i.meal_id " +
            "WHERE i.dish_version_id = :versionId AND i.deleted = 0 AND m.deleted = 0)"
    )
    fun observeVersionUsed(versionId: String): Flow<Boolean>

    /** Что профиль ел, новые сверху. */
    @Query(
        "SELECT i.type AS type, i.product_id AS productId, i.dish_version_id AS dishVersionId, m.eaten_at AS eatenAt " +
            "FROM meal_item i JOIN meal m ON m.id = i.meal_id " +
            "WHERE m.profile_id = :profileId AND m.deleted = 0 AND i.deleted = 0 " +
            "ORDER BY m.eaten_at DESC LIMIT :limit"
    )
    suspend fun usages(profileId: String, limit: Int): List<ItemUsage>
}
