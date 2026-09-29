package com.glucoplan.foodhealth.data.pan

import com.glucoplan.foodhealth.data.NumberText

/** Кастрюля. [photo] — имя файла в PanPhotos или null. */
data class Pan(val id: String, val name: String, val weightG: Double, val photo: String?, val deleted: Boolean)

data class PanForm(val name: String = "", val weight: String = "", val photo: String? = null) {
    companion object {
        fun from(pan: Pan) = PanForm(pan.name, NumberText.format(pan.weightG), pan.photo)
    }
}

sealed interface PanValidation {
    data class Valid(val name: String, val weightG: Double, val photo: String?) : PanValidation
    data class Invalid(val nameError: String?, val weightError: String?) : PanValidation
}

object PanValidator {
    const val MAX_WEIGHT_G = 20_000.0

    fun validate(form: PanForm): PanValidation {
        val name = form.name.trim()
        val nameError = if (name.isEmpty()) "Введите название" else null
        val weight = NumberText.parse(form.weight)?.takeIf { it > 0 && it <= MAX_WEIGHT_G }
        val weightError = if (weight == null) "Число от 0 до ${NumberText.format(MAX_WEIGHT_G)}" else null
        return if (nameError == null && weightError == null) {
            PanValidation.Valid(name, weight!!, form.photo)
        } else {
            PanValidation.Invalid(nameError, weightError)
        }
    }
}
