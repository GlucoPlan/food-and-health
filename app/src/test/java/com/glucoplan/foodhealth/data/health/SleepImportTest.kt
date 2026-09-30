package com.glucoplan.foodhealth.data.health

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Правила импорта сна из Health Connect (ТЗ 15.5). */
class SleepImportTest {

    private val min = 60_000L
    private val h = 60 * min

    private fun s(id: String, startH: Double, endH: Double) = SleepSession(id, (startH * h).toLong(), (endH * h).toLong())

    @Test
    fun `сессии с перерывом меньше часа — одна ночь, id первой`() {
        val nights = SleepImport.nights(listOf(s("b", 3.5, 7.0), s("a", 0.0, 3.0)))
        assertThat(nights).containsExactly(ImportNight("a", 0, 7 * h))
    }

    @Test
    fun `перерыв час и больше — разные ночи`() {
        val nights = SleepImport.nights(listOf(s("a", 0.0, 3.0), s("b", 4.0, 8.0)))
        assertThat(nights.map { it.externalId }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `короче часа — не берём, в том числе дневной сон`() {
        val nights = SleepImport.nights(listOf(s("nap", 13.0, 13.75), s("night", 23.0, 31.0)))
        assertThat(nights.map { it.externalId }).containsExactly("night")
    }

    @Test
    fun `ровно час берём, больше 20 часов — нет`() {
        assertThat(SleepImport.nights(listOf(s("a", 0.0, 1.0)))).hasSize(1)
        assertThat(SleepImport.nights(listOf(s("a", 0.0, 21.0)))).isEmpty()
    }

    @Test
    fun `пустые и перевёрнутые сессии пропускаются`() {
        assertThat(SleepImport.nights(listOf(s("x", 5.0, 5.0), s("y", 7.0, 6.0)))).isEmpty()
    }

    private val night = ImportNight("hc-1", 0, 8 * h)

    @Test
    fun `уже импортированная не повторяется — даже исправленная или удалённая`() {
        val edited = ExistingSleep("hc-1", h, 9 * h, deleted = false)
        val deleted = ExistingSleep("hc-1", 0, 8 * h, deleted = true)
        assertThat(SleepImport.toImport(listOf(night), listOf(edited))).isEmpty()
        assertThat(SleepImport.toImport(listOf(night), listOf(deleted))).isEmpty()
    }

    @Test
    fun `на эту ночь уже есть сон — пропуск, соседние ночи не мешают`() {
        val manual = ExistingSleep(null, 30 * min, 7 * h, deleted = false)
        assertThat(SleepImport.toImport(listOf(night), listOf(manual))).isEmpty()
        val yesterday = ExistingSleep(null, -24 * h, -16 * h, deleted = false)
        assertThat(SleepImport.toImport(listOf(night), listOf(yesterday))).containsExactly(night)
        // Удалённая ручная запись ночь не занимает
        val removed = ExistingSleep(null, 30 * min, 7 * h, deleted = true)
        assertThat(SleepImport.toImport(listOf(night), listOf(removed))).containsExactly(night)
    }

    @Test
    fun `стык без пересечения — не мешает`() {
        val before = ExistingSleep(null, -8 * h, 0, deleted = false)
        assertThat(SleepImport.toImport(listOf(night), listOf(before))).containsExactly(night)
    }
}
