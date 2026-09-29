package com.glucoplan.foodhealth.data.dish

import com.glucoplan.foodhealth.data.product.PieceWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PieceWeightTest {

    @Test
    fun `штуки в граммы`() {
        assertThat(PieceWeight.toGrams(2.0, 55.0)).isEqualTo(110.0)
        assertThat(PieceWeight.toGrams(0.5, 180.0)).isEqualTo(90.0)
    }

    @Test
    fun `граммы в штуки`() {
        assertThat(PieceWeight.toPieces(110.0, 55.0)).isEqualTo(2.0)
        assertThat(PieceWeight.toPieces(110.0, null)).isNull()
        assertThat(PieceWeight.toPieces(110.0, 0.0)).isNull()
    }
}
