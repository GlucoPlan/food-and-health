package com.glucoplan.foodhealth.data.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class ProfileValidatorTest {

    private val today = LocalDate.of(2026, 9, 30)

    /** Пол и дата рождения обязательны; если тест проверяет другое — дозаполняем их. */
    private fun validate(form: ProfileForm, others: List<String> = emptyList(), current: Double = 10.0) =
        ProfileValidator.validate(
            form.copy(sex = form.sex ?: Sex.MALE, birthDate = form.birthDate ?: LocalDate.of(1990, 1, 1)),
            others, current, today = today,
        )

    private fun raw(form: ProfileForm, currentWater: Double = 30.0) =
        ProfileValidator.validate(form, emptyList(), currentWater = currentWater, today = today)

    @Test
    fun `имя обрезается по краям`() {
        val result = validate(ProfileForm(name = "  Иван  "))
        assertThat(result).isEqualTo(ProfileValidation.Valid("Иван", 10.0))
    }

    @Test
    fun `пустое имя — ошибка`() {
        val result = validate(ProfileForm(name = "   ")) as ProfileValidation.Invalid
        assertThat(result.nameError).isNotNull()
        assertThat(result.carbsError).isNull()
    }

    @Test
    fun `дубликат имени без учёта регистра — ошибка`() {
        val result = validate(ProfileForm(name = "иван"), others = listOf("Иван")) as ProfileValidation.Invalid
        assertThat(result.nameError).isEqualTo("Такой профиль уже есть")
    }

    @Test
    fun `по умолчанию 10 г углеводов в ХЕ`() {
        assertThat(ProfileForm(name = "Иван").carbsPerXe).isEqualTo("10")
        assertThat(ProfileValidator.DEFAULT_CARBS_PER_XE).isEqualTo(10.0)
    }

    @Test
    fun `граммы в ХЕ принимаются с запятой и точкой`() {
        assertThat(validate(ProfileForm("Дочь", showXe = true, carbsPerXe = "12,5")))
            .isEqualTo(ProfileValidation.Valid("Дочь", 12.5))
        assertThat(validate(ProfileForm("Дочь", showXe = true, carbsPerXe = "12.5")))
            .isEqualTo(ProfileValidation.Valid("Дочь", 12.5))
    }

    @Test
    fun `границы граммов в ХЕ включительно`() {
        assertThat(validate(ProfileForm("Дочь", showXe = true, carbsPerXe = "5")))
            .isInstanceOf(ProfileValidation.Valid::class.java)
        assertThat(validate(ProfileForm("Дочь", showXe = true, carbsPerXe = "25")))
            .isInstanceOf(ProfileValidation.Valid::class.java)
    }

    @Test
    fun `граммы в ХЕ вне границ или не число — ошибка`() {
        listOf("4.9", "25.1", "", "abc", "-10").forEach { text ->
            val result = validate(ProfileForm("Дочь", showXe = true, carbsPerXe = text))
            assertThat(result).isInstanceOf(ProfileValidation.Invalid::class.java)
            assertThat((result as ProfileValidation.Invalid).carbsError).isNotNull()
        }
    }

    @Test
    fun `при выключенных ХЕ поле граммов не проверяется и значение не меняется`() {
        val result = validate(ProfileForm("Иван", showXe = false, carbsPerXe = "мусор"), current = 12.0)
        assertThat(result).isEqualTo(ProfileValidation.Valid("Иван", 12.0))
    }

    @Test
    fun `обе ошибки сразу`() {
        val result = validate(ProfileForm("", showXe = true, carbsPerXe = "0")) as ProfileValidation.Invalid
        assertThat(result.nameError).isNotNull()
        assertThat(result.carbsError).isNotNull()
    }

    @Test
    fun `форматирование граммов`() {
        assertThat(ProfileValidator.formatCarbs(10.0)).isEqualTo("10")
        assertThat(ProfileValidator.formatCarbs(12.5)).isEqualTo("12,5")
    }

    @Test
    fun `пол и дата рождения обязательны`() {
        val e = raw(ProfileForm("Иван")) as ProfileValidation.Invalid
        assertThat(e.sexError).isEqualTo("Укажите пол")
        assertThat(e.birthDateError).isEqualTo("Укажите дату рождения")
        assertThat(e.nameError).isNull()
    }

    @Test
    fun `дата рождения не в будущем и не раньше 1900 года`() {
        val base = ProfileForm("Иван", sex = Sex.MALE)
        assertThat(raw(base.copy(birthDate = today))).isInstanceOf(ProfileValidation.Valid::class.java)
        assertThat((raw(base.copy(birthDate = today.plusDays(1))) as ProfileValidation.Invalid).birthDateError).isNotNull()
        assertThat((raw(base.copy(birthDate = LocalDate.of(1899, 12, 31))) as ProfileValidation.Invalid).birthDateError)
            .isNotNull()
    }

    @Test
    fun `норма воды проверяется только при включённой воде`() {
        val base = ProfileForm("Иван", sex = Sex.MALE, birthDate = LocalDate.of(1985, 5, 5))
        assertThat(raw(base.copy(waterEnabled = false, waterMlPerKg = "мусор"), currentWater = 35.0))
            .isEqualTo(ProfileValidation.Valid("Иван", 10.0, 35.0))
        assertThat(raw(base.copy(waterEnabled = true, waterMlPerKg = "32,5")))
            .isEqualTo(ProfileValidation.Valid("Иван", 10.0, 32.5))
        listOf("9", "101", "", "много").forEach {
            assertThat((raw(base.copy(waterEnabled = true, waterMlPerKg = it)) as ProfileValidation.Invalid).waterError)
                .isNotNull()
        }
    }

    @Test
    fun `по умолчанию вода выключена, норма 30`() {
        val form = ProfileForm("Иван")
        assertThat(form.waterEnabled).isFalse()
        assertThat(form.waterMlPerKg).isEqualTo("30")
    }

    @Test
    fun `возраст — полных лет`() {
        val p = Profile("p", "Дочь", false, false, 10.0, Sex.FEMALE, LocalDate.of(2017, 10, 1))
        assertThat(p.ageYears(LocalDate.of(2026, 9, 30))).isEqualTo(8)
        assertThat(p.ageYears(LocalDate.of(2026, 10, 1))).isEqualTo(9)
        val leap = p.copy(birthDate = LocalDate.of(2016, 2, 29))
        assertThat(leap.ageYears(LocalDate.of(2025, 2, 28))).isEqualTo(8)
        assertThat(leap.ageYears(LocalDate.of(2025, 3, 1))).isEqualTo(9)
        assertThat(p.copy(birthDate = null).ageYears()).isNull()
    }

    @Test
    fun `возраст словами`() {
        assertThat(listOf(1, 2, 4, 5, 9, 11, 14, 21, 22, 25, 101, 111).map(ProfileValidator::ageText)).containsExactly(
            "1 год", "2 года", "4 года", "5 лет", "9 лет", "11 лет", "14 лет", "21 год", "22 года", "25 лет",
            "101 год", "111 лет",
        ).inOrder()
    }

    @Test
    fun `неполный профиль`() {
        val p = Profile("p", "Я", false, false, 10.0)
        assertThat(p.incomplete).isTrue()
        assertThat(p.copy(sex = Sex.MALE, birthDate = LocalDate.of(1980, 1, 1)).incomplete).isFalse()
    }
}
