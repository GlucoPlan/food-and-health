package com.glucoplan.foodhealth.data.meal

import com.glucoplan.foodhealth.data.db.ItemUsage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentItemsTest {

    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    private fun product(id: String, daysAgo: Double) = ItemUsage("product", id, null, now - (daysAgo * day).toLong())
    private fun dish(versionId: String, daysAgo: Double) = ItemUsage("dish", null, versionId, now - (daysAgo * day).toLong())

    private fun pick(usages: List<ItemUsage>, available: (RecentKey) -> Boolean = { true }) = RecentItems.pick(
        usages = usages.sortedByDescending { it.eatenAt },
        dishOfVersion = { v -> "суп".takeIf { v.startsWith("суп") } },
        isAvailable = available,
        now = now,
    )

    private fun p(id: String) = RecentKey(MealItemType.PRODUCT, id)

    @Test
    fun `недавние — последние разные, новые первыми`() {
        val result = pick(listOf(product("a", 0.1), product("b", 0.2), product("a", 0.3), product("c", 0.4)))
        assertThat(result.filter { it.kind == RecentKind.RECENT }.map { it.key })
            .containsExactly(p("a"), p("b"), p("c")).inOrder()
    }

    @Test
    fun `недавних не больше пяти, остальное — в частых`() {
        val usages = (1..7).map { product("p$it", it * 0.1) } + product("p7", 1.0) + product("p7", 2.0)
        val result = pick(usages)
        val recent = result.filter { it.kind == RecentKind.RECENT }
        assertThat(recent).hasSize(5)
        assertThat(result.filter { it.kind == RecentKind.FREQUENT }.map { it.key }.first()).isEqualTo(p("p7"))
    }

    @Test
    fun `частые — по числу раз за 30 дней, без повторов с недавними`() {
        val usages = (1..5).map { product("r$it", it * 0.01) } +
            List(3) { product("often", 5.0 + it) } +
            List(2) { product("sometimes", 6.0 + it) } +
            List(10) { product("old", 40.0 + it) }
        val frequent = pick(usages).filter { it.kind == RecentKind.FREQUENT }.map { it.key }
        assertThat(frequent).containsExactly(p("often"), p("sometimes")).inOrder()
    }

    @Test
    fun `все варки блюда — одна позиция`() {
        val result = pick(listOf(dish("суп-2", 0.1), dish("суп-1", 3.0)))
        assertThat(result.map { it.key }).containsExactly(RecentKey(MealItemType.DISH, "суп"))
    }

    @Test
    fun `удалённое не показывается`() {
        val result = pick(listOf(product("a", 0.1), product("gone", 0.2))) { it.id != "gone" }
        assertThat(result.map { it.key }).containsExactly(p("a"))
    }

    @Test
    fun `частых не больше десяти`() {
        val usages = (1..5).map { product("r$it", it * 0.01) } +
            (1..15).flatMap { i -> List(2) { product("f$i", 1.0 + i) } }
        assertThat(pick(usages).count { it.kind == RecentKind.FREQUENT }).isEqualTo(10)
    }
}
