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

/** Правка, удаление и «Повторить» записанного приёма (ТЗ 4.2). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MealEditTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var prefsScope: CoroutineScope
    private lateinit var products: ProductRepository
    private lateinit var dishes: DishRepository
    private lateinit var repo: MealRepository

    private lateinit var milk: String
    private lateinit var bread: String
    private lateinit var mealId: String

    private val now = 1_700_000_000_000

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = prefsScope) { File(tmp.root, "t.preferences_pb") })
        products = ProductRepository(db.productDao(), prefs)
        dishes = DishRepository(db, db.dishDao(), db.panDao(), db.mealDao(), products, prefs)
        repo = MealRepository(db, db.mealDao(), products, dishes, prefs)

        products.save(null, ProductForm("Молоко", kcal = "60", protein = "3", fat = "3,2", carbs = "4,7"))
        products.save(null, ProductForm("Хлеб", kcal = "250", protein = "8", fat = "3", carbs = "48", pieceWeight = "30"))
        val all = products.observeProducts().first()
        milk = all.single { it.name == "Молоко" }.id
        bread = all.single { it.name == "Хлеб" }.id

        val draft = MealDraft(
            eatenAt = now - 3_600_000,
            items = listOf(
                DraftItem("a", MealItemType.PRODUCT, milk, "200"),
                DraftItem("b", MealItemType.PRODUCT, bread, "", pieces = "1"),
            ),
        )
        mealId = (repo.record("p1", draft, now) as MealRecordResult.Recorded).mealId
    }

    @After
    fun tearDown() {
        db.close()
        prefsScope.cancel()
    }

    @Test
    fun `открытый приём — черновик с id позиций`() = runTest {
        val draft = repo.loadForEdit(mealId)!!
        assertThat(draft.profileId).isEqualTo("p1")
        assertThat(draft.eatenAt).isEqualTo(now - 3_600_000)
        val ids = db.mealDao().itemsOf(mealId).map { it.id }
        assertThat(draft.items.map { it.key }).containsExactlyElementsIn(ids)
        assertThat(draft.items.single { it.refId == bread }.pieces).isEqualTo("1")
    }

    @Test
    fun `правка — вес и снимок пересчитаны, убранное помечено, новое добавлено`() = runTest {
        val draft = repo.loadForEdit(mealId)!!
        val milkItem = draft.items.single { it.refId == milk }
        val edited = draft.copy(
            profileId = "p2",
            eatenAt = now - 7_200_000,
            items = listOf(
                milkItem.copy(weight = "300"),
                DraftItem("new", MealItemType.PRODUCT, bread, "50"),
            ),
        )
        assertThat(repo.update(mealId, "p2", edited, now)).isInstanceOf(MealRecordResult.Recorded::class.java)

        val meal = db.mealDao().getMeal(mealId)!!
        assertThat(meal.profileId).isEqualTo("p2")
        assertThat(meal.eatenAt).isEqualTo(now - 7_200_000)

        val active = db.mealDao().itemsOf(mealId)
        assertThat(active).hasSize(2)
        val milkRow = active.single { it.id == milkItem.key }
        assertThat(milkRow.weightG).isEqualTo(300.0)
        assertThat(milkRow.snapshotKcal).isWithin(1e-9).of(180.0)
        assertThat(active.single { it.id == "new" }.snapshotKcal).isWithin(1e-9).of(125.0)

        val all = db.mealDao().allItemsOf(mealId)
        assertThat(all).hasSize(3)
        assertThat(all.single { it.productId == bread && it.pieces == 1.0 }.deleted).isTrue()
    }

    @Test
    fun `правка с ошибкой ничего не меняет`() = runTest {
        val draft = repo.loadForEdit(mealId)!!
        val result = repo.update(mealId, "p1", draft.copy(items = emptyList()), now)
        assertThat(result).isInstanceOf(MealRecordResult.Invalid::class.java)
        assertThat(db.mealDao().itemsOf(mealId)).hasSize(2)
    }

    @Test
    fun `история профиля и удаление приёма`() = runTest {
        val history = repo.observeHistory("p1").first()
        assertThat(history.single().id).isEqualTo(mealId)
        assertThat(history.single().total.kcal).isWithin(1e-9).of(120.0 + 75.0)
        assertThat(repo.observeHistory("p2").first()).isEmpty()

        repo.delete(mealId)
        assertThat(repo.observeHistory("p1").first()).isEmpty()
        assertThat(db.mealDao().getMeal(mealId)?.deleted).isTrue()
        assertThat(repo.loadForEdit(mealId)).isNull()
    }

    @Test
    fun `история пересчитывается, если продукт дополнили`() = runTest {
        products.save(milk, ProductForm("Молоко", kcal = "64", protein = "3", fat = "3,2", carbs = "4,7"))
        assertThat(repo.observeHistory("p1").first().single().total.kcal).isWithin(1e-9).of(128.0 + 75.0)
    }

    @Test
    fun `повторить — текущая варка блюда, удалённое пропущено`() = runTest {
        dishes.save(null, DishSaveMode.NEW, DishForm("Суп", ingredients = listOf(IngredientDraft("k", milk, "100"))))
        val dish = db.dishDao().observeActiveDishes().first().single()
        val first = dish.currentVersionId
        val soupMeal = (repo.record(
            "p1",
            MealDraft(items = listOf(
                DraftItem("s", MealItemType.DISH, first, "300"),
                DraftItem("m", MealItemType.PRODUCT, bread, "30"),
            )),
            now,
        ) as MealRecordResult.Recorded).mealId

        dishes.save(dish.id, DishSaveMode.RECOOK, DishForm("Суп", ingredients = listOf(IngredientDraft("k", milk, "200"))))
        products.delete(bread)

        val r = repo.repeat(soupMeal)!!
        assertThat(r.draft.items.single().refId).isEqualTo(db.dishDao().getDish(dish.id)!!.currentVersionId)
        assertThat(r.draft.items.single().refId).isNotEqualTo(first)
        assertThat(r.skipped).containsExactly("Хлеб")
    }
}
