package com.glucoplan.foodhealth.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ProductDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ProductDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.productDao()
    }

    @After
    fun tearDown() = db.close()

    private fun product(id: String, barcode: String? = null, deleted: Boolean = false) = ProductEntity(
        id = id, name = "Продукт $id", brand = null, barcode = barcode, kcal = 100.0, protein = 1.0, fat = 2.0,
        carbs = 3.0, fiber = null, sugar = null, salt = null, gi = null, pieceWeightG = null, source = "manual",
        notes = null, micro = mapOf("vit_c" to 1.5), updatedAt = 1L, deleted = deleted, deviceId = "dev",
    )

    @Test
    fun `все поля сохраняются, включая витамины`() = runTest {
        val p = product("1", barcode = "123").copy(gi = 40, fiber = 0.5, pieceWeightG = 60.0, notes = "заметка")
        dao.upsert(p)
        assertThat(dao.getById("1")).isEqualTo(p)
    }

    @Test
    fun `мягко удалённый не виден в списке, но доступен по id`() = runTest {
        dao.upsert(product("1"))
        dao.upsert(product("2", deleted = true))
        assertThat(dao.observeActive().first().map { it.id }).containsExactly("1")
        assertThat(dao.getById("2")?.deleted).isTrue()
    }

    @Test
    fun `поиск по штрихкоду только среди неудалённых`() = runTest {
        dao.upsert(product("1", barcode = "123", deleted = true))
        dao.upsert(product("2", barcode = "123"))
        assertThat(dao.findActiveByBarcode("123").map { it.id }).containsExactly("2")
    }
}
