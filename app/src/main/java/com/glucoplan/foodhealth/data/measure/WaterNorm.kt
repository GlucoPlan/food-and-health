package com.glucoplan.foodhealth.data.measure

import com.glucoplan.foodhealth.data.NumberText
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Вода за день и дневная норма (ТЗ 15.3, 15.4). */
object WaterNorm {
    /** Объём по умолчанию, пока записей нет. */
    const val DEFAULT_ML = 250

    /** Норма = мл на 1 кг × последний вес; без веса — нормы нет. */
    fun dailyNormMl(mlPerKg: Double, lastWeightKg: Double?): Int? = lastWeightKg?.let { (mlPerKg * it).roundToInt() }

    /** Начало и конец местных суток, в которые попадает [now]: «сегодня» — с полуночи до полуночи. */
    fun dayBounds(now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return day.atStartOfDay(zone).toInstant().toEpochMilli() to
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** 1450 → «1,45». */
    fun litres(ml: Int): String = NumberText.format(ml / 1000.0, 2)

    /** «сегодня 1,45 из 2,5 л» или «сегодня 1,45 л». */
    fun today(todayMl: Int, normMl: Int?): String =
        "сегодня ${litres(todayMl)}" + (normMl?.let { " из ${litres(it)} л" } ?: " л")
}
