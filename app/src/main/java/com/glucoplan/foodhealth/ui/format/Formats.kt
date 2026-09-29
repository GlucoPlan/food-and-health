package com.glucoplan.foodhealth.ui.format

import com.glucoplan.foodhealth.data.NumberText
import com.glucoplan.foodhealth.data.nutrition.Nutrition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")

/** «250 ккал · Б 10 · Ж 5 · У 40». */
fun nutritionLine(n: Nutrition): String =
    "${NumberText.format(n.kcal, 0)} ккал · Б ${NumberText.format(n.protein, 1)} · " +
        "Ж ${NumberText.format(n.fat, 1)} · У ${NumberText.format(n.carbs, 1)}"

/** «1 250 г». */
fun grams(value: Double): String {
    val text = NumberText.format(value, 0)
    return (if (text.length > 4) text.reversed().chunked(3).joinToString(" ").reversed() else text) + " г"
}

/** «29 сент.»; для другого года — «29 сент. 2025». */
fun shortDate(millis: Long): String {
    val year = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR)
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val pattern = if (year == thisYear) "d MMM" else "d MMM yyyy"
    return SimpleDateFormat(pattern, ru).format(Date(millis))
}
