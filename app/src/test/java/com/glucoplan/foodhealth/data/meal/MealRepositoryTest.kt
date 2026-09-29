package com.glucoplan.foodhealth.data.meal

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.dish.DishForm
import com.glucoplan.foodhealth.data.dish.DishRepository
import com.glucoplan.foodhealth.data.dish.DishSaveMode
import com.glucoplan.foodhealth.data.dish.IngredientDraft
import com.glucoplan.foodhealth.data.prefs.DevicePrefs
import com.glucoplan.foodhealth.data.product.ProductForm
import com.glucoplan.foodhealth.data.product.ProductRepository
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
class MealRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var prefsScope: CoroutineScope
    private lateinit var prefs: DevicePrefs
    private lateinit var products: ProductRepository
    private lateinit var dishes: DishRepository
    private lateinit var repo: MealRepository

    private lateinit var bread: String
    private lateinit var soupVersion: String

    private val now = 1_700_000_000_000

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = prefsScope) { File(tmp.root, "t.preferences_pb") })
        products = ProductRepository(db.productDao(), prefs)
        dishes = DishRepository(db, db.dishDao(), db.panDao(), db.mealDao(), products, prefs)
        repo = MealRepository(db, db.mealDao(), products, dishes, prefs)

        products.save(null, ProductForm("Хлеб", kcal = "250", protein = "8", fat = "3", carbs = "48", pieceWeight = "30"))
        bread = products.observeProducts().first().single().id
        dishes.save(null, DishSaveMode.NEW, DishForm("Суп", ingredients = listOf(IngredientDraft("k", bread, "100"))))
        soupVersion = db.dishDao().observeActiveDishes().first().single().currentVersionId
    }

    @After
    fun tearDown() {
        db.close()
        prefsScope.cancel()
    }

    private val draft = MealDraft(
        items = listOf(
            DraftItem("a", MealItemType.PRODUCT, "", "", pieces = "2"),
            DraftItem("b", MealItemType.DISH, "", "50"),
        )
    )

    private fun draftWithIds() = draft.copy(
        items = listOf(draft.items[0].copy(refId = bread), draft.items[1].copy(refId = soupVersion))
    )

    @Test
    fun `запись со снимками КБЖУ и служебными полями`() = runTest {
        val result = repo.record("p1", draftWithIds(), now) as MealRecordResult.Recorded

        val meal = db.mealDao().getMeal(result.mealId)!!
        assertThat(meal.profileId).isEqualTo("p1")
        assertThat(meal.eatenAt).isEqualTo(now)
        assertThat(meal.deviceId).isEqualTo(prefs.deviceId())
        assertThat(meal.glucose).isNull()

        val items = db.mealDao().itemsOf(result.mealId).sortedBy { it.type }
        val dish = items.single { it.type == "dish" }
        val product = items.single { it.type == "product" }
        assertThat(product.productId).isEqualTo(bread)
        assertThat(product.pieces).isEqualTo(2.0)
        assertThat(product.weightG).isEqualTo(60.0)
        assertThat(product.snapshotKcal).isWithin(1e-9).of(150.0)
        // Суп: 100 г хлеба на 100 г нетто → 250 ккал на 100 г, порция 50 г
        assertThat(dish.dishVersionId).isEqualTo(soupVersion)
        assertThat(dish.snapshotKcal).isWithin(1e-9).of(125.0)
        assertThat(result.total.kcal).isWithin(1e-9).of(275.0)
    }

    @Test
    fun `выбранное время сохраняется`() = runTest {
        val result = repo.record("p1", draftWithIds().copy(eatenAt = now - 3_600_000), now) as MealRecordResult.Recorded
        assertThat(db.mealDao().getMeal(result.mealId)!!.eatenAt).isEqualTo(now - 3_600_000)
    }

    @Test
    fun `ошибки — ничего не пишется`() = runTest {
        val result = repo.record("p1", MealDraft(), now)
        assertThat(result).isInstanceOf(MealRecordResult.Invalid::class.java)
        assertThat(repo.usages("p1")).isEmpty()
    }

    @Test
    fun `снимок не меняется, когда продукт потом правят`() = runTest {
        val result = repo.record("p1", draftWithIds(), now) as MealRecordResult.Recorded
        products.save(bread, ProductForm("Хлеб", kcal = "300", protein = "8", fat = "3", carbs = "48", pieceWeight = "30"))
        val product = db.mealDao().itemsOf(result.mealId).single { it.type == "product" }
        assertThat(product.snapshotKcal).isWithin(1e-9).of(150.0)
    }

    @Test
    fun `история употребления — только этого профиля, новые сверху`() = runTest {
        repo.record("p1", draftWithIds().copy(eatenAt = now - 10_000), now)
        repo.record("p2", draftWithIds(), now)
        val usages = repo.usages("p1")
        assertThat(usages).hasSize(2)
        assertThat(usages.all { it.eatenAt == now - 10_000 }).isTrue()
    }
}
