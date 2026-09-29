package com.glucoplan.foodhealth.data.meal

import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.dish.VersionInfo
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import com.glucoplan.foodhealth.data.product.PieceWeight
import com.glucoplan.foodhealth.data.product.Product

/** Порции и итоги приёма (ТЗ 5.3). */
object MealCalculator {

    /** Сопоставляет позиции черновика с продуктами и варками и считает порции. */
    fun resolve(
        items: List<DraftItem>,
        products: Map<String, Product>,
        versions: Map<String, VersionInfo>,
    ): List<ResolvedItem> = items.map { item ->
        when (item.type) {
            MealItemType.PRODUCT -> {
                val product = products[item.refId]
                val pieces = item.pieces?.let(NumberText::parse)
                val pieceWeight = product?.pieceWeightG
                val grams = when {
                    item.pieces == null -> NumberText.parse(item.weight)
                    pieces != null && pieceWeight != null -> PieceWeight.toGrams(pieces, pieceWeight)
                    else -> null
                }?.takeIf { it > 0 }
                ResolvedItem(
                    key = item.key, type = item.type, refId = item.refId,
                    name = product?.name ?: "Неизвестный продукт",
                    deleted = product?.deleted == true,
                    missing = product == null,
                    grams = grams,
                    pieces = pieces.takeIf { item.pieces != null },
                    pieceWeightG = product?.pieceWeightG,
                    portion = if (product != null && grams != null) Nutrition.portion(product, grams) else null,
                    dishId = null,
                    cookedAt = null,
                )
            }

            MealItemType.DISH -> {
                val version = versions[item.refId]
                val grams = NumberText.parse(item.weight)?.takeIf { it > 0 }
                ResolvedItem(
                    key = item.key, type = item.type, refId = item.refId,
                    name = version?.dishName ?: "Неизвестное блюдо",
                    deleted = version?.dishDeleted == true,
                    missing = version == null,
                    grams = grams,
                    pieces = null,
                    pieceWeightG = null,
                    portion = if (version != null && grams != null) dishPortion(version.per100, grams) else null,
                    dishId = version?.dishId,
                    cookedAt = version?.createdAt,
                )
            }
        }
    }

    fun dishPortion(per100: Nutrition, grams: Double): Nutrition = per100.scaled(grams / 100.0)

    /** Итоги приёма: сумма порций с известным весом. */
    fun total(items: List<ResolvedItem>): Nutrition =
        items.mapNotNull { it.portion }.fold(Nutrition.ZERO) { acc, n -> acc + n }
}

/** Ключи ошибок: «items», «time», «glucose», «dose», «item:<key>». */
object MealField {
    const val ITEMS = "items"
    const val TIME = "time"
    const val GLUCOSE = "glucose"
    const val DOSE = "dose"
    fun item(key: String) = "item:$key"
}

object MealValidator {

    /** Допуск на расхождение часов при проверке «не в будущем». */
    private const val CLOCK_SLACK_MS = 60_000L

    fun validate(items: List<ResolvedItem>, eatenAt: Long?, now: Long): Map<String, String> = buildMap {
        if (items.isEmpty()) put(MealField.ITEMS, "Добавьте продукт или блюдо")
        items.forEach { item ->
            when {
                item.missing -> put(MealField.item(item.key), "Нет в базе")
                item.grams == null -> put(MealField.item(item.key), "Вес больше 0")
            }
        }
        if (eatenAt != null && eatenAt > now + CLOCK_SLACK_MS) put(MealField.TIME, "Время в будущем")
    }
}
