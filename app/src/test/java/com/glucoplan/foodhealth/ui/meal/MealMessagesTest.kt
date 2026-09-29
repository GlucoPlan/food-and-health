package com.glucoplan.foodhealth.ui.meal

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.dish.DishRepository
import com.glucoplan.foodhealth.data.meal.DraftItem
import com.glucoplan.foodhealth.data.meal.MealDraft
import com.glucoplan.foodhealth.data.meal.MealDraftStore
import com.glucoplan.foodhealth.data.meal.MealItemType
import com.glucoplan.foodhealth.data.meal.MealRepository
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.product.ProductForm
import com.glucoplan.foodhealth.data.product.ProductRepository
import com.glucoplan.foodhealth.data.profile.ProfileForm
import com.glucoplan.foodhealth.data.profile.ProfileRepository
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

/** Сообщения экрана «Приём пищи» доходят, даже если экран начал их слушать позже. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MealMessagesTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var prefs: DevicePrefs
    private lateinit var products: ProductRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var store: MealDraftStore
    private lateinit var vm: MealViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") }
        prefs = DevicePrefs(dataStore)
        products = ProductRepository(db.productDao(), prefs)
        profiles = ProfileRepository(db.profileDao(), prefs)
        val dishes = DishRepository(db, db.dishDao(), db.panDao(), db.mealDao(), products, prefs)
        store = MealDraftStore(dataStore, scope)
        vm = MealViewModel(store, MealRepository(db, db.mealDao(), products, dishes, prefs), products, dishes, profiles, prefs)
    }

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
        Dispatchers.resetMain()
    }

    /** Ждём по-настоящему: DataStore и Room работают в своих потоках. */
    private suspend fun <T> Flow<T>.await(predicate: (T) -> Boolean = { true }): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { first(predicate) } }

    @Test
    fun `сообщение от «Повторить» доходит до экрана, открытого позже`() = runTest {
        store.draft.await { it != null }
        store.replace(MealDraft(), notice = "Удалено и не добавлено: Хлеб")

        // Экран начинает слушать только сейчас — сообщение ждало в очереди
        assertThat(vm.messages.await()).isEqualTo("Удалено и не добавлено: Хлеб")
        assertThat(store.notice.value).isNull()
    }

    @Test
    fun `после записи приходит «Записано»`() = runTest {
        profiles.save(null, ProfileForm("Иван"))
        val profile = profiles.observeProfiles().await { it.isNotEmpty() }.single()
        prefs.setOwner(profile.id)
        products.save(null, ProductForm("Молоко", kcal = "60", protein = "3", fat = "3", carbs = "5"))
        val milk = products.observeProducts().await { it.isNotEmpty() }.single()

        store.draft.await { it != null }
        store.update { it.copy(items = listOf(DraftItem("a", MealItemType.PRODUCT, milk.id, "100"))) }
        vm.state.await { it.loaded && it.profileId == profile.id && it.rows.isNotEmpty() }

        vm.record()

        assertThat(vm.messages.await()).isEqualTo("Записано: 60 ккал")
        assertThat(store.draft.value?.items).isEmpty()
    }
}
