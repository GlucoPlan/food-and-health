package com.glucoplan.foodhealth.data.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductSearchTest {

    private fun p(
        name: String, kcal: Double = 100.0, protein: Double = 1.0, carbs: Double = 1.0, gi: Int? = null,
        brand: String? = null, barcode: String? = null,
    ) = Product(
        id = name, name = name, brand = brand, barcode = barcode, kcal = kcal, protein = protein, fat = 0.0,
        carbs = carbs, fiber = null, sugar = null, salt = null, gi = gi, pieceWeightG = null,
        source = ProductSource.MANUAL, notes = null, micro = emptyMap(),
    )

    private val milk = p("Молоко", kcal = 60.0, protein = 3.0, carbs = 4.7, gi = 30, brand = "Простоквашино", barcode = "4600001")
    private val buckwheat = p("гречка", kcal = 313.0, protein = 12.6, carbs = 62.0, gi = 50)
    private val chicken = p("Курица", kcal = 190.0, protein = 20.0, carbs = 0.0, gi = null)
    private val honey = p("Мёд", kcal = 304.0, protein = 0.3, carbs = 82.0, gi = 60, barcode = "4700001")
    private val all = listOf(milk, buckwheat, chicken, honey)

    private fun names(query: String = "", sort: ProductSort = ProductSort.NAME, ascending: Boolean = sort.defaultAscending) =
        ProductSearch.apply(all, query, sort, ascending).map { it.name }

    @Test
    fun `поиск по названию без учёта регистра кириллицы`() {
        assertThat(names("МОЛ")).containsExactly("Молоко")
        assertThat(names("греч")).containsExactly("гречка")
    }

    @Test
    fun `ё и е не различаются`() {
        assertThat(names("мед")).containsExactly("Мёд")
        assertThat(names("мёд")).containsExactly("Мёд")
    }

    @Test
    fun `поиск по производителю`() {
        assertThat(names("простоквашино")).containsExactly("Молоко")
    }

    @Test
    fun `поиск по началу штрихкода`() {
        assertThat(names("460")).containsExactly("Молоко")
        assertThat(names("0001")).isEmpty()
    }

    @Test
    fun `пустой запрос — всё`() {
        assertThat(names("  ")).hasSize(4)
    }

    @Test
    fun `по названию по алфавиту без учёта регистра и обратно`() {
        assertThat(names()).containsExactly("гречка", "Курица", "Мёд", "Молоко").inOrder()
        assertThat(names(ascending = false)).containsExactly("Молоко", "Мёд", "Курица", "гречка").inOrder()
    }

    @Test
    fun `числовые сортировки по умолчанию по убыванию`() {
        assertThat(names(sort = ProductSort.KCAL)).containsExactly("гречка", "Мёд", "Курица", "Молоко").inOrder()
        assertThat(names(sort = ProductSort.PROTEIN)).containsExactly("Курица", "гречка", "Молоко", "Мёд").inOrder()
        assertThat(names(sort = ProductSort.CARBS)).containsExactly("Мёд", "гречка", "Молоко", "Курица").inOrder()
        assertThat(names(sort = ProductSort.KCAL, ascending = true))
            .containsExactly("Молоко", "Курица", "Мёд", "гречка").inOrder()
    }

    @Test
    fun `продукты без ГИ всегда в конце`() {
        assertThat(names(sort = ProductSort.GI)).containsExactly("Мёд", "гречка", "Молоко", "Курица").inOrder()
        assertThat(names(sort = ProductSort.GI, ascending = true))
            .containsExactly("Молоко", "гречка", "Мёд", "Курица").inOrder()
    }

    @Test
    fun `при равных значениях — по названию`() {
        val a = p("Б", kcal = 100.0)
        val b = p("А", kcal = 100.0)
        assertThat(ProductSearch.apply(listOf(a, b), "", ProductSort.KCAL, false).map { it.name })
            .containsExactly("А", "Б").inOrder()
    }
}
