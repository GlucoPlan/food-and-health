package com.glucoplan.foodhealth.data.product

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductValidatorTest {

    private val valid = ProductForm(name = "Молоко", kcal = "60", protein = "3", fat = "3,2", carbs = "4,7")

    private fun errors(form: ProductForm) =
        (ProductValidator.validate("id", form) as ProductValidation.Invalid).errors

    private fun product(form: ProductForm) =
        (ProductValidator.validate("id", form) as ProductValidation.Valid).product

    @Test
    fun `минимальный продукт: название и КБЖУ`() {
        val p = product(valid)
        assertThat(p.name).isEqualTo("Молоко")
        assertThat(p.fat).isEqualTo(3.2)
        assertThat(p.gi).isNull()
        assertThat(p.fiber).isNull()
        assertThat(p.brand).isNull()
        assertThat(p.barcode).isNull()
        assertThat(p.pieceWeightG).isNull()
        assertThat(p.micro).isEmpty()
        assertThat(p.source).isEqualTo(ProductSource.MANUAL)
    }

    @Test
    fun `пробелы по краям обрезаются, пустые строки становятся null`() {
        val p = product(valid.copy(name = "  Молоко ", brand = "  ", notes = " ", barcode = " 4600000000017 "))
        assertThat(p.name).isEqualTo("Молоко")
        assertThat(p.brand).isNull()
        assertThat(p.notes).isNull()
        assertThat(p.barcode).isEqualTo("4600000000017")
    }

    @Test
    fun `название и КБЖУ обязательны`() {
        val e = errors(ProductForm())
        assertThat(e.keys).containsAtLeast(
            ProductField.NAME, ProductField.KCAL, ProductField.PROTEIN, ProductField.FAT, ProductField.CARBS,
        )
    }

    @Test
    fun `ГИ необязательный, но если указан — целое от 0 до 100`() {
        assertThat(product(valid.copy(gi = "")).gi).isNull()
        assertThat(product(valid.copy(gi = "0")).gi).isEqualTo(0)
        assertThat(product(valid.copy(gi = "100")).gi).isEqualTo(100)
        assertThat(errors(valid.copy(gi = "101"))).containsKey(ProductField.GI)
        assertThat(errors(valid.copy(gi = "55,5"))).containsKey(ProductField.GI)
        assertThat(errors(valid.copy(gi = "-1"))).containsKey(ProductField.GI)
    }

    @Test
    fun `границы ккал и граммов`() {
        assertThat(product(valid.copy(kcal = "900", protein = "0", fat = "100", carbs = "0")).kcal).isEqualTo(900.0)
        assertThat(errors(valid.copy(kcal = "900,1"))).containsKey(ProductField.KCAL)
        assertThat(errors(valid.copy(protein = "-1"))).containsKey(ProductField.PROTEIN)
        assertThat(errors(valid.copy(salt = "100,5"))).containsKey(ProductField.SALT)
        assertThat(errors(valid.copy(fiber = "abc"))).containsKey(ProductField.FIBER)
    }

    @Test
    fun `сумма БЖУ не больше 100 г`() {
        assertThat(product(valid.copy(protein = "20", fat = "50", carbs = "30")).carbs).isEqualTo(30.0)
        assertThat(errors(valid.copy(protein = "20", fat = "50", carbs = "30,1"))).containsKey(ProductField.MACROS)
    }

    @Test
    fun `вес штуки больше нуля`() {
        assertThat(product(valid.copy(pieceWeight = "55,5")).pieceWeightG).isEqualTo(55.5)
        assertThat(errors(valid.copy(pieceWeight = "0"))).containsKey(ProductField.PIECE_WEIGHT)
    }

    @Test
    fun `штрихкод только из цифр`() {
        assertThat(errors(valid.copy(barcode = "46000-1"))).containsKey(ProductField.BARCODE)
    }

    @Test
    fun `витамины: пустые пропускаются, отрицательные — ошибка`() {
        val p = product(valid.copy(micro = mapOf("vit_c" to "12,5", "ca" to "", "fe" to "0")))
        assertThat(p.micro).containsExactly("vit_c", 12.5, "fe", 0.0)
        assertThat(errors(valid.copy(micro = mapOf("vit_c" to "-1")))).containsKey(ProductField.micro("vit_c"))
    }

    @Test
    fun `карточка из продукта и обратно даёт тот же продукт`() {
        val original = product(
            valid.copy(
                brand = "Простоквашино", barcode = "4600000000017", gi = "30", fiber = "0", sugar = "4,7",
                salt = "0,1", pieceWeight = "250", source = ProductSource.LABEL, notes = "2,5%",
                micro = mapOf("ca" to "120", "vit_b12" to "0,4"),
            )
        )
        assertThat(product(ProductForm.from(original))).isEqualTo(original)
    }
}
