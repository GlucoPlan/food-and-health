package com.glucoplan.foodhealth.data.measure

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class SleepTimeTest {

    private val zone: ZoneId = ZoneOffset.ofHours(3)
    private val day = LocalDate.of(2026, 9, 30)

    private fun at(date: LocalDate, h: Int, m: Int) = LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `заснул до полуночи — накануне`() {
        val (asleep, woke) = SleepTime.moments(day, LocalTime.of(7, 15), LocalTime.of(23, 40), zone)
        assertThat(asleep).isEqualTo(at(day.minusDays(1), 23, 40))
        assertThat(woke).isEqualTo(at(day, 7, 15))
        assertThat(SleepTime.minutes(asleep, woke)).isEqualTo(7 * 60 + 35)
    }

    @Test
    fun `заснул после полуночи — в тот же день`() {
        val (asleep, woke) = SleepTime.moments(day, LocalTime.of(7, 15), LocalTime.of(0, 30), zone)
        assertThat(asleep).isEqualTo(at(day, 0, 30))
        assertThat(SleepTime.minutes(asleep, woke)).isEqualTo(6 * 60 + 45)
    }

    @Test
    fun `одинаковое время — сутки, а не ноль`() {
        val (asleep, woke) = SleepTime.moments(day, LocalTime.of(7, 0), LocalTime.of(7, 0), zone)
        assertThat(SleepTime.minutes(asleep, woke)).isEqualTo(24 * 60)
    }

    @Test
    fun `длительность словами`() {
        assertThat(SleepTime.durationText(455)).isEqualTo("7 ч 35 мин")
        assertThat(SleepTime.durationText(45)).isEqualTo("45 мин")
        assertThat(SleepTime.durationText(480)).isEqualTo("8 ч")
    }

    @Test
    fun `время засыпания по умолчанию — как в прошлый раз, без записей 23-00`() {
        assertThat(SleepTime.defaultAsleep(null, zone)).isEqualTo(LocalTime.of(23, 0))
        assertThat(SleepTime.defaultAsleep(at(day, 23, 47), zone)).isEqualTo(LocalTime.of(23, 47))
    }

    @Test
    fun `оценка по коду`() {
        assertThat(SleepQuality.fromCode("good")).isEqualTo(SleepQuality.GOOD)
        assertThat(SleepQuality.fromCode(null)).isNull()
        assertThat(SleepQuality.fromCode("чудесно")).isNull()
    }
}
