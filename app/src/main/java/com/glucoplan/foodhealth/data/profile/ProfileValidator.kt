package com.glucoplan.foodhealth.data.profile

import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.norms.Activity
import com.glucoplan.foodhealth.data.norms.NormCalculator
import com.glucoplan.foodhealth.data.norms.NormSet
import com.glucoplan.foodhealth.data.norms.NormTables
import java.time.LocalDate
import java.time.Period
import java.util.Locale

/** Пол (ТЗ 15.3). [code] хранится в базе. */
enum class Sex(val code: String, val label: String) {
    MALE("male", "Мужской"),
    FEMALE("female", "Женский");

    companion object {
        fun fromCode(code: String?): Sex? = entries.firstOrNull { it.code == code }
    }
}

/** Профиль без служебных полей синхронизации. */
data class Profile(
    val id: String,
    val name: String,
    val sd1Enabled: Boolean,
    val showXe: Boolean,
    val carbsPerXe: Double,
    /** Пол и дата рождения обязательны в редакторе, но у старых профилей могут быть пустыми. */
    val sex: Sex? = null,
    val birthDate: LocalDate? = null,
    val waterEnabled: Boolean = false,
    val waterMlPerKg: Double = ProfileValidator.DEFAULT_WATER_ML_PER_KG,
    // ТЗ 17.3: нормы
    val activity: Activity = Activity.MODERATE,
    val targetWeightKg: Double? = null,
    val weightPaceKg: Double = NormCalculator.DEFAULT_PACE,
    /** Нормы, введённые вручную; пустые поля — расчёт. */
    val manualNorms: NormSet = NormSet(),
    val glucoseLow: Double? = null,
    val glucoseHigh: Double? = null,
) {
    /** Не указаны пол или дата рождения — профиль нужно дополнить. */
    val incomplete: Boolean get() = sex == null || birthDate == null

    /** Полных лет на [today]; null — дата рождения не указана. */
    fun ageYears(today: LocalDate = LocalDate.now()): Int? = birthDate?.let { Period.between(it, today).years }
}

/** То, что пользователь ввёл в редакторе профиля. */
data class ProfileForm(
    val name: String,
    val sd1Enabled: Boolean = false,
    val showXe: Boolean = false,
    val carbsPerXe: String = ProfileValidator.formatCarbs(ProfileValidator.DEFAULT_CARBS_PER_XE),
    val sex: Sex? = null,
    val birthDate: LocalDate? = null,
    val waterEnabled: Boolean = false,
    val waterMlPerKg: String = ProfileValidator.formatCarbs(ProfileValidator.DEFAULT_WATER_ML_PER_KG),
    val activity: Activity = Activity.MODERATE,
    /** Пусто — цели нет, поддержание. */
    val targetWeightKg: String = "",
    val weightPaceKg: String = ProfileValidator.formatCarbs(NormCalculator.DEFAULT_PACE),
    /** Пустые нормы — расчёт. */
    val normKcal: String = "",
    val normProtein: String = "",
    val normFat: String = "",
    val normCarbs: String = "",
    val glucoseLow: String = "",
    val glucoseHigh: String = "",
)

sealed interface ProfileValidation {
    data class Valid(
        val name: String,
        val carbsPerXe: Double,
        val waterMlPerKg: Double = ProfileValidator.DEFAULT_WATER_ML_PER_KG,
        val targetWeightKg: Double? = null,
        val weightPaceKg: Double = NormCalculator.DEFAULT_PACE,
        val manualNorms: NormSet = NormSet(),
        val glucoseLow: Double? = null,
        val glucoseHigh: Double? = null,
    ) : ProfileValidation

    data class Invalid(
        val nameError: String?,
        val carbsError: String?,
        val sexError: String? = null,
        val birthDateError: String? = null,
        val waterError: String? = null,
        val targetError: String? = null,
        val paceError: String? = null,
        val kcalError: String? = null,
        val proteinError: String? = null,
        val fatError: String? = null,
        val normCarbsError: String? = null,
        val glucoseError: String? = null,
    ) : ProfileValidation
}

object ProfileValidator {
    const val DEFAULT_CARBS_PER_XE = 10.0
    const val MIN_CARBS_PER_XE = 5.0
    const val MAX_CARBS_PER_XE = 25.0

    /** ТЗ 15.3: норма воды, мл на 1 кг веса. */
    const val DEFAULT_WATER_ML_PER_KG = 30.0
    const val MIN_WATER_ML_PER_KG = 10.0
    const val MAX_WATER_ML_PER_KG = 100.0

    val MIN_BIRTH_DATE: LocalDate = LocalDate.of(1900, 1, 1)

    /** ТЗ 17.3: границы от опечаток. */
    val TARGET_WEIGHT_KG = 30.0..300.0
    val PACE_KG = 0.1..1.5
    val NORM_KCAL = 800.0..5000.0
    val NORM_GRAMS = 10.0..800.0
    val GLUCOSE_LOW = 2.0..10.0
    val GLUCOSE_HIGH = 4.0..20.0
    /** Подставляется при включении «Дневника СД1»: стандартный «в диапазоне» для CGM. */
    const val DEFAULT_GLUCOSE_LOW = 3.9
    const val DEFAULT_GLUCOSE_HIGH = 10.0

    /**
     * [otherNames] — имена остальных профилей (без редактируемого).
     * Граммы в ХЕ проверяются, только если показ ХЕ включён; иначе остаётся [currentCarbs].
     * Норма воды — только если вода включена; иначе остаётся [currentWater].
     * Цель по весу — только у взрослых (у детей её поле скрыто); иначе остаются [currentTarget] и [currentPace].
     * Темп — только если задан целевой вес. Диапазон сахара — только при «Дневнике СД1»; иначе остаётся [currentGlucose].
     */
    fun validate(
        form: ProfileForm,
        otherNames: Collection<String>,
        currentCarbs: Double = DEFAULT_CARBS_PER_XE,
        currentWater: Double = DEFAULT_WATER_ML_PER_KG,
        today: LocalDate = LocalDate.now(),
        currentTarget: Double? = null,
        currentPace: Double = NormCalculator.DEFAULT_PACE,
        currentGlucose: Pair<Double?, Double?> = null to null,
    ): ProfileValidation {
        val name = form.name.trim()
        val nameError = when {
            name.isEmpty() -> "Введите имя"
            otherNames.any { normalize(it) == normalize(name) } -> "Такой профиль уже есть"
            else -> null
        }

        var carbs = currentCarbs
        var carbsError: String? = null
        if (form.showXe) {
            val parsed = parseNumber(form.carbsPerXe)
            if (parsed == null || parsed < MIN_CARBS_PER_XE || parsed > MAX_CARBS_PER_XE) {
                carbsError = "Число от ${formatCarbs(MIN_CARBS_PER_XE)} до ${formatCarbs(MAX_CARBS_PER_XE)}"
            } else {
                carbs = parsed
            }
        }

        val sexError = if (form.sex == null) "Укажите пол" else null
        val birthDateError = when (val birth = form.birthDate) {
            null -> "Укажите дату рождения"
            else -> if (birth.isAfter(today) || birth.isBefore(MIN_BIRTH_DATE)) "Проверьте дату рождения" else null
        }

        var water = currentWater
        var waterError: String? = null
        if (form.waterEnabled) {
            val parsed = parseNumber(form.waterMlPerKg)
            if (parsed == null || parsed < MIN_WATER_ML_PER_KG || parsed > MAX_WATER_ML_PER_KG) {
                waterError = "Число от ${formatCarbs(MIN_WATER_ML_PER_KG)} до ${formatCarbs(MAX_WATER_ML_PER_KG)}"
            } else {
                water = parsed
            }
        }

        val adult = form.birthDate?.let { Period.between(it, today).years >= NormTables.ADULT_AGE } ?: true
        var target = currentTarget
        var pace = currentPace
        var targetError: String? = null
        var paceError: String? = null
        if (adult) {
            val t = optional(form.targetWeightKg, TARGET_WEIGHT_KG)
            targetError = t.error
            target = t.value
            if (target != null || targetError != null) {
                val p = optional(form.weightPaceKg, PACE_KG)
                paceError = p.error ?: if (p.value == null) rangeText(PACE_KG) else null
                if (p.value != null) pace = p.value
            }
        }

        val kcal = optional(form.normKcal, NORM_KCAL)
        val protein = optional(form.normProtein, NORM_GRAMS)
        val fat = optional(form.normFat, NORM_GRAMS)
        val normCarbs = optional(form.normCarbs, NORM_GRAMS)

        var glucose = currentGlucose
        var glucoseError: String? = null
        if (form.sd1Enabled) {
            val low = form.glucoseLow.isNotBlank()
            val high = form.glucoseHigh.isNotBlank()
            if (!low && !high) {
                glucose = null to null
            } else {
                val l = parseNumber(form.glucoseLow)?.takeIf { it in GLUCOSE_LOW }
                val h = parseNumber(form.glucoseHigh)?.takeIf { it in GLUCOSE_HIGH }
                glucoseError = when {
                    !low || !high -> "Укажите обе границы"
                    l == null || h == null ->
                        "«От» — от ${formatCarbs(GLUCOSE_LOW.start)} до ${formatCarbs(GLUCOSE_LOW.endInclusive)}, " +
                            "«до» — от ${formatCarbs(GLUCOSE_HIGH.start)} до ${formatCarbs(GLUCOSE_HIGH.endInclusive)}"
                    l >= h -> "«От» должно быть меньше «до»"
                    else -> null
                }
                if (glucoseError == null) glucose = l to h
            }
        }

        val errors = listOf(
            nameError, carbsError, sexError, birthDateError, waterError, targetError, paceError,
            kcal.error, protein.error, fat.error, normCarbs.error, glucoseError,
        )
        return if (errors.all { it == null }) {
            ProfileValidation.Valid(
                name, carbs, water,
                targetWeightKg = target,
                weightPaceKg = pace,
                manualNorms = NormSet(kcal.value, protein.value, fat.value, normCarbs.value),
                glucoseLow = glucose.first,
                glucoseHigh = glucose.second,
            )
        } else {
            ProfileValidation.Invalid(
                nameError, carbsError, sexError, birthDateError, waterError,
                targetError = targetError,
                paceError = paceError,
                kcalError = kcal.error,
                proteinError = protein.error,
                fatError = fat.error,
                normCarbsError = normCarbs.error,
                glucoseError = glucoseError,
            )
        }
    }

    private class Optional(val value: Double?, val error: String?)

    /** Пусто — null без ошибки; иначе число в [range]. */
    private fun optional(text: String, range: ClosedFloatingPointRange<Double>): Optional {
        if (text.isBlank()) return Optional(null, null)
        val parsed = parseNumber(text)
        return if (parsed == null || parsed !in range) Optional(null, rangeText(range)) else Optional(parsed, null)
    }

    private fun rangeText(range: ClosedFloatingPointRange<Double>) =
        "Число от ${formatCarbs(range.start)} до ${formatCarbs(range.endInclusive)}"

    /** 10.0 → "10", 12.5 → "12,5". */
    fun formatCarbs(value: Double): String = NumberText.format(value)

    /** «1 год», «3 года», «9 лет», «11 лет». */
    fun ageText(years: Int): String {
        val word = when {
            years % 100 in 11..14 -> "лет"
            years % 10 == 1 -> "год"
            years % 10 in 2..4 -> "года"
            else -> "лет"
        }
        return "$years $word"
    }

    private fun parseNumber(text: String): Double? = NumberText.parse(text)

    private fun normalize(name: String) = name.trim().lowercase(Locale.forLanguageTag("ru"))
}
