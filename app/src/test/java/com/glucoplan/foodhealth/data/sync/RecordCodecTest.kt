package com.glucoplan.foodhealth.data.sync

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.foodhealth.data.db.DishEntity
import com.glucoplan.foodhealth.data.db.DishIngredientEntity
import com.glucoplan.foodhealth.data.db.DishVersionEntity
import com.glucoplan.foodhealth.data.db.MealEntity
import com.glucoplan.foodhealth.data.db.MealItemEntity
import com.glucoplan.foodhealth.data.db.PanEntity
import com.glucoplan.foodhealth.data.db.ProductEntity
import com.glucoplan.foodhealth.data.db.ProfileEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Запись ↔ JSON для всех синхронизируемых таблиц без потерь. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class RecordCodecTest {

    @get:Rule val tmp = TemporaryFolder()

    private val phones = mutableListOf<Phone>()
    private fun phone(name: String) = Phone(tmp.root, name, FakeServer()).also { phones += it }

    @After
    fun tearDown() = phones.forEach { it.close() }

    private fun copy(from: Phone, to: Phone, table: String, id: String) {
        val data = RecordCodec(from.db.openHelper.writableDatabase).read(table, listOf(id)).getValue(id)
        assertThat(RecordCodec(to.db.openHelper.writableDatabase).write(table, id, data)).isTrue()
    }

    @Test
    fun `нормы профиля передаются без изменений (17_3)`() = runTest {
        val a = phone("a")
        val b = phone("b")
        val profile = ProfileEntity(
            "pr", "Иван", false, false, 10.0, 11L, false, "dev",
            sex = "male", birthDate = "1985-03-08", waterEnabled = true, waterMlPerKg = 35.0,
            activity = "light", targetWeightKg = 80.0, weightPaceKg = 0.3,
            normKcal = 2100.0, normProtein = null, normFat = 70.0, normCarbs = null,
            glucoseLow = 3.9, glucoseHigh = 10.0,
        )
        a.db.profileDao().upsert(profile)
        copy(a, b, "profile", "pr")
        assertThat(b.db.profileDao().getById("pr")).isEqualTo(profile)
    }

    @Test
    fun `все таблицы туда и обратно без изменений`() = runTest {
        val a = phone("a")
        val b = phone("b")
        val profile = ProfileEntity("pr", "Дочь", true, true, 12.5, 11L, false, "dev")
        val product = ProductEntity(
            "p", "Молоко", null, "4600001", 60.0, 3.0, 3.2, 4.7, null, 4.7, 0.1, 30, 250.0, "label", "заметка",
            mapOf("ca" to 120.0, "vit_b12" to 0.4), 12L, false, "dev",
        )
        val pan = PanEntity("pan", "Большая", 800.0, "x.jpg", 13L, true, "dev")
        val dish = DishEntity("d", "Суп", "v", 14L, false, "dev")
        val version = DishVersionEntity("v", "d", 100L, "pan", null, 500.0, 15L, false, "dev")
        val ingredient = DishIngredientEntity("i", "v", "p", 200.0, 16L, false, "dev")
        val meal = MealEntity("m", "pr", 200L, null, 6.5, 2.25, 17L, false, "dev")
        val item = MealItemEntity("mi", "m", "dish", null, "v", 350.0, null, 140.0, 7.0, 3.5, 21.0, 18L, false, "dev")

        a.db.profileDao().upsert(profile)
        a.db.productDao().upsert(product)
        a.db.panDao().upsert(pan)
        a.db.dishDao().upsertVersion(version)
        a.db.dishDao().upsertIngredients(listOf(ingredient))
        a.db.dishDao().upsertDish(dish)
        a.db.mealDao().upsertMeal(meal)
        a.db.mealDao().upsertItems(listOf(item))

        listOf("profile" to "pr", "product" to "p", "pan" to "pan", "dish" to "d", "dish_version" to "v",
            "dish_ingredient" to "i", "meal" to "m", "meal_item" to "mi").forEach { (t, id) -> copy(a, b, t, id) }

        assertThat(b.db.profileDao().getById("pr")).isEqualTo(profile)
        assertThat(b.db.productDao().getById("p")).isEqualTo(product)
        assertThat(b.db.panDao().getById("pan")).isEqualTo(pan)
        assertThat(b.db.dishDao().getDish("d")).isEqualTo(dish)
        assertThat(b.db.dishDao().getVersion("v")).isEqualTo(version)
        assertThat(b.db.dishDao().ingredientsOf("v")).containsExactly(ingredient)
        assertThat(b.db.mealDao().getMeal("m")).isEqualTo(meal)
        assertThat(b.db.mealDao().itemsOf("m")).containsExactly(item)
    }

    @Test
    fun `неизвестные поля от новой версии сохраняются и отправляются обратно`() = runTest {
        val b = phone("b")
        val codec = RecordCodec(b.db.openHelper.writableDatabase)
        val data = JsonObject(
            mapOf(
                "id" to JsonPrimitive("pr"), "name" to JsonPrimitive("Иван"), "sd1_enabled" to JsonPrimitive(0),
                "show_xe" to JsonPrimitive(0), "carbs_per_xe" to JsonPrimitive(10.0), "updated_at" to JsonPrimitive(1),
                "deleted" to JsonPrimitive(0), "device_id" to JsonPrimitive("new-phone"),
                "birth_year" to JsonPrimitive(1980),
            )
        )
        assertThat(codec.write("profile", "pr", data)).isTrue()
        assertThat(b.db.profileDao().getById("pr")!!.name).isEqualTo("Иван")

        // Старый телефон правит запись — неизвестное поле не теряется
        b.profiles.save("pr", com.glucoplan.foodhealth.data.profile.filledProfileForm("Иван Петрович"))
        val back = codec.read("profile", listOf("pr")).getValue("pr")
        assertThat(back["birth_year"]).isEqualTo(JsonPrimitive(1980))
        assertThat(back["name"]).isEqualTo(JsonPrimitive("Иван Петрович"))
    }

    @Test
    fun `запись без обязательного поля не ломает остальные`() = runTest {
        val b = phone("b")
        val codec = RecordCodec(b.db.openHelper.writableDatabase)
        val broken = JsonObject(mapOf("id" to JsonPrimitive("x"), "name" to JsonPrimitive("Без калорий")))
        assertThat(codec.write("product", "x", broken)).isFalse()
        assertThat(b.db.productDao().getById("x")).isNull()
    }

    @Test
    fun `чтение отсутствующих id не падает`() = runTest {
        val a = phone("a")
        assertThat(RecordCodec(a.db.openHelper.writableDatabase).read("product", listOf("нет"))).isEmpty()
    }
}
