package com.glucoplan.foodhealth.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NumberTextTest {

    @Test
    fun `разбор с запятой и точкой`() {
        assertThat(NumberText.parse("12,5")).isEqualTo(12.5)
        assertThat(NumberText.parse(" 12.5 ")).isEqualTo(12.5)
        assertThat(NumberText.parse("")).isNull()
        assertThat(NumberText.parse("abc")).isNull()
        assertThat(NumberText.parse("NaN")).isNull()
    }

    @Test
    fun `форматирование без лишних нулей и с запятой`() {
        assertThat(NumberText.format(10.0)).isEqualTo("10")
        assertThat(NumberText.format(12.5)).isEqualTo("12,5")
        assertThat(NumberText.format(1.2345)).isEqualTo("1,23")
        assertThat(NumberText.format(249.6, maxFraction = 0)).isEqualTo("250")
        assertThat(NumberText.format(0.04, maxFraction = 1)).isEqualTo("0")
    }
}
