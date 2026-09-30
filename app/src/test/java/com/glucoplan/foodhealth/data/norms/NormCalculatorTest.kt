package com.glucoplan.foodhealth.data.norms

import com.glucoplan.foodhealth.data.product.Nutrients
import com.glucoplan.foodhealth.data.profile.Sex
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Расчёт норм (ТЗ 17.3). Основные случаи — общие с сервером контрольные примеры shared/norm-cases.json. */
class NormCalculatorTest {

    private val date = LocalDate.of(2026, 9, 30)

    @Test
    fun `общие контрольные примеры`() {
        // Рабочая папка unit-тестов — модуль app
        val root = Json.parseToJsonElement(File("../shared/norm-cases.json").readText()).jsonObject
        val cases = root.getValue("cases").jsonArray
        assertThat(cases).isNotEmpty()
        cases.forEach { element ->
            val case = element.jsonObject
            val name = case.getValue("name").jsonPrimitive.content
            val norms = NormCalculator.calculate(input(case.getValue("input").jsonObject), dateOf(case))
            case.getValue("expect").jsonObject.forEach { (key, expected) ->
                check(name, key, actual(norms, key), expected)
            }
        }
    }

    private fun dateOf(case: JsonObject) = LocalDate.parse(case.getValue("input").jsonObject.getValue("date").jsonPrimitive.content)

    private fun input(json: JsonObject): NormInput {
        fun num(key: String, obj: JsonObject = json) = (obj[key] as? JsonPrimitive)?.doubleOrNull
        fun text(key: String) = (json[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
        val manual = json["manual"]?.jsonObject ?: JsonObject(emptyMap())
        return NormInput(
            sex = Sex.fromCode(text("sex")),
            birthDate = text("birth_date")?.let(LocalDate::parse),
            heightCm = num("height_cm"),
            weightKg = num("weight_kg"),
            activity = Activity.fromCode(text("activity")),
            targetWeightKg = num("target_weight_kg"),
            paceKgPerWeek = num("pace_kg_per_week") ?: NormCalculator.DEFAULT_PACE,
            manual = NormSet(num("kcal", manual), num("protein", manual), num("fat", manual), num("carbs", manual)),
        )
    }

    private fun actual(n: DailyNorms, key: String): Any? = when (key) {
        "child" -> n.child
        "missing" -> n.missing.map { it.code }
        "auto_kcal" -> n.auto.kcal
        "auto_protein" -> n.auto.protein
        "kcal" -> n.final.kcal
        "protein" -> n.final.protein
        "fat" -> n.final.fat
        "carbs" -> n.final.carbs
        "fiber_min_g" -> n.fiberMinG
        "salt_max_g" -> n.saltMaxG
        "micro" -> n.micro
        else -> error("неизвестное поле $key")
    }

    private fun check(name: String, key: String, actual: Any?, expected: JsonElement) {
        val what = assertWithMessage("$name: $key")
        when {
            expected is JsonNull -> what.that(actual).isNull()
            key == "missing" -> what.that(actual).isEqualTo(expected.jsonArray.map { it.jsonPrimitive.content })
            key == "micro" -> {
                val map = actual as Map<*, *>
                val exp = expected.jsonObject
                if (exp.isEmpty()) what.that(map).isEmpty()
                exp.forEach { (code, v) ->
                    assertWithMessage("$name: micro.$code").that(map[code] as Double?)
                        .isWithin(TOLERANCE).of(v.jsonPrimitive.doubleOrNull!!)
                }
            }
            expected.jsonPrimitive.booleanOrNull != null && !expected.jsonPrimitive.isString ->
                what.that(actual).isEqualTo(expected.jsonPrimitive.booleanOrNull)
            else -> {
                what.that(actual).isNotNull()
                what.that(actual as Double).isWithin(TOLERANCE).of(expected.jsonPrimitive.doubleOrNull!!)
            }
        }
    }

    @Test
    fun `базовый обмен по Миффлину — Сан Жеору`() {
        assertThat(NormCalculator.bmr(Sex.MALE, 41, 180.0, 95.0)).isWithin(TOLERANCE).of(1875.0)
        assertThat(NormCalculator.bmr(Sex.FEMALE, 36, 160.0, 70.0)).isWithin(TOLERANCE).of(1359.0)
    }

    @Test
    fun `неизвестная или пустая активность — умеренно`() {
        assertThat(Activity.fromCode(null)).isEqualTo(Activity.MODERATE)
        assertThat(Activity.fromCode("marathon")).isEqualTo(Activity.MODERATE)
        assertThat(Activity.fromCode("high")).isEqualTo(Activity.HIGH)
    }

    @Test
    fun `у всех возрастов от года есть нормы всех нутриентов справочника`() {
        val codes = Nutrients.all.map { it.code }.toSet()
        for (age in 1..100) for (sex in Sex.entries) {
            assertWithMessage("$age лет, $sex").that(NormTables.micro(age, sex).keys).isEqualTo(codes)
        }
    }

    @Test
    fun `у детей нет цели по весу — активность и цель не влияют`() {
        val base = NormInput(Sex.FEMALE, LocalDate.of(2014, 5, 20), 150.0, 40.0)
        val withGoal = base.copy(activity = Activity.HIGH, targetWeightKg = 35.0, paceKgPerWeek = 1.0)
        assertThat(NormCalculator.calculate(withGoal, date).final)
            .isEqualTo(NormCalculator.calculate(base, date).final)
    }

    @Test
    fun `норма считается по весу, переданному на нужный день`() {
        val input = NormInput(Sex.MALE, LocalDate.of(1985, 3, 15), 180.0, 100.0)
        val lighter = input.copy(weightKg = 90.0)
        // 10 ккал на кг × 1,55
        assertThat(NormCalculator.calculate(input, date).final.kcal!! - NormCalculator.calculate(lighter, date).final.kcal!!)
            .isWithin(TOLERANCE).of(155.0)
    }

    private companion object {
        const val TOLERANCE = 0.001
    }
}
