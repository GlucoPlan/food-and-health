package com.glucoplan.foodhealth.data.norms

import com.glucoplan.foodhealth.data.profile.Sex
import java.time.LocalDate
import java.time.Period

/** Уровень активности (ТЗ 17.3). [code] хранится в базе, [factor] — множитель к базовому обмену. */
enum class Activity(val code: String, val label: String, val factor: Double) {
    SEDENTARY("sedentary", "Сидячий", 1.2),
    LIGHT("light", "Немного", 1.375),
    MODERATE("moderate", "Умеренно", 1.55),
    HIGH("high", "Много", 1.725);

    companion object {
        fun fromCode(code: String?): Activity = entries.firstOrNull { it.code == code } ?: MODERATE
    }
}

/** Чего не хватает для расчёта. [code] — для общих контрольных примеров с сервером. */
enum class NormMissing(val code: String, val label: String) {
    SEX("sex", "пол"),
    BIRTH_DATE("birth_date", "дата рождения"),
    HEIGHT("height", "рост"),
    WEIGHT("weight", "вес"),
    /** Для детей до года нормы в граммах на кг веса — не считаются. */
    INFANT("infant", "нормы для детей до года не считаются"),
}

/** Исходные данные: профиль, последние рост и вес на нужный день. */
data class NormInput(
    val sex: Sex?,
    val birthDate: LocalDate?,
    val heightCm: Double?,
    val weightKg: Double?,
    val activity: Activity = Activity.MODERATE,
    val targetWeightKg: Double? = null,
    val paceKgPerWeek: Double = NormCalculator.DEFAULT_PACE,
    /** Нормы, введённые вручную; пустые поля — расчёт. */
    val manual: NormSet = NormSet(),
)

/**
 * Нормы на день.
 * [auto] — что будет в поле, если оставить его пустым (показывается подсказкой «расчёт»);
 * [final] — итог с учётом ручных значений, по нему считаются отчёты.
 */
data class DailyNorms(
    val auto: NormSet,
    val final: NormSet,
    val missing: List<NormMissing>,
    val child: Boolean,
    val fiberMinG: Double?,
    val saltMaxG: Double?,
    /** Витамины и минералы по кодам справочника Nutrients; пусто — неизвестны пол или возраст. */
    val micro: Map<String, Double>,
)

/**
 * Расчёт норм (ТЗ 17.3). Взрослым — Миффлин — Сан Жеор × активность с поправкой на цель по весу,
 * БЖУ — 20/30/50 % калорий. Детям — таблица МР, при ручных калориях БЖУ меняются пропорционально.
 * Сервер считает так же: общие контрольные примеры — shared/norm-cases.json.
 */
object NormCalculator {
    const val DEFAULT_PACE = 0.5
    const val KCAL_PER_KG = 7700.0
    const val PROTEIN_SHARE = 0.20
    const val FAT_SHARE = 0.30
    const val CARBS_SHARE = 0.50

    fun calculate(input: NormInput, date: LocalDate): DailyNorms {
        val age = input.birthDate?.let { Period.between(it, date).years }
        val child = age != null && age < NormTables.ADULT_AGE
        val sex = input.sex

        val missing = buildList {
            if (sex == null) add(NormMissing.SEX)
            if (age == null) add(NormMissing.BIRTH_DATE)
            if (!child) {
                if (input.heightCm == null) add(NormMissing.HEIGHT)
                if (input.weightKg == null) add(NormMissing.WEIGHT)
            }
            if (age != null && age < 1) add(NormMissing.INFANT)
        }

        val childTable = if (child && sex != null) NormTables.childMacros(age!!, sex) else null
        val autoKcal = when {
            missing.isNotEmpty() -> null
            child -> childTable?.kcal
            else -> adultKcal(input, sex!!, age!!)
        }
        val kcal = input.manual.kcal ?: autoKcal
        val derived = kcal?.let { macrosFromKcal(it, childTable) } ?: NormSet()
        val auto = NormSet(autoKcal, derived.protein, derived.fat, derived.carbs)

        return DailyNorms(
            auto = auto,
            final = NormSet(
                kcal = kcal,
                protein = input.manual.protein ?: auto.protein,
                fat = input.manual.fat ?: auto.fat,
                carbs = input.manual.carbs ?: auto.carbs,
            ),
            missing = missing,
            child = child,
            fiberMinG = if (child) NormTables.childFiber(age!!) else NormTables.ADULT_FIBER_G,
            saltMaxG = if (child) null else NormTables.ADULT_SALT_MAX_G,
            micro = if (sex != null && age != null) NormTables.micro(age, sex) else emptyMap(),
        )
    }

    /** Базовый обмен по Миффлину — Сан Жеору, ккал. */
    fun bmr(sex: Sex, age: Int, heightCm: Double, weightKg: Double): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + if (sex == Sex.MALE) 5 else -161

    /** Похудение — минус темп, но не ниже базового обмена; набор — плюс темп; без цели — поддержание. */
    private fun adultKcal(input: NormInput, sex: Sex, age: Int): Double {
        val weight = input.weightKg!!
        val bmr = bmr(sex, age, input.heightCm!!, weight)
        val tdee = bmr * input.activity.factor
        val target = input.targetWeightKg ?: return tdee
        val shift = input.paceKgPerWeek * KCAL_PER_KG / 7
        return when {
            target < weight -> maxOf(tdee - shift, bmr)
            target > weight -> tdee + shift
            else -> tdee
        }
    }

    private fun macrosFromKcal(kcal: Double, childTable: NormSet?): NormSet =
        if (childTable != null) {
            val k = kcal / childTable.kcal!!
            NormSet(kcal, childTable.protein!! * k, childTable.fat!! * k, childTable.carbs!! * k)
        } else {
            NormSet(kcal, kcal * PROTEIN_SHARE / 4, kcal * FAT_SHARE / 9, kcal * CARBS_SHARE / 4)
        }
}
