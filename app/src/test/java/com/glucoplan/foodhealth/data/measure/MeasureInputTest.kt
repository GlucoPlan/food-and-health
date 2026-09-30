package com.glucoplan.foodhealth.data.measure

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MeasureInputTest {

    @Test
    fun `число с запятой и точкой, округление`() {
        assertThat(MeasureInput.number("84,2", 2.0, 300.0, 1)).isEqualTo(84.2)
        assertThat(MeasureInput.number(" 84.25 ", 2.0, 300.0, 1)).isEqualTo(84.3)
        assertThat(MeasureInput.number("84,24", 2.0, 300.0, 1)).isEqualTo(84.2)
        assertThat(MeasureInput.number("120", 50.0, 260.0, 0)).isEqualTo(120.0)
    }

    @Test
    fun `границы включительно, вне — null`() {
        assertThat(MeasureInput.number("2", 2.0, 300.0, 1)).isEqualTo(2.0)
        assertThat(MeasureInput.number("300", 2.0, 300.0, 1)).isEqualTo(300.0)
        assertThat(MeasureInput.number("1,9", 2.0, 300.0, 1)).isNull()
        assertThat(MeasureInput.number("300,1", 2.0, 300.0, 1)).isNull()
        assertThat(MeasureInput.number("", 2.0, 300.0, 1)).isNull()
        assertThat(MeasureInput.number("восемьдесят", 2.0, 300.0, 1)).isNull()
    }

    @Test
    fun `время в будущем — ошибка, небольшое расхождение часов — нет`() {
        val now = 1_000_000_000L
        assertThat(MeasureInput.timeError(now - 3_600_000, now)).isNull()
        assertThat(MeasureInput.timeError(now + 30_000, now)).isNull()
        assertThat(MeasureInput.timeError(now + 3_600_000, now)).isNotNull()
    }
}
