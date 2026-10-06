package com.glucoplan.foodhealth.ui.products

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.product.ProductForm
import com.glucoplan.foodhealth.data.product.ProductRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Что происходит на вкладке «Продукты» и в карточке продукта после сканирования. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ProductScanTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var prefsScope: CoroutineScope
    private lateinit var repo: ProductRepository
    private lateinit var vm: ProductsViewModel

    private val milk = ProductForm(name = "Молоко", barcode = "4600001", kcal = "60", protein = "3", fat = "3,2", carbs = "4,7")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = prefsScope) {
            File(tmp.root, "test.preferences_pb")
        })
        repo = ProductRepository(db.productDao(), prefs)
        vm = ProductsViewModel(repo)
    }

    @After
    fun tearDown() {
        db.close()
        prefsScope.cancel()
        Dispatchers.resetMain()
    }

    /** Ждём по-настоящему: Room отвечает из своего потока, виртуальное время тут не помогает. */
    private suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { first(predicate) } }

    @Test
    fun `известный код подставляется в поиск и продукт виден в списке`() = runTest {
        repo.save(null, milk)
        vm.onScanned("4600001")
        assertThat(vm.query.await { it.text.isNotEmpty() }.text).isEqualTo("4600001")
        assertThat(vm.state.await { it.loaded }.products.map { it.name }).containsExactly("Молоко")
        assertThat(vm.createWithBarcode.value).isNull()
    }

    @Test
    fun `неизвестный код открывает создание продукта с этим кодом`() = runTest {
        vm.onScanned("4600002")
        assertThat(vm.createWithBarcode.await { it != null }).isEqualTo("4600002")
        assertThat(vm.query.value.text).isEmpty()

        vm.onCreateWithBarcodeHandled()
        assertThat(vm.createWithBarcode.value).isNull()
    }

    @Test
    fun `код удалённого продукта считается неизвестным`() = runTest {
        repo.save(null, milk)
        repo.delete(repo.findByBarcode("4600001")!!.id)
        vm.onScanned("4600001")
        assertThat(vm.createWithBarcode.await { it != null }).isEqualTo("4600001")
    }

    private fun editVm(id: String? = null) =
        ProductEditViewModel(SavedStateHandle(mapOf(ProductEditViewModel.ARG_ID to id)), repo)

    @Test
    fun `код существующего продукта в новом продукте открывает его карточку`() = runTest {
        repo.save(null, milk)
        val milkId = repo.findByBarcode("4600001")!!.id
        val edit = editVm()
        edit.onScanned("4600001")
        assertThat(edit.state.await { it.existingId != null }.existingId).isEqualTo(milkId)
    }

    @Test
    fun `неизвестный код в новом продукте подставляется в поле`() = runTest {
        val edit = editVm()
        edit.onScanned("4600002")
        val state = edit.state.await { it.form.barcode.isNotEmpty() }
        assertThat(state.form.barcode).isEqualTo("4600002")
        assertThat(state.existingId).isNull()
    }

    @Test
    fun `код удалённого продукта в новом продукте подставляется в поле`() = runTest {
        repo.save(null, milk)
        repo.delete(repo.findByBarcode("4600001")!!.id)
        val edit = editVm()
        edit.onScanned("4600001")
        val state = edit.state.await { it.form.barcode.isNotEmpty() }
        assertThat(state.existingId).isNull()
    }

    @Test
    fun `в существующем продукте чужой код не уводит с карточки`() = runTest {
        repo.save(null, milk)
        repo.save(null, milk.copy(name = "Кефир", barcode = "4600003"))
        val kefirId = repo.findByBarcode("4600003")!!.id
        val edit = editVm(kefirId)
        edit.state.await { it.loaded }
        edit.onScanned("4600001")
        val state = edit.state.await { it.form.barcode == "4600001" }
        assertThat(state.existingId).isNull()
    }
}
