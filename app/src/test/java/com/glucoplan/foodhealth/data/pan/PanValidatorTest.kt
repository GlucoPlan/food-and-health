package com.glucoplan.foodhealth.data.pan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PanValidatorTest {

    @Test
    fun `нормальная кастрюля`() {
        assertThat(PanValidator.validate(PanForm(" Большая ", "1250,5", "a.jpg")))
            .isEqualTo(PanValidation.Valid("Большая", 1250.5, "a.jpg"))
    }

    @Test
    fun `пустое название и неверный вес — ошибки`() {
        listOf("", "0", "-5", "abc", "20001").forEach { weight ->
            val r = PanValidator.validate(PanForm("", weight)) as PanValidation.Invalid
            assertThat(r.nameError).isNotNull()
            assertThat(r.weightError).isNotNull()
        }
    }
}
