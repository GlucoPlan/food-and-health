package com.glucoplan.foodhealth.ui.picker

import com.glucoplan.foodhealth.data.meal.MealItemType

/** Результат выбора: продукт (id продукта) или блюдо (id варки). Передаётся строкой через savedStateHandle. */
data class PickedItem(val type: MealItemType, val id: String) {
    fun encode() = "${type.code}:$id"

    companion object {
        fun decode(text: String): PickedItem? {
            val type = MealItemType.fromCode(text.substringBefore(':')) ?: return null
            val id = text.substringAfter(':', "").ifEmpty { return null }
            return PickedItem(type, id)
        }
    }
}
