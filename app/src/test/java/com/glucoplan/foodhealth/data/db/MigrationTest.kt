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
    fun `1 → 2 сохраняет профили и добавляет продукты`() = runTest {
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
}
