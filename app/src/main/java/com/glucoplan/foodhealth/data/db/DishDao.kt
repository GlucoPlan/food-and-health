package com.glucoplan.foodhealth.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DishDao {

    @Query("SELECT * FROM dish WHERE deleted = 0")
    fun observeActiveDishes(): Flow<List<DishEntity>>

    /** Вместе с удалёнными: прошлые приёмы показывают удалённые блюда (ТЗ 5.5). */
    @Query("SELECT * FROM dish")
    fun observeAllDishes(): Flow<List<DishEntity>>

    @Query("SELECT * FROM dish WHERE id = :id")
    suspend fun getDish(id: String): DishEntity?

    @Query("SELECT * FROM dish WHERE id = :id")
    fun observeDish(id: String): Flow<DishEntity?>

    @Upsert
    suspend fun upsertDish(dish: DishEntity)

    @Query("SELECT * FROM dish_version WHERE deleted = 0")
    fun observeVersions(): Flow<List<DishVersionEntity>>

    @Query("SELECT * FROM dish_version WHERE id = :id")
    suspend fun getVersion(id: String): DishVersionEntity?

    @Query("SELECT * FROM dish_version WHERE dish_id = :dishId AND deleted = 0 ORDER BY created_at DESC")
    fun observeVersionsOf(dishId: String): Flow<List<DishVersionEntity>>

    @Upsert
    suspend fun upsertVersion(version: DishVersionEntity)

    @Query("SELECT * FROM dish_ingredient WHERE deleted = 0")
    fun observeIngredients(): Flow<List<DishIngredientEntity>>

    /** Вместе с удалёнными строками — для проверки, что правка ничего не стёрла физически. */
    @Query("SELECT * FROM dish_ingredient WHERE dish_version_id = :versionId")
    suspend fun allIngredientsOf(versionId: String): List<DishIngredientEntity>

    @Query("SELECT * FROM dish_ingredient WHERE dish_version_id = :versionId AND deleted = 0")
    suspend fun ingredientsOf(versionId: String): List<DishIngredientEntity>

    @Upsert
    suspend fun upsertIngredients(ingredients: List<DishIngredientEntity>)
}
