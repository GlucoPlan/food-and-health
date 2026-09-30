package com.glucoplan.foodhealth.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Локальная база. Схема меняется только миграцией с тестом (ТЗ 8.4),
 * JSON-схемы каждой версии лежат в app/schemas.
 */
@Database(
    entities = [
        ProfileEntity::class,
        ProductEntity::class,
        PanEntity::class,
        DishEntity::class,
        DishVersionEntity::class,
        DishIngredientEntity::class,
        MealEntity::class,
        MealItemEntity::class,
        SyncOutboxEntity::class,
        SyncStateEntity::class,
        SyncExtraEntity::class,
        HeightEntity::class,
        WeightEntity::class,
        BloodPressureEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    abstract fun productDao(): ProductDao

    abstract fun panDao(): PanDao

    abstract fun dishDao(): DishDao

    abstract fun mealDao(): MealDao

    abstract fun syncDao(): SyncDao

    abstract fun heightDao(): HeightDao

    abstract fun weightDao(): WeightDao

    abstract fun bloodPressureDao(): BloodPressureDao

    companion object {
        const val VERSION = 8
        const val NAME = "food_health.db"
    }
}
