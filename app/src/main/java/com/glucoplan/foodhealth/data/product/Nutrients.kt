package com.glucoplan.foodhealth.data.product

enum class NutrientUnit(val label: String) { MG("мг"), MCG("мкг") }

/** Нутриент из справочника (ТЗ 5.2). [dailyNorm] — суточная норма в единицах [unit]. */
data class Nutrient(val code: String, val name: String, val unit: NutrientUnit, val dailyNorm: Double)

/**
 * Справочник витаминов и минералов для поля Product.micro.
 * Коды хранятся в базе: менять их нельзя, добавлять новые можно без миграции.
 * Суточные нормы — МР 2.3.1.0253-21 для взрослых; где нормы мужчин и женщин
 * различаются, взята норма для мужчин. Пригодятся в отчётах (этап 3).
 */
object Nutrients {
    val vitamins = listOf(
        Nutrient("vit_a", "Витамин A", NutrientUnit.MCG, 900.0),
        Nutrient("vit_c", "Витамин C", NutrientUnit.MG, 100.0),
        Nutrient("vit_d", "Витамин D", NutrientUnit.MCG, 15.0),
        Nutrient("vit_e", "Витамин E", NutrientUnit.MG, 15.0),
        Nutrient("vit_k", "Витамин K", NutrientUnit.MCG, 120.0),
        Nutrient("vit_b1", "Витамин B1", NutrientUnit.MG, 1.5),
        Nutrient("vit_b2", "Витамин B2", NutrientUnit.MG, 1.8),
        Nutrient("vit_b6", "Витамин B6", NutrientUnit.MG, 2.0),
        Nutrient("vit_b9", "Витамин B9 (фолаты)", NutrientUnit.MCG, 400.0),
        Nutrient("vit_b12", "Витамин B12", NutrientUnit.MCG, 3.0),
    )

    val minerals = listOf(
        Nutrient("ca", "Кальций", NutrientUnit.MG, 1000.0),
        Nutrient("fe", "Железо", NutrientUnit.MG, 10.0),
        Nutrient("mg", "Магний", NutrientUnit.MG, 420.0),
        Nutrient("k", "Калий", NutrientUnit.MG, 3500.0),
        Nutrient("na", "Натрий", NutrientUnit.MG, 1300.0),
        Nutrient("zn", "Цинк", NutrientUnit.MG, 12.0),
        Nutrient("p", "Фосфор", NutrientUnit.MG, 700.0),
        Nutrient("i", "Йод", NutrientUnit.MCG, 150.0),
    )

    val all = vitamins + minerals

    fun byCode(code: String): Nutrient? = all.firstOrNull { it.code == code }
}
