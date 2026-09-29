package com.glucoplan.foodhealth.data.profile

import java.time.LocalDate

/** Форма профиля для тестов: пол и дата рождения заполнены (обязательны с этапа 2). */
fun filledProfileForm(
    name: String,
    sd1Enabled: Boolean = false,
    showXe: Boolean = false,
    carbsPerXe: String = "10",
    sex: Sex = Sex.FEMALE,
    birthDate: LocalDate = LocalDate.of(2000, 1, 1),
) = ProfileForm(name, sd1Enabled, showXe, carbsPerXe, sex = sex, birthDate = birthDate)
