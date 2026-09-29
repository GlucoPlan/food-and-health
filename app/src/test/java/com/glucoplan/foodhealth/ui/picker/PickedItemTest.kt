package com.glucoplan.foodhealth.ui.picker

import com.glucoplan.foodhealth.data.meal.MealItemType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PickedItemTest {

    @Test
    fun `туда и обратно`() {
        val item = PickedItem(MealItemType.DISH, "0f8c-uuid")
        assertThat(PickedItem.decode(item.encode())).isEqualTo(item)
    }

    @Test
    fun `мусор — null`() {
        assertThat(PickedItem.decode("soup:1")).isNull()
        assertThat(PickedItem.decode("product:")).isNull()
        assertThat(PickedItem.decode("")).isNull()
    }
}
