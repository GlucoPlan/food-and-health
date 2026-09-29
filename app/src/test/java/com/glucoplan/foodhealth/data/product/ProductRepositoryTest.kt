package com.glucoplan.foodhealth.data.product

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ProductRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var prefsScope: CoroutineScope
    private lateinit var devicePrefs: DevicePrefs
    private lateinit var repo: ProductRepository

    private val milk = ProductForm(name = "Молоко", kcal = "60", protein = "3", fat = "3,2", carbs = "4,7")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        devicePrefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = prefsScope) {
            File(tmp.root, "test.preferences_pb")
        })
        repo = ProductRepository(db.productDao(), devicePrefs)
    }

    @After
    fun tearDown() {
        db.close()
        prefsScope.cancel()
    }

    private suspend fun onlyId() = repo.observeProducts().first().single().id

    @Test
    fun `новый продукт получает UUID и служебные поля`() = runTest {
        val before = System.currentTimeMillis()
        assertThat(repo.save(null, milk)).isInstanceOf(ProductValidation.Valid::class.java)
        val entity = db.productDao().getById(onlyId())!!
        assertThat(entity.id).matches("[0-9a-f-]{36}")
        assertThat(entity.updatedAt).isAtLeast(before)
        assertThat(entity.deviceId).isEqualTo(devicePrefs.deviceId())
        assertThat(entity.deleted).isFalse()
        assertThat(entity.source).isEqualTo("manual")
    }

    @Test
    fun `ошибки формы — ничего не пишется`() = runTest {
        assertThat(repo.save(null, milk.copy(name = ""))).isInstanceOf(ProductValidation.Invalid::class.java)
        assertThat(repo.observeProducts().first()).isEmpty()
    }

    @Test
    fun `изменение сохраняет id`() = runTest {
        repo.save(null, milk)
        val id = onlyId()
        repo.save(id, milk.copy(name = "Молоко 2,5%", source = ProductSource.LABEL))
        val p = repo.get(id)!!
        assertThat(p.name).isEqualTo("Молоко 2,5%")
        assertThat(p.source).isEqualTo(ProductSource.LABEL)
        assertThat(repo.observeProducts().first()).hasSize(1)
    }

    @Test
    fun `штрихкод другого продукта занять нельзя, свой — можно`() = runTest {
        repo.save(null, milk.copy(barcode = "4600001"))
        val id = onlyId()
        assertThat(repo.save(id, milk.copy(barcode = "4600001", kcal = "61")))
            .isInstanceOf(ProductValidation.Valid::class.java)

        val result = repo.save(null, milk.copy(name = "Кефир", barcode = "4600001"))
        assertThat((result as ProductValidation.Invalid).errors).containsKey(ProductField.BARCODE)
    }

    @Test
    fun `штрихкод удалённого продукта можно занять`() = runTest {
        repo.save(null, milk.copy(barcode = "4600001"))
        repo.delete(onlyId())
        assertThat(repo.save(null, milk.copy(name = "Кефир", barcode = "4600001")))
            .isInstanceOf(ProductValidation.Valid::class.java)
    }

    @Test
    fun `удаление мягкое: из списка пропадает, по id доступен`() = runTest {
        repo.save(null, milk)
        val id = onlyId()
        repo.delete(id)
        assertThat(repo.observeProducts().first()).isEmpty()
        assertThat(repo.get(id)?.name).isEqualTo("Молоко")
        assertThat(db.productDao().getById(id)?.deleted).isTrue()
    }
}
