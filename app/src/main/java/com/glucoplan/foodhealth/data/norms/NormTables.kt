package com.glucoplan.foodhealth.data.norms

import com.glucoplan.foodhealth.data.profile.Sex

/** Калории и БЖУ в день; поля пустые, если значение неизвестно. */
data class NormSet(
    val kcal: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null,
)

/**
 * Нормы из МР 2.3.1.0253-21 (ТЗ 17.3).
 * Дети — таблица 21 (возрастные группы 1–2, 3–6, 7–10, 11–14, 15–17 лет; с 11 лет по полу).
 * Взрослые — таблицы 11, 12 (мужчины) и 16, 17 (женщины).
 * В таблице 21 часть ячеек объединена на несколько групп; значения разнесены по группам в порядке следования.
 */
object NormTables {

    private class ChildGroup(
        val ages: IntRange,
        val male: NormSet,
        val female: NormSet,
        val fiber: Double,
    )

    private val childGroups = listOf(
        ChildGroup(1..2, NormSet(1300.0, 39.0, 44.0, 188.0), NormSet(1300.0, 39.0, 44.0, 188.0), 10.0),
        ChildGroup(3..6, NormSet(1800.0, 54.0, 60.0, 261.0), NormSet(1800.0, 54.0, 60.0, 261.0), 12.0),
        ChildGroup(7..10, NormSet(2100.0, 63.0, 70.0, 305.0), NormSet(2100.0, 63.0, 70.0, 305.0), 16.0),
        ChildGroup(11..14, NormSet(2500.0, 75.0, 83.0, 363.0), NormSet(2300.0, 69.0, 77.0, 334.0), 20.0),
        ChildGroup(15..17, NormSet(2900.0, 87.0, 97.0, 421.0), NormSet(2500.0, 75.0, 83.0, 363.0), 22.0),
    )

    /** Нутриент по группам 1–2, 3–6, 7–10, 11–14, 15–17 лет: мальчики и девочки. */
    private class ChildMicro(val male: List<Double>, val female: List<Double>)

    private fun mf(male: List<Number>, female: List<Number> = male) =
        ChildMicro(male.map { it.toDouble() }, female.map { it.toDouble() })

    private val childMicro = mapOf(
        "vit_a" to mf(listOf(450, 500, 700, 1000, 1000), listOf(450, 500, 700, 800, 800)),
        "vit_c" to mf(listOf(45, 50, 60, 70, 90), listOf(45, 50, 60, 60, 70)),
        "vit_d" to mf(listOf(15, 15, 15, 15, 15)),
        "vit_e" to mf(listOf(4, 7, 10, 12, 15)),
        "vit_k" to mf(listOf(30, 55, 60, 80, 120), listOf(30, 55, 60, 70, 100)),
        "vit_b1" to mf(listOf(0.8, 0.9, 1.1, 1.3, 1.5), listOf(0.8, 0.9, 1.1, 1.3, 1.3)),
        "vit_b2" to mf(listOf(0.9, 1.0, 1.2, 1.5, 1.8), listOf(0.9, 1.0, 1.2, 1.5, 1.5)),
        "vit_b6" to mf(listOf(0.9, 1.2, 1.5, 1.7, 2.0), listOf(0.9, 1.2, 1.5, 1.6, 1.6)),
        "vit_b9" to mf(listOf(100, 200, 300, 350, 400)),
        "vit_b12" to mf(listOf(0.7, 1.5, 2.0, 3.0, 3.0)),
        "ca" to mf(listOf(800, 900, 1100, 1200, 1200)),
        "fe" to mf(listOf(10, 10, 12, 12, 15), listOf(10, 10, 12, 15, 18)),
        "mg" to mf(listOf(80, 200, 250, 300, 400)),
        "k" to mf(listOf(1000, 1500, 2000, 2500, 3200)),
        "na" to mf(listOf(500, 700, 1000, 1100, 1300)),
        "zn" to mf(listOf(5, 8, 10, 12, 12)),
        "p" to mf(listOf(600, 700, 800, 900, 900)),
        "i" to mf(listOf(90, 90, 130, 150, 150)),
    )

    /** Взрослые: мужчины; у женщин отличаются витамин A и железо. */
    private val adultMale = mapOf(
        "vit_a" to 900.0, "vit_c" to 100.0, "vit_d" to 15.0, "vit_e" to 15.0, "vit_k" to 120.0,
        "vit_b1" to 1.5, "vit_b2" to 1.8, "vit_b6" to 2.0, "vit_b9" to 400.0, "vit_b12" to 3.0,
        "ca" to 1000.0, "fe" to 10.0, "mg" to 420.0, "k" to 3500.0, "na" to 1300.0,
        "zn" to 12.0, "p" to 700.0, "i" to 150.0,
    )
    private val adultFemale = adultMale + mapOf("vit_a" to 800.0, "fe" to 18.0)

    /** Старше 65 лет: витамин D 20 мкг, кальций 1200 мг (примечания к таблицам 11, 12, 16, 17). */
    private val senior = mapOf("vit_d" to 20.0, "ca" to 1200.0)

    const val ADULT_AGE = 18
    const val ADULT_FIBER_G = 25.0
    const val ADULT_SALT_MAX_G = 5.0

    private fun groupIndex(age: Int) = childGroups.indexOfFirst { age in it.ages }

    /** Калории и БЖУ ребёнка 1–17 лет; null — возраст вне таблицы. */
    fun childMacros(age: Int, sex: Sex): NormSet? =
        childGroups.getOrNull(groupIndex(age))?.let { if (sex == Sex.MALE) it.male else it.female }

    /** Клетчатка ребёнка 1–17 лет, г. */
    fun childFiber(age: Int): Double? = childGroups.getOrNull(groupIndex(age))?.fiber

    /** Витамины и минералы по коду справочника Nutrients; пусто — для возраста норм нет (до года). */
    fun micro(age: Int, sex: Sex): Map<String, Double> {
        if (age >= ADULT_AGE) {
            val base = if (sex == Sex.MALE) adultMale else adultFemale
            return if (age > 65) base + senior else base
        }
        val i = groupIndex(age)
        if (i < 0) return emptyMap()
        return childMicro.mapValues { (_, v) -> if (sex == Sex.MALE) v.male[i] else v.female[i] }
    }
}
