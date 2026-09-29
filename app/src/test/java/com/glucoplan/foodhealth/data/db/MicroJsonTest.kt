package com.glucoplan.foodhealth.data.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MicroJsonTest {

    @Test
    fun `туда и обратно`() {
        val micro = mapOf("vit_c" to 12.5, "ca" to 120.0)
        assertThat(MicroJson.decode(MicroJson.encode(micro))).isEqualTo(micro)
    }

    @Test
    fun `пустой набор`() {
        assertThat(MicroJson.encode(emptyMap())).isEqualTo("{}")
        assertThat(MicroJson.decode("{}")).isEmpty()
    }

    @Test
    fun `неизвестные коды сохраняются`() {
        assertThat(MicroJson.decode("""{"vit_c":10,"omega3":1.5}"""))
            .containsExactly("vit_c", 10.0, "omega3", 1.5)
    }

    @Test
    fun `испорченный JSON и нечисловые значения не роняют приложение`() {
        assertThat(MicroJson.decode("не json")).isEmpty()
        assertThat(MicroJson.decode("""{"vit_c":"много","ca":5}""")).containsExactly("ca", 5.0)
    }
}
