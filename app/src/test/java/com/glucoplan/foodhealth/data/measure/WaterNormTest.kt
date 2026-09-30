package com.glucoplan.foodhealth.data.measure

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class WaterNormTest {

    private val zone = ZoneOffset.ofHours(3)
    private fun at(d: Int, h: Int, m: Int = 0) =
        LocalDateTime.of(2026, 9, d, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `норма — мл на кг на последний вес, без веса нормы нет`() {
        assertThat(WaterNorm.dailyNormMl(30.0, 84.2)).isEqualTo(2526)
        assertThat(WaterNorm.dailyNormMl(35.0, 31.0)).isEqualTo(1085)
        assertThat(WaterNorm.dailyNormMl(30.0, null)).isNull()
    }

    @Test
    fun `сегодня — с местной полуночи до полуночи`() {
        val (from, to) = WaterNorm.dayBounds(at(30, 14), zone)
        assertThat(from).isEqualTo(at(30, 0))
        assertThat(to).isEqualTo(at(30, 0) + 24 * 3_600_000L)
        assertThat(at(29, 23, 59)).isLessThan(from)
        assertThat(at(30, 0, 1)).isAtLeast(from)
    }

    @Test
    fun `литры и итог за день`() {
        assertThat(WaterNorm.litres(1450)).isEqualTo("1,45")
        assertThat(WaterNorm.litres(2000)).isEqualTo("2")
        assertThat(WaterNorm.today(1450, 2526)).isEqualTo("сегодня 1,45 из 2,53 л")
        assertThat(WaterNorm.today(250, null)).isEqualTo("сегодня 0,25 л")
    }
}
