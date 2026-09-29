package com.glucoplan.foodhealth.data.meal

import com.glucoplan.foodhealth.data.nutrition.Nutrition

enum class MealItemType(val code: String) {
    PRODUCT("product"),
    DISH("dish");

    companion object {
        fun fromCode(code: String): MealItemType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Позиция незаписанного приёма. [refId] — id продукта или id варки блюда.
 * [pieces] не null — вес вводили штуками, граммы = штуки × вес штуки продукта.
 */
data class DraftItem(
    val key: String,
    val type: MealItemType,
    val refId: String,
    val weight: String,
    val pieces: String? = null,
)

/**
 * Незаписанный приём (ТЗ 4.1): не теряется при переключении вкладок и перезапуске.
 * [profileId] null — владелец телефона, [eatenAt] null — «сейчас».
 */
data class MealDraft(
    val profileId: String? = null,
    val eatenAt: Long? = null,
    val items: List<DraftItem> = emptyList(),
) {
    val isEmpty: Boolean get() = profileId == null && eatenAt == null && items.isEmpty()
}

/** Позиция, сопоставленная с продуктом или варкой. */
data class ResolvedItem(
    val key: String,
    val type: MealItemType,
    val refId: String,
    val name: String,
    /** Продукт или блюдо удалены после добавления в приём. */
    val deleted: Boolean,
    /** Продукта или варки нет в базе. */
    val missing: Boolean,
    val grams: Double?,
    val pieces: Double?,
    val pieceWeightG: Double?,
    val portion: Nutrition?,
    val dishId: String?,
    val cookedAt: Long?,
)
