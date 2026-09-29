package com.glucoplan.foodhealth.data.meal

import com.glucoplan.foodhealth.data.db.ItemUsage

/** Позиция в недавних: продукт (id продукта) или блюдо (id блюда, не варки). */
data class RecentKey(val type: MealItemType, val id: String)

enum class RecentKind { RECENT, FREQUENT }

data class RecentEntry(val key: RecentKey, val kind: RecentKind)

/** Недавние и частые позиции профиля — в начале выбора без ввода текста (ТЗ 4.1). */
object RecentItems {
    const val RECENT_COUNT = 5
    const val FREQUENT_COUNT = 10
    const val FREQUENT_DAYS = 30
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * [usages] — что профиль ел, новые сверху. Блюдо считается одной позицией для всех
     * варок ([dishOfVersion]). [isAvailable] отсеивает удалённое (ТЗ 5.5).
     */
    fun pick(
        usages: List<ItemUsage>,
        dishOfVersion: (String) -> String?,
        isAvailable: (RecentKey) -> Boolean,
        now: Long,
    ): List<RecentEntry> {
        fun keyOf(u: ItemUsage): RecentKey? = when (MealItemType.fromCode(u.type)) {
            MealItemType.PRODUCT -> u.productId?.let { RecentKey(MealItemType.PRODUCT, it) }
            MealItemType.DISH -> u.dishVersionId?.let(dishOfVersion)?.let { RecentKey(MealItemType.DISH, it) }
            null -> null
        }

        val keyed = usages.mapNotNull { u -> keyOf(u)?.takeIf(isAvailable)?.let { it to u.eatenAt } }

        val recent = keyed.map { it.first }.distinct().take(RECENT_COUNT)

        val since = now - FREQUENT_DAYS * DAY_MS
        val frequent = keyed.filter { it.second >= since }
            .groupBy { it.first }
            .map { (key, uses) -> Triple(key, uses.size, uses.maxOf { it.second }) }
            .filter { it.first !in recent }
            .sortedWith(compareByDescending<Triple<RecentKey, Int, Long>> { it.second }.thenByDescending { it.third })
            .take(FREQUENT_COUNT)
            .map { it.first }

        return recent.map { RecentEntry(it, RecentKind.RECENT) } + frequent.map { RecentEntry(it, RecentKind.FREQUENT) }
    }
}
