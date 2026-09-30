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

/**
 * 4 → 5: синхронизация. Всё, что накоплено на телефоне до подключения к серверу,
 * ставится в очередь отправки. Триггеры создаёт SyncTriggers при открытии базы.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_outbox` (`tbl` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`version` INTEGER NOT NULL, PRIMARY KEY(`tbl`, `id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, `applying` INTEGER NOT NULL, " +
                "`cursor` INTEGER NOT NULL, `initialized` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_extra` (`tbl` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                "`json` TEXT NOT NULL, PRIMARY KEY(`tbl`, `id`))"
        )
        db.execSQL("INSERT OR IGNORE INTO sync_state (id, applying, cursor, initialized) VALUES (1, 0, 0, 0)")
        // Список таблиц — как был в версии 5. Не SyncTables.ALL: он растёт с новыми версиями,
        // а таблиц из будущих версий в этот момент ещё нет
        listOf("profile", "product", "pan", "dish", "dish_version", "dish_ingredient", "meal", "meal_item").forEach { table ->
            db.execSQL("INSERT OR REPLACE INTO sync_outbox (tbl, id, version) SELECT '$table', id, 1 FROM `$table`")
        }
    }
}

/**
 * 5 → 6: этап 2 — пол, дата рождения и вода в профиле, история роста.
 * Курсор синхронизации сбрасывается: телефон заново получит всё, в том числе записи роста,
 * пришедшие на сервер до обновления этого телефона (SyncTables). Неотправленные правки не затираются.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `sex` TEXT")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `birth_date` TEXT")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `water_enabled` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `water_ml_per_kg` REAL NOT NULL DEFAULT 30")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `height` (
                `id` TEXT NOT NULL,
                `profile_id` TEXT NOT NULL,
                `measured_at` INTEGER NOT NULL,
                `height_cm` REAL NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_height_profile_id` ON `height` (`profile_id`)")
        db.execSQL("UPDATE sync_state SET cursor = 0 WHERE id = 1")
    }
}

/** 6 → 7: вес. Курсор сбрасывается по той же причине, что в 5 → 6. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `weight` (
                `id` TEXT NOT NULL,
                `profile_id` TEXT NOT NULL,
                `measured_at` INTEGER NOT NULL,
                `weight_kg` REAL NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_weight_profile_id_measured_at` ON `weight` (`profile_id`, `measured_at`)"
        )
        db.execSQL("UPDATE sync_state SET cursor = 0 WHERE id = 1")
    }
}

/** 7 → 8: давление и пульс. Курсор сбрасывается по той же причине, что в 5 → 6. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `blood_pressure` (
                `id` TEXT NOT NULL,
                `profile_id` TEXT NOT NULL,
                `measured_at` INTEGER NOT NULL,
                `systolic` INTEGER NOT NULL,
                `diastolic` INTEGER NOT NULL,
                `pulse` INTEGER,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_blood_pressure_profile_id_measured_at` " +
                "ON `blood_pressure` (`profile_id`, `measured_at`)"
        )
        db.execSQL("UPDATE sync_state SET cursor = 0 WHERE id = 1")
    }
}

/** 8 → 9: сон. Курсор сбрасывается по той же причине, что в 5 → 6. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sleep` (
                `id` TEXT NOT NULL,
                `profile_id` TEXT NOT NULL,
                `asleep_at` INTEGER NOT NULL,
                `woke_at` INTEGER NOT NULL,
                `quality` TEXT,
                `source` TEXT NOT NULL,
                `external_id` TEXT,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_profile_id_woke_at` ON `sleep` (`profile_id`, `woke_at`)")
        db.execSQL("UPDATE sync_state SET cursor = 0 WHERE id = 1")
    }
}

/** 9 → 10: вода. Курсор сбрасывается по той же причине, что в 5 → 6. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `water` (
                `id` TEXT NOT NULL,
                `profile_id` TEXT NOT NULL,
                `drunk_at` INTEGER NOT NULL,
                `ml` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_profile_id_drunk_at` ON `water` (`profile_id`, `drunk_at`)")
        db.execSQL("UPDATE sync_state SET cursor = 0 WHERE id = 1")
    }
}

/** 10 → 11: обхваты тела. Курсор сбрасывается по той же причине, что в 5 → 6. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `body_measure` (
                `id` TEXT NOT NULL,
                `profile_id` TEXT NOT NULL,
                `measured_at` INTEGER NOT NULL,
                `neck` REAL,
                `chest` REAL,
                `waist` REAL,
                `belly` REAL,
                `hips` REAL,
                `thigh` REAL,
                `calf` REAL,
                `arm` REAL,
                `wrist` REAL,
                `updated_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL,
                `device_id` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_body_measure_profile_id_measured_at` ON `body_measure` (`profile_id`, `measured_at`)"
        )
        db.execSQL("UPDATE sync_state SET cursor = 0 WHERE id = 1")
    }
}

/**
 * 11 → 12: этап 3 — активность, цель по весу, ручные нормы и диапазон сахара в профиле (ТЗ 17.3).
 * Курсор сбрасывается: эти поля могли прийти с телефона, обновлённого раньше, и лежат в sync_extra.
 * Без сброса они остались бы там, а колонки — пустыми, и первая же правка профиля на этом телефоне
 * отправила бы пустые значения поверх настоящих. Повторная загрузка раскладывает их по колонкам.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `activity` TEXT")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `target_weight_kg` REAL")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `weight_pace_kg` REAL NOT NULL DEFAULT 0.5")
        listOf("norm_kcal", "norm_protein", "norm_fat", "norm_carbs", "glucose_low", "glucose_high").forEach {
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `$it` REAL")
        }
        db.execSQL("UPDATE sync_state SET cursor = 0 WHERE id = 1")
    }
}

/** Все миграции по порядку; каждая покрыта MigrationTest. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
)
