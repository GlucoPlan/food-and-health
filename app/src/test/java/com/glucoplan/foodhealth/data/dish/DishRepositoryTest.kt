package com.glucoplan.foodhealth.data.dish

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.db.AppDatabase
import com.glucoplan.foodhealth.data.db.MealEntity
import com.glucoplan.foodhealth.data.db.MealItemEntity
import com.glucoplan.foodhealth.data.pan.PanForm
import com.glucoplan.foodhealth.data.pan.PanRepository
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

/** Версии блюд (ТЗ 5.4): новая варка не меняет старые. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class DishRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var prefsScope: CoroutineScope
    private lateinit var products: ProductRepository
    private lateinit var pans: PanRepository
    private lateinit var repo: DishRepository

    private lateinit var buckwheat: String
    private lateinit var butter: String

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val prefs = DevicePrefs(PreferenceDataStoreFactory.create(scope = prefsScope) {
            File(tmp.root, "test.preferences_pb")
        })
        products = ProductRepository(db.productDao(), prefs)
        pans = PanRepository(db.panDao(), prefs)
        repo = DishRepository(db, db.dishDao(), db.panDao(), db.mealDao(), products, prefs)

        buckwheat = saveProduct("Гречка", kcal = "313", carbs = "62")
        butter = saveProduct("Масло", kcal = "748", fat = "82,5")
    }

    @After
    fun tearDown() {
        db.close()
        prefsScope.cancel()
    }

    private suspend fun saveProduct(name: String, kcal: String, fat: String = "0", carbs: String = "0"): String {
        products.save(null, ProductForm(name = name, kcal = kcal, protein = "0", fat = fat, carbs = carbs))
        return products.observeProducts().first().single { it.name == name }.id
    }

    private fun form(vararg ingredients: Pair<String, String>, gross: String = "", panId: String? = null) = DishForm(
        name = "Каша",
        panId = panId,
        grossWeight = gross,
        ingredients = ingredients.mapIndexed { i, (product, weight) -> IngredientDraft("k$i", product, weight) },
    )

    private suspend fun onlyDish() = db.dishDao().observeActiveDishes().first().single()

    @Test
    fun `новое блюдо — одна варка, она текущая`() = runTest {
        assertThat(repo.save(null, DishSaveMode.NEW, form(buckwheat to "200", butter to "20")))
            .isInstanceOf(DishValidation.Valid::class.java)
        val dish = onlyDish()
        val version = repo.getVersion(dish.currentVersionId)!!
        assertThat(version.dishId).isEqualTo(dish.id)
        assertThat(version.ingredients.map { it.weightG }).containsExactly(200.0, 20.0)
        assertThat(version.byRawIngredients).isTrue()
        assertThat(version.netWeightG).isEqualTo(220.0)
    }

    @Test
    fun `сварил заново — новая текущая варка, старая и её состав не изменились`() = runTest {
        repo.save(null, DishSaveMode.NEW, form(buckwheat to "200", butter to "20"))
        val dish = onlyDish()
        val first = repo.getVersion(dish.currentVersionId)!!

        repo.save(dish.id, DishSaveMode.RECOOK, form(buckwheat to "250", gross = "600"))

        val updated = onlyDish()
        assertThat(updated.currentVersionId).isNotEqualTo(first.id)
        val second = repo.getVersion(updated.currentVersionId)!!
        assertThat(second.ingredients.map { it.weightG }).containsExactly(250.0)
        assertThat(second.netWeightG).isEqualTo(600.0)
        assertThat(repo.getVersion(first.id)).isEqualTo(first)

        val versions = repo.observeVersionSummaries(dish.id).first()
        assertThat(versions).hasSize(2)
        assertThat(versions.single { it.isCurrent }.id).isEqualTo(second.id)
    }

    @Test
    fun `правка меняет текущую варку на месте, убранный ингредиент помечается удалённым`() = runTest {
        repo.save(null, DishSaveMode.NEW, form(buckwheat to "200", butter to "20"))
        val dish = onlyDish()
        val version = repo.getVersion(dish.currentVersionId)!!
        val buckwheatRow = version.ingredients.single { it.productId == buckwheat }

        val edited = DishForm(
            name = "Гречневая каша",
            ingredients = listOf(IngredientDraft(buckwheatRow.id, buckwheat, "210")),
        )
        repo.save(dish.id, DishSaveMode.EDIT, edited)

        assertThat(onlyDish().currentVersionId).isEqualTo(version.id)
        assertThat(onlyDish().name).isEqualTo("Гречневая каша")
        val after = repo.getVersion(version.id)!!
        assertThat(after.ingredients).containsExactly(DishIngredient(buckwheatRow.id, buckwheat, 210.0))
        assertThat(after.createdAt).isEqualTo(version.createdAt)

        val all = db.dishDao().allIngredientsOf(version.id)
        assertThat(all).hasSize(2)
        assertThat(all.single { it.productId == butter }.deleted).isTrue()
    }

    @Test
    fun `вес с кастрюлей — нетто за вычетом кастрюли`() = runTest {
        val (panId, _) = pans.save(null, PanForm("Большая", "800"))
        repo.save(null, DishSaveMode.NEW, form(buckwheat to "200", gross = "1300", panId = panId))
        val version = repo.getVersion(onlyDish().currentVersionId)!!
        assertThat(version.netWeightG).isEqualTo(500.0)
        assertThat(version.panId).isEqualTo(panId)
    }

    @Test
    fun `удалённая кастрюля не пересчитывает сохранённую варку`() = runTest {
        val (panId, _) = pans.save(null, PanForm("Большая", "800"))
        repo.save(null, DishSaveMode.NEW, form(buckwheat to "200", gross = "1300", panId = panId))
        pans.delete(panId!!)
        val summary = repo.observeVersionSummaries(onlyDish().id).first().single()
        assertThat(summary.netWeightG).isEqualTo(500.0)
        assertThat(summary.panName).isEqualTo("Большая")
        assertThat(summary.panDeleted).isTrue()
    }

    @Test
    fun `список блюд — КБЖУ на 100 г текущей варки, удалённый продукт считается`() = runTest {
        repo.save(null, DishSaveMode.NEW, form(buckwheat to "100", butter to "100"))
        products.delete(butter)
        val summary = repo.observeSummaries().first().single()
        assertThat(summary.per100.kcal).isWithin(1e-9).of((313.0 + 748.0) / 200.0 * 100)
        assertThat(summary.byRawIngredients).isTrue()
    }

    @Test
    fun `удаление мягкое — блюдо пропадает из списка, варки остаются`() = runTest {
        repo.save(null, DishSaveMode.NEW, form(buckwheat to "100"))
        val dish = onlyDish()
        repo.delete(dish.id)
        assertThat(repo.observeSummaries().first()).isEmpty()
        assertThat(repo.getDish(dish.id)?.deleted).isTrue()
        assertThat(repo.getVersion(dish.currentVersionId)).isNotNull()
    }

    @Test
    fun `ошибки формы — ничего не пишется`() = runTest {
        assertThat(repo.save(null, DishSaveMode.NEW, DishForm(name = "Пусто")))
            .isInstanceOf(DishValidation.Invalid::class.java)
        assertThat(db.dishDao().observeActiveDishes().first()).isEmpty()
    }

    @Test
    fun `записанную в приём варку править нельзя — меняется только название`() = runTest {
        repo.save(null, DishSaveMode.NEW, form(buckwheat to "200"))
        val dish = onlyDish()
        val version = repo.getVersion(dish.currentVersionId)!!
        assertThat(repo.observeCurrentVersionUsed(dish.id).first()).isFalse()

        // Приём пищи с этой варкой
        db.mealDao().upsertMeal(MealEntity("m", "p", 1L, null, null, null, 1L, false, "dev"))
        db.mealDao().upsertItems(
            listOf(MealItemEntity("mi", "m", "dish", null, version.id, 300.0, null, 1.0, 1.0, 1.0, 1.0, 1L, false, "dev"))
        )
        assertThat(repo.observeCurrentVersionUsed(dish.id).first()).isTrue()

        val row = version.ingredients.single()
        repo.save(
            dish.id, DishSaveMode.EDIT,
            DishForm(name = "Гречка варёная", ingredients = listOf(IngredientDraft(row.id, buckwheat, "999"))),
        )
        assertThat(onlyDish().name).isEqualTo("Гречка варёная")
        assertThat(repo.getVersion(version.id)).isEqualTo(version)

        // «Сварил заново» разрешён
        repo.save(dish.id, DishSaveMode.RECOOK, form(buckwheat to "999"))
        val recooked = repo.getVersion(onlyDish().currentVersionId)!!
        assertThat(recooked.ingredients.single().weightG).isEqualTo(999.0)
        assertThat(repo.observeCurrentVersionUsed(dish.id).first()).isFalse()
    }
}
