package com.glucoplan.foodhealth.data.profile

import java.util.Locale

/** Профиль без служебных полей синхронизации. */
data class Profile(
    val id: String,
    val name: String,
    val sd1Enabled: Boolean,
    val showXe: Boolean,
    val carbsPerXe: Double,
)

/** То, что пользователь ввёл в редакторе профиля. */
data class ProfileForm(
    val name: String,
    val sd1Enabled: Boolean = false,
    val showXe: Boolean = false,
    val carbsPerXe: String = ProfileValidator.formatCarbs(ProfileValidator.DEFAULT_CARBS_PER_XE),
)

sealed interface ProfileValidation {
    data class Valid(val name: String, val carbsPerXe: Double) : ProfileValidation
    data class Invalid(val nameError: String?, val carbsError: String?) : ProfileValidation
}

object ProfileValidator {
    const val DEFAULT_CARBS_PER_XE = 10.0
    const val MIN_CARBS_PER_XE = 5.0
    const val MAX_CARBS_PER_XE = 25.0

    /**
     * [otherNames] — имена остальных профилей (без редактируемого).
     * Граммы в ХЕ проверяются, только если показ ХЕ включён; иначе остаётся [currentCarbs].
     */
    fun validate(
        form: ProfileForm,
        otherNames: Collection<String>,
        currentCarbs: Double = DEFAULT_CARBS_PER_XE,
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

        return if (nameError == null && carbsError == null) {
            ProfileValidation.Valid(name, carbs)
        } else {
            ProfileValidation.Invalid(nameError, carbsError)
        }
    }

    /** 10.0 → "10", 12.5 → "12,5". */
    fun formatCarbs(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else value.toString().replace('.', ',')

    private fun parseNumber(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun normalize(name: String) = name.trim().lowercase(Locale.forLanguageTag("ru"))
}
