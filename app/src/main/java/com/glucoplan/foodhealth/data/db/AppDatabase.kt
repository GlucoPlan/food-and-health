package com.glucoplan.foodhealth.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Локальная база. Схема меняется только миграцией с тестом (ТЗ 8.4),
 * JSON-схемы каждой версии лежат в app/schemas.
 */
@Database(
    entities = [ProfileEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    companion object {
        const val VERSION = 1
        const val NAME = "food_health.db"
    }
}
