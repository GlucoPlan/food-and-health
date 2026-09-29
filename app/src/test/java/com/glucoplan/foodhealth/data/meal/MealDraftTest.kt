package com.glucoplan.foodhealth.data.meal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MealDraftTest {

    @Test
    fun `черновик туда и обратно`() {
        val draft = MealDraft(
            profileId = "p1",
            eatenAt = 1_700_000_000_000,
            items = listOf(
                DraftItem("a", MealItemType.PRODUCT, "молоко", "200,5"),
                DraftItem("b", MealItemType.PRODUCT, "хлеб", "60", pieces = "2"),
                DraftItem("c", MealItemType.DISH, "v1", "350"),
            ),
            glucose = "6,5",
            dose = "4",
        )
        assertThat(MealDraftJson.decode(MealDraftJson.encode(draft))).isEqualTo(draft)
    }

    @Test
    fun `пустой черновик`() {
        assertThat(MealDraftJson.decode(MealDraftJson.encode(MealDraft()))).isEqualTo(MealDraft())
        assertThat(MealDraftJson.decode(null)).isEqualTo(MealDraft())
        assertThat(MealDraft().isEmpty).isTrue()
    }

    @Test
    fun `испорченный черновик — пустой приём`() {
        assertThat(MealDraftJson.decode("не json")).isEqualTo(MealDraft())
        assertThat(MealDraftJson.decode("[1,2]")).isEqualTo(MealDraft())
    }

    @Test
    fun `позиции неизвестного типа пропускаются`() {
        val json = """{"items":[{"key":"a","type":"soup","refId":"x","weight":"1"},""" +
            """{"key":"b","type":"product","refId":"y","weight":"2"}]}"""
        assertThat(MealDraftJson.decode(json).items.map { it.key }).containsExactly("b")
    }

    @Test
    fun `черновик только с сахаром — не пустой`() {
        assertThat(MealDraft(glucose = "6").isEmpty).isFalse()
        assertThat(MealDraftJson.decode(MealDraftJson.encode(MealDraft(dose = "2"))).dose).isEqualTo("2")
    }
}
