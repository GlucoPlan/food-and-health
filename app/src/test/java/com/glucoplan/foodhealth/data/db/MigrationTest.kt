package com.glucoplan.foodhealth.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Миграции базы (ТЗ 8.4, 10). Старая база создаётся по JSON-схеме из app/schemas,
 * затем открывается текущей версией Room с миграциями. Room при открытии сверяет
 * получившиеся таблицы со схемой сущностей и падает, если миграция написана неверно.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    private val name = "migration_test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    /** Создаёт базу версии [version] по экспортированной схеме Room. */
    private fun createDatabase(version: Int, fill: (SQLiteDatabase) -> Unit) {
        // Рабочая папка unit-тестов — модуль app
        val schema = File("schemas/${AppDatabase::class.java.name}/$version.json").readText()
        val database = Json.parseToJsonElement(schema).jsonObject["database"]!!.jsonObject
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            database["entities"]!!.jsonArray.forEach { e ->
                val entity = e.jsonObject
                val table = entity["tableName"]!!.jsonPrimitive.content
                db.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            database["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            fill(db)
            db.version = version
        }
    }

    private fun openCurrent(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { it.openHelper.writableDatabase } // запускает миграции и проверку схемы

    @Test
    fun `миграции идут подряд от 1 до текущей версии`() {
        var version = 1
        ALL_MIGRATIONS.forEach {
            assertThat(it.startVersion).isEqualTo(version)
            assertThat(it.endVersion).isEqualTo(version + 1)
            version = it.endVersion
        }
        assertThat(version).isEqualTo(AppDatabase.VERSION)
    }

    @Test
    fun `с версии 1 до текущей профили сохраняются, продукты добавляются`() = runTest {
        createDatabase(1) { db ->
            db.execSQL(
                "INSERT INTO profile (id, name, sd1_enabled, show_xe, carbs_per_xe, updated_at, deleted, device_id) " +
                    "VALUES ('p1', 'Дочь', 1, 1, 12.0, 100, 0, 'dev')"
            )
        }

        val db = openCurrent()
        try {
            val profile = db.profileDao().getById("p1")!!
            assertThat(profile.name).isEqualTo("Дочь")
            assertThat(profile.sd1Enabled).isTrue()
            assertThat(profile.carbsPerXe).isEqualTo(12.0)

            val product = ProductEntity(
                id = "x", name = "Молоко", brand = null, barcode = "46", kcal = 60.0, protein = 3.0, fat = 3.2,
                carbs = 4.7, fiber = null, sugar = null, salt = null, gi = 30, pieceWeightG = null,
                source = "label", notes = null, micro = mapOf("ca" to 120.0), updatedAt = 1L, deleted = false,
                deviceId = "dev",
            )
            db.productDao().upsert(product)
            assertThat(db.productDao().getById("x")).isEqualTo(product)
        } finally {
            db.close()
        }
    }

    @Test
    fun `с версии 2 до текущей профили и продукты сохраняются, блюда и кастрюли добавляются`() = runTest {
        createDatabase(2) { db ->
            db.execSQL(
                "INSERT INTO profile (id, name, sd1_enabled, show_xe, carbs_per_xe, updated_at, deleted, device_id) " +
                    "VALUES ('p1', 'Иван', 0, 0, 10.0, 100, 0, 'dev')"
            )
            db.execSQL(
                "INSERT INTO product (id, name, brand, barcode, kcal, protein, fat, carbs, fiber, sugar, salt, gi, " +
                    "piece_weight_g, source, notes, micro, updated_at, deleted, device_id) VALUES " +
                    "('x', 'Гречка', NULL, NULL, 313, 12.6, 3.3, 62, NULL, NULL, NULL, 50, NULL, 'label', NULL, " +
                    "'{\"fe\":6.7}', 1, 0, 'dev')"
            )
        }

        val db = openCurrent()
        try {
            assertThat(db.profileDao().getById("p1")?.name).isEqualTo("Иван")
            val product = db.productDao().getById("x")!!
            assertThat(product.gi).isEqualTo(50)
            assertThat(product.micro).containsExactly("fe", 6.7)

            db.panDao().upsert(PanEntity("pan", "Большая", 800.0, null, 1L, false, "dev"))
            db.dishDao().upsertVersion(DishVersionEntity("v", "d", 1L, "pan", 1300.0, 500.0, 1L, false, "dev"))
            db.dishDao().upsertIngredients(listOf(DishIngredientEntity("i", "v", "x", 200.0, 1L, false, "dev")))
            db.dishDao().upsertDish(DishEntity("d", "Каша", "v", 1L, false, "dev"))
            assertThat(db.dishDao().getDish("d")?.currentVersionId).isEqualTo("v")
            assertThat(db.dishDao().ingredientsOf("v").single().weightG).isEqualTo(200.0)
        } finally {
            db.close()
        }
    }

    @Test
    fun `с версии 3 до текущей блюда сохраняются, приёмы пищи добавляются`() = runTest {
        createDatabase(3) { db ->
            db.execSQL(
                "INSERT INTO dish (id, name, current_version_id, updated_at, deleted, device_id) " +
                    "VALUES ('d', 'Суп', 'v', 1, 0, 'dev')"
            )
            db.execSQL(
                "INSERT INTO dish_version (id, dish_id, created_at, pan_id, gross_weight_g, net_weight_g, " +
                    "updated_at, deleted, device_id) VALUES ('v', 'd', 1, NULL, NULL, 500, 1, 0, 'dev')"
            )
        }

        val db = openCurrent()
        try {
            assertThat(db.dishDao().getDish("d")?.name).isEqualTo("Суп")
            assertThat(db.dishDao().getVersion("v")?.netWeightG).isEqualTo(500.0)

            db.mealDao().upsertMeal(MealEntity("m", "p", 10L, null, 6.5, 2.0, 1L, false, "dev"))
            db.mealDao().upsertItems(
                listOf(MealItemEntity("i", "m", "dish", null, "v", 300.0, null, 120.0, 6.0, 3.0, 18.0, 1L, false, "dev"))
            )
            assertThat(db.mealDao().getMeal("m")?.glucose).isEqualTo(6.5)
            assertThat(db.mealDao().itemsOf("m").single().snapshotKcal).isEqualTo(120.0)
            assertThat(db.mealDao().isVersionUsed("v")).isTrue()
        } finally {
            db.close()
        }
    }

    @Test
    fun `с версии 4 — всё накопленное встаёт в очередь отправки, триггеры работают`() = runTest {
        createDatabase(4) { db ->
            db.execSQL(
                "INSERT INTO profile (id, name, sd1_enabled, show_xe, carbs_per_xe, updated_at, deleted, device_id) " +
                    "VALUES ('p1', 'Иван', 0, 0, 10.0, 100, 0, 'dev')"
            )
            db.execSQL(
                "INSERT INTO meal (id, profile_id, eaten_at, notes, glucose, insulin_dose, updated_at, deleted, device_id) " +
                    "VALUES ('m1', 'p1', 1, NULL, NULL, NULL, 1, 0, 'dev')"
            )
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .addCallback(SyncTriggers.callback)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.writableDatabase
            assertThat(db.syncDao().outbox().map { it.tbl to it.id })
                .containsExactly("profile" to "p1", "meal" to "m1")
            val state = db.syncDao().state()!!
            assertThat(state.cursor).isEqualTo(0)
            assertThat(state.initialized).isFalse()

            db.profileDao().upsert(ProfileEntity("p2", "Дочь", true, false, 10.0, 2, false, "dev"))
            assertThat(db.syncDao().outbox().map { it.id }).contains("p2")
        } finally {
            db.close()
        }
    }

    @Test
    fun `с версии 5 — пол, дата рождения и вода в профиле, рост, курсор сброшен`() = runTest {
        createDatabase(5) { db ->
            db.execSQL(
                "INSERT INTO profile (id, name, sd1_enabled, show_xe, carbs_per_xe, updated_at, deleted, device_id) " +
                    "VALUES ('p1', 'Иван', 0, 1, 12.0, 100, 0, 'dev')"
            )
            db.execSQL("UPDATE sync_state SET cursor = 51, initialized = 1 WHERE id = 1")
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .addCallback(SyncTriggers.callback)
            .allowMainThreadQueries()
            .build()
        try {
            val profile = db.profileDao().getById("p1")!!
            assertThat(profile.name).isEqualTo("Иван")
            assertThat(profile.carbsPerXe).isEqualTo(12.0)
            assertThat(profile.sex).isNull()
            assertThat(profile.birthDate).isNull()
            assertThat(profile.waterEnabled).isFalse()
            assertThat(profile.waterMlPerKg).isEqualTo(30.0)

            val state = db.syncDao().state()!!
            assertThat(state.cursor).isEqualTo(0)
            assertThat(state.initialized).isTrue()

            db.heightDao().upsert(HeightEntity("h", "p1", 1L, 172.0, 1L, false, "dev"))
            assertThat(db.heightDao().getById("h")!!.heightCm).isEqualTo(172.0)
            // Рост синхронизируется: новая таблица под триггерами очереди
            assertThat(db.syncDao().outbox().map { it.tbl to it.id }).contains("height" to "h")
        } finally {
            db.close()
        }
    }
}
