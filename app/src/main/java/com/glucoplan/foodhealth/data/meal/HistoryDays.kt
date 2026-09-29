package com.glucoplan.foodhealth.data.meal

import com.glucoplan.foodhealth.data.db.MealEntity
import com.glucoplan.foodhealth.data.db.MealItemEntity
import com.glucoplan.foodhealth.data.dish.VersionInfo
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.product.Product
import java.util.Calendar

data class HistoryItem(val name: String, val deleted: Boolean, val grams: Double, val nutrition: Nutrition)

data class HistoryMeal(val id: String, val eatenAt: Long, val items: List<HistoryItem>, val total: Nutrition)

/** День истории: [dayStart] — местная полночь. */
data class HistoryDay(val dayStart: Long, val meals: List<HistoryMeal>, val total: Nutrition)

/** История по дням (ТЗ 4.2). */
object HistoryDays {

    /**
     * КБЖУ позиции: по текущим данным продукта или варки (ТЗ 5.3: дополненный продукт
     * пересчитывает статистику), снимок — только если их нет в базе.
     */
    fun itemNutrition(item: MealItemEntity, products: Map<String, Product>, versions: Map<String, VersionInfo>): Nutrition {
        val live = when (MealItemType.fromCode(item.type)) {
            MealItemType.PRODUCT -> item.productId?.let(products::get)?.let { Nutrition.portion(it, item.weightG) }
            MealItemType.DISH -> item.dishVersionId?.let(versions::get)
                ?.let { MealCalculator.dishPortion(it.per100, item.weightG) }
            null -> null
        }
        return live ?: Nutrition(item.snapshotKcal, item.snapshotProtein, item.snapshotFat, item.snapshotCarbs)
    }

    private fun itemName(item: MealItemEntity, products: Map<String, Product>, versions: Map<String, VersionInfo>) =
        when (MealItemType.fromCode(item.type)) {
            MealItemType.PRODUCT -> item.productId?.let(products::get)?.let { it.name to it.deleted }
            MealItemType.DISH -> item.dishVersionId?.let(versions::get)?.let { it.dishName to it.dishDeleted }
            null -> null
        } ?: ("Неизвестно" to false)

    /** Приёмы с позициями, новые сверху. */
    fun meals(
        meals: List<MealEntity>,
        items: List<MealItemEntity>,
        products: Map<String, Product>,
        versions: Map<String, VersionInfo>,
    ): List<HistoryMeal> {
        val itemsByMeal = items.filter { !it.deleted }.groupBy { it.mealId }
        return meals.filter { !it.deleted }.sortedByDescending { it.eatenAt }.map { meal ->
            val list = itemsByMeal[meal.id].orEmpty().map { item ->
                val (name, deleted) = itemName(item, products, versions)
                HistoryItem(name, deleted, item.weightG, itemNutrition(item, products, versions))
            }
            HistoryMeal(meal.id, meal.eatenAt, list, list.fold(Nutrition.ZERO) { acc, i -> acc + i.nutrition })
        }
    }

    /** Группировка по местным дням, новые сверху. [day] — показать только этот день (любой момент внутри него). */
    fun group(meals: List<HistoryMeal>, day: Long? = null): List<HistoryDay> {
        val filterStart = day?.let(::dayStart)
        return meals
            .groupBy { dayStart(it.eatenAt) }
            .filterKeys { filterStart == null || it == filterStart }
            .toSortedMap(compareByDescending { it })
            .map { (start, list) ->
                val sorted = list.sortedByDescending { it.eatenAt }
                HistoryDay(start, sorted, sorted.fold(Nutrition.ZERO) { acc, m -> acc + m.total })
            }
    }

    fun dayStart(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
