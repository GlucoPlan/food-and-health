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

/** 3 → 4: приёмы пищи. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `meal` (
                `id` TEXT NOT NULL,
                `profile_id` TEXT NOT NULL,
                `eaten_at` INTEGER NOT NULL,
                `notes` TEXT,
                `glucose` REAL,
                `insulin_dose` REAL,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_meal_profile_id_eaten_at` ON `meal` (`profile_id`, `eaten_at`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `meal_item` (
                `id` TEXT NOT NULL,
                `meal_id` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `product_id` TEXT,
                `dish_version_id` TEXT,
                `weight_g` REAL NOT NULL,
                `pieces` REAL,
                `snapshot_kcal` REAL NOT NULL,
                `snapshot_protein` REAL NOT NULL,
                `snapshot_fat` REAL NOT NULL,
                `snapshot_carbs` REAL NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_item_meal_id` ON `meal_item` (`meal_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_item_dish_version_id` ON `meal_item` (`dish_version_id`)")
    }
}

/** Все миграции по порядку; каждая покрыта MigrationTest. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
