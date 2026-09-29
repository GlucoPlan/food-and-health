package com.glucoplan.foodhealth.data.meal

import com.glucoplan.foodhealth.data.db.MealItemEntity
import com.glucoplan.foodhealth.data.dish.VersionInfo
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.testProduct
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RepeatMealTest {

    private val bread = testProduct("хлеб", kcal = 250.0, pieceWeightG = 30.0)
    private val oldBread = testProduct("старый хлеб", kcal = 250.0, deleted = true)
    private val products = mapOf("хлеб" to bread, "старый хлеб" to oldBread)

    private fun version(id: String, dishId: String, name: String, current: Boolean, deleted: Boolean = false) =
        VersionInfo(id, dishId, name, deleted, 1L, Nutrition.ZERO, current)

    private val versions = listOf(
        version("суп-1", "суп", "Суп", current = false),
        version("суп-2", "суп", "Суп", current = true),
        version("каша-1", "каша", "Каша", current = true, deleted = true),
    ).associateBy { it.versionId }

    private fun item(type: String, ref: String, weight: Double, pieces: Double? = null, deleted: Boolean = false) =
        MealItemEntity(
            "id-$ref", "m", type, ref.takeIf { type == "product" }, ref.takeIf { type == "dish" }, weight, pieces,
            0.0, 0.0, 0.0, 0.0, 1L, deleted, "dev",
        )

    @Test
    fun `вес и штуки копируются, время сейчас, профиль тот же`() {
        val r = RepeatMeal.build("p1", listOf(item("product", "хлеб", 60.0, pieces = 2.0)), products, versions)
        assertThat(r.draft.profileId).isEqualTo("p1")
        assertThat(r.draft.eatenAt).isNull()
        val d = r.draft.items.single()
        assertThat(d.refId).isEqualTo("хлеб")
        assertThat(d.weight).isEqualTo("60")
        assertThat(d.pieces).isEqualTo("2")
        assertThat(r.skipped).isEmpty()
        assertThat(r.message).isNull()
    }

    @Test
    fun `блюдо — текущая варка, даже если ели старую`() {
        val r = RepeatMeal.build("p1", listOf(item("dish", "суп-1", 350.0)), products, versions)
        assertThat(r.draft.items.single().refId).isEqualTo("суп-2")
        assertThat(r.draft.items.single().weight).isEqualTo("350")
    }

    @Test
    fun `удалённые продукты и блюда пропускаются с сообщением`() {
        val r = RepeatMeal.build(
            "p1",
            listOf(item("product", "хлеб", 30.0), item("product", "старый хлеб", 30.0), item("dish", "каша-1", 200.0)),
            products,
            versions,
        )
        assertThat(r.draft.items.map { it.refId }).containsExactly("хлеб")
        assertThat(r.skipped).containsExactly("старый хлеб", "Каша").inOrder()
        assertThat(r.message).isEqualTo("Удалено и не добавлено: старый хлеб, Каша")
    }

    @Test
    fun `удалённые позиции приёма не повторяются`() {
        val r = RepeatMeal.build("p1", listOf(item("product", "хлеб", 30.0, deleted = true)), products, versions)
        assertThat(r.draft.items).isEmpty()
        assertThat(r.skipped).isEmpty()
    }

    @Test
    fun `новые ключи позиций`() {
        val r = RepeatMeal.build("p1", listOf(item("product", "хлеб", 30.0), item("product", "хлеб", 40.0)), products, versions)
        assertThat(r.draft.items.map { it.key }.toSet()).hasSize(2)
        assertThat(r.draft.items.map { it.key }).doesNotContain("id-хлеб")
    }
}
