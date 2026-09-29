package com.glucoplan.foodhealth.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 1 → 2: продукты. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `product` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `brand` TEXT,
                `barcode` TEXT,
                `kcal` REAL NOT NULL,
                `protein` REAL NOT NULL,
                `fat` REAL NOT NULL,
                `carbs` REAL NOT NULL,
                `fiber` REAL,
                `sugar` REAL,
                `salt` REAL,
                `gi` INTEGER,
                `piece_weight_g` REAL,
                `source` TEXT NOT NULL,
                `notes` TEXT,
                `micro` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_barcode` ON `product` (`barcode`)")
    }
}

/** 2 → 3: кастрюли и блюда с версиями. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pan` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `weight_g` REAL NOT NULL,
                `photo` TEXT,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dish` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `current_version_id` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dish_version` (
                `id` TEXT NOT NULL,
                `dish_id` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `pan_id` TEXT,
                `gross_weight_g` REAL,
                `net_weight_g` REAL NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dish_version_dish_id` ON `dish_version` (`dish_id`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dish_ingredient` (
                `id` TEXT NOT NULL,
                `dish_version_id` TEXT NOT NULL,
                `product_id` TEXT NOT NULL,
                `weight_g` REAL NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_dish_ingredient_dish_version_id` ON `dish_ingredient` (`dish_version_id`)"
        )
    }
}

/** Все миграции по порядку; каждая покрыта MigrationTest. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
