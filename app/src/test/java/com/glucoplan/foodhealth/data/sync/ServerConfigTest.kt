package com.glucoplan.foodhealth.data.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerConfigTest {

    private val key = "ONmb-Apm-TNfDO42-test"

    @Test
    fun `https адрес принимается, хвостовой слэш и пробелы убираются`() {
        val c = ServerConfig.parse(" https://51434.koara.live/ ", " $key ").getOrThrow()
        assertThat(c.url).isEqualTo("https://51434.koara.live")
        assertThat(c.key).isEqualTo(key)
    }

    @Test
    fun `http и мусор не принимаются`() {
        listOf("http://51434.koara.live", "51434.koara.live", "https://", "").forEach {
            assertThat(ServerConfig.parse(it, key).isFailure).isTrue()
        }
    }

    @Test
    fun `короткий ключ не принимается`() {
        assertThat(ServerConfig.parse("https://51434.koara.live", "short").isFailure).isTrue()
    }
}
