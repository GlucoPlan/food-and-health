package com.glucoplan.foodhealth.data.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BarcodesTest {

    @Test
    fun `пробелы убираются, пустой код — null`() {
        assertThat(Barcodes.normalize(" 4600 0000 00017 ")).isEqualTo("4600000000017")
        assertThat(Barcodes.normalize("   ")).isNull()
    }

    @Test
    fun `товарные коды — только цифры длиной 8, 12 или 13`() {
        assertThat(Barcodes.isRetail("46000000")).isTrue()
        assertThat(Barcodes.isRetail("036000291452")).isTrue()
        assertThat(Barcodes.isRetail("4600000000017")).isTrue()
        assertThat(Barcodes.isRetail("460000000001")).isTrue()
        assertThat(Barcodes.isRetail("4600000")).isFalse()
        assertThat(Barcodes.isRetail("46000000000170")).isFalse()
        assertThat(Barcodes.isRetail("46000000000A7")).isFalse()
        assertThat(Barcodes.isRetail("")).isFalse()
    }

    @Test
    fun `код принимается со второго одинакового кадра`() {
        val c = ScanConfirmer()
        assertThat(c.offer("111")).isNull()
        assertThat(c.offer("111")).isEqualTo("111")
    }

    @Test
    fun `другой код сбрасывает счётчик`() {
        val c = ScanConfirmer()
        assertThat(c.offer("111")).isNull()
        assertThat(c.offer("222")).isNull()
        assertThat(c.offer("111")).isNull()
        assertThat(c.offer("111")).isEqualTo("111")
    }
}
