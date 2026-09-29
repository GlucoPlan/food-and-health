package com.glucoplan.foodhealth.data.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProfileValidatorTest {

    private fun validate(form: ProfileForm, others: List<String> = emptyList(), current: Double = 10.0) =
        ProfileValidator.validate(form, others, current)

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
}
