package com.glucoplan.foodhealth.data.meal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Sd1Test {

    @Test
    fun `ХЕ — углеводы делённые на граммы в ХЕ`() {
        assertThat(Sd1.xe(45.0, 12.0)).isWithin(1e-9).of(3.75)
        assertThat(Sd1.xe(45.0, 10.0)).isWithin(1e-9).of(4.5)
        assertThat(Sd1.xe(45.0, 0.0)).isNull()
    }

    @Test
    fun `пустые сахар и доза допустимы`() {
        assertThat(Sd1.validate("", " ")).isEmpty()
        assertThat(Sd1.value("")).isNull()
    }

    @Test
    fun `сахар от 1 до 35, с запятой`() {
        assertThat(Sd1.validate("1", "")).isEmpty()
        assertThat(Sd1.validate("35", "")).isEmpty()
        assertThat(Sd1.validate("6,5", "")).isEmpty()
        assertThat(Sd1.value("6,5")).isEqualTo(6.5)
        listOf("0,9", "35,1", "abc").forEach {
            assertThat(Sd1.validate(it, "")).containsKey(MealField.GLUCOSE)
        }
    }

    @Test
    fun `доза от 0 до 50`() {
        assertThat(Sd1.validate("", "0")).isEmpty()
        assertThat(Sd1.validate("", "50")).isEmpty()
        assertThat(Sd1.validate("", "0.25")).isEmpty()
        listOf("-1", "50,5", "много").forEach {
            assertThat(Sd1.validate("", it)).containsKey(MealField.DOSE)
        }
    }
}
