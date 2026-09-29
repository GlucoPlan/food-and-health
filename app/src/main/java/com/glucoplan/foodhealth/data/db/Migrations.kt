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

/** Все миграции по порядку; каждая покрыта MigrationTest. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
