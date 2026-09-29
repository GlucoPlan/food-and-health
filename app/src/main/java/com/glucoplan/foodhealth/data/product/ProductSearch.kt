package com.glucoplan.foodhealth.data.product

import java.text.Collator
import java.util.Locale

enum class ProductSort(val label: String, val defaultAscending: Boolean) {
    NAME("Название", true),
    KCAL("Ккал", false),
    PROTEIN("Белки", false),
    CARBS("Углеводы", false),
    GI("ГИ", false),
}

/** Поиск и сортировка списка продуктов (ТЗ 4.3). */
object ProductSearch {

    private val ru = Locale.forLanguageTag("ru")
    private val collator = Collator.getInstance(ru).apply { strength = Collator.SECONDARY }
    private val byName = Comparator<Product> { a, b -> collator.compare(a.name, b.name) }

    /**
     * Поиск по вхождению в название и производителя без учёта регистра и «ё»,
     * по началу штрихкода. Продукты без ГИ при сортировке по ГИ всегда в конце.
     */
    fun apply(products: List<Product>, query: String, sort: ProductSort, ascending: Boolean): List<Product> {
        val q = normalize(query)
        val found = if (q.isEmpty()) products else products.filter { p ->
            normalize(p.name).contains(q) ||
                p.brand?.let { normalize(it).contains(q) } == true ||
                p.barcode?.startsWith(q) == true
        }

        val value: ((Product) -> Double?)? = when (sort) {
            ProductSort.NAME -> null
            ProductSort.KCAL -> { p -> p.kcal }
            ProductSort.PROTEIN -> { p -> p.protein }
            ProductSort.CARBS -> { p -> p.carbs }
            ProductSort.GI -> { p -> p.gi?.toDouble() }
        }

        if (value == null) return found.sortedWith(if (ascending) byName else byName.reversed())

        val (known, unknown) = found.partition { value(it) != null }
        val numeric = compareBy<Product> { value(it)!! }
        val ordered = known.sortedWith((if (ascending) numeric else numeric.reversed()).then(byName))
        return ordered + unknown.sortedWith(byName)
    }

    private fun normalize(s: String) = s.trim().lowercase(ru).replace('ё', 'е')
}
