package com.glucoplan.foodhealth.data.profile

import com.glucoplan.foodhealth.data.NumberText
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
)

sealed interface ProfileValidation {
    data class Valid(
        val name: String,
        val carbsPerXe: Double,
        val waterMlPerKg: Double = ProfileValidator.DEFAULT_WATER_ML_PER_KG,
    ) : ProfileValidation

    data class Invalid(
        val nameError: String?,
        val carbsError: String?,
        val sexError: String? = null,
        val birthDateError: String? = null,
        val waterError: String? = null,
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

    /**
     * [otherNames] — имена остальных профилей (без редактируемого).
     * Граммы в ХЕ проверяются, только если показ ХЕ включён; иначе остаётся [currentCarbs].
     * Норма воды — только если вода включена; иначе остаётся [currentWater].
     */
    fun validate(
        form: ProfileForm,
        otherNames: Collection<String>,
        currentCarbs: Double = DEFAULT_CARBS_PER_XE,
        currentWater: Double = DEFAULT_WATER_ML_PER_KG,
        today: LocalDate = LocalDate.now(),
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

        return if (listOf(nameError, carbsError, sexError, birthDateError, waterError).all { it == null }) {
            ProfileValidation.Valid(name, carbs, water)
        } else {
            ProfileValidation.Invalid(nameError, carbsError, sexError, birthDateError, waterError)
        }
    }

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
