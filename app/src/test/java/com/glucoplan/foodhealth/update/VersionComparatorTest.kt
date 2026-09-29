package com.glucoplan.foodhealth.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `новый патч новее`() {
        assertThat(VersionComparator.isNewer("0.1.2", "0.1.1")).isTrue()
    }

    @Test
    fun `числа сравниваются как числа, а не как строки`() {
        assertThat(VersionComparator.isNewer("0.1.10", "0.1.9")).isTrue()
        assertThat(VersionComparator.isNewer("0.1.9", "0.1.10")).isFalse()
    }

    @Test
    fun `старший разряд важнее младших`() {
        assertThat(VersionComparator.isNewer("1.0.0", "0.99.999")).isTrue()
        assertThat(VersionComparator.isNewer("0.2.0", "0.1.500")).isTrue()
        assertThat(VersionComparator.isNewer("0.1.500", "0.2.0")).isFalse()
    }

    @Test
    fun `одинаковая версия не обновление`() {
        assertThat(VersionComparator.isNewer("0.1.5", "0.1.5")).isFalse()
        assertThat(VersionComparator.isNewer("v0.1.5", "0.1.5")).isFalse()
    }

    @Test
    fun `старая версия не обновление`() {
        assertThat(VersionComparator.isNewer("0.1.4", "0.1.5")).isFalse()
    }

    @Test
    fun `префикс v у тега и пробелы допускаются`() {
        assertThat(VersionComparator.isNewer("v0.1.6", "0.1.5")).isTrue()
        assertThat(VersionComparator.isNewer(" V0.1.6 ", "0.1.5")).isTrue()
    }

    @Test
    fun `разная длина дополняется нулями`() {
        assertThat(VersionComparator.isNewer("0.2", "0.1.9")).isTrue()
        assertThat(VersionComparator.isNewer("0.2", "0.2.0")).isFalse()
        assertThat(VersionComparator.isNewer("0.2.0.1", "0.2.0")).isTrue()
    }

    @Test
    fun `нераспознанная версия не обновление`() {
        assertThat(VersionComparator.isNewer("", "0.1.0")).isFalse()
        assertThat(VersionComparator.isNewer("latest", "0.1.0")).isFalse()
        assertThat(VersionComparator.isNewer("0.2.0-beta", "0.1.0")).isFalse()
        assertThat(VersionComparator.isNewer("0..2", "0.1.0")).isFalse()
        assertThat(VersionComparator.isNewer("0.2.0", "мусор")).isFalse()
    }

    @Test
    fun `разбор версии`() {
        assertThat(VersionComparator.parse("v1.2.3")).containsExactly(1, 2, 3).inOrder()
        assertThat(VersionComparator.parse("-1.0")).isNull()
    }
}
