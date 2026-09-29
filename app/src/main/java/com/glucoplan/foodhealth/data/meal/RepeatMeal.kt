package com.glucoplan.foodhealth.data.meal

import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.db.MealItemEntity
import com.glucoplan.foodhealth.data.dish.VersionInfo
import com.glucoplan.foodhealth.data.product.Product
import java.util.UUID

/** Черновик для «Повторить» и названия пропущенных удалённых позиций. */
data class RepeatResult(val draft: MealDraft, val skipped: List<String>) {
    /** «Удалено и не добавлено: Суп, Хлеб» (ТЗ 5.5); null — ничего не пропущено. */
    val message: String?
        get() = if (skipped.isEmpty()) null else "Удалено и не добавлено: ${skipped.joinToString(", ")}"
}

/**
 * «Повторить» (ТЗ 4.2, 5.5): состав приёма — новым приёмом на экран «Приём пищи».
 * Вес и штуки копируются, время «сейчас», блюдо — его текущая варка.
 * Удалённые продукты и блюда пропускаются.
 */
object RepeatMeal {

    fun build(
        profileId: String,
        items: List<MealItemEntity>,
        products: Map<String, Product>,
        versions: Map<String, VersionInfo>,
    ): RepeatResult {
        val currentByDish = versions.values.filter { it.isCurrent }.associateBy { it.dishId }
        val skipped = mutableListOf<String>()
        val draftItems = items.filter { !it.deleted }.mapNotNull { item ->
            when (MealItemType.fromCode(item.type)) {
                MealItemType.PRODUCT -> {
                    val product = item.productId?.let(products::get)
                    if (product == null || product.deleted) {
                        skipped += product?.name ?: "Неизвестный продукт"
                        null
                    } else {
                        DraftItem(
                            key = UUID.randomUUID().toString(),
                            type = MealItemType.PRODUCT,
                            refId = product.id,
                            weight = NumberText.format(item.weightG, 1),
                            pieces = item.pieces?.let { NumberText.format(it, 2) },
                        )
                    }
                }

                MealItemType.DISH -> {
                    val eaten = item.dishVersionId?.let(versions::get)
                    val current = eaten?.let { currentByDish[it.dishId] }
                    if (eaten == null || eaten.dishDeleted || current == null) {
                        skipped += eaten?.dishName ?: "Неизвестное блюдо"
                        null
                    } else {
                        DraftItem(
                            key = UUID.randomUUID().toString(),
                            type = MealItemType.DISH,
                            refId = current.versionId,
                            weight = NumberText.format(item.weightG, 1),
                        )
                    }
                }

                null -> null
            }
        }
        return RepeatResult(MealDraft(profileId = profileId, eatenAt = null, items = draftItems), skipped)
    }
}
