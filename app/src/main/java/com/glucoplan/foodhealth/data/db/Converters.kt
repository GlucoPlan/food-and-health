package com.glucoplan.foodhealth.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Витамины и минералы продукта: JSON «код → значение на 100 г».
 * Сохраняются все ключи, в том числе неизвестные этой версии приложения.
 */
object MicroJson {

    fun encode(micro: Map<String, Double>): String =
        JsonObject(micro.toSortedMap().mapValues { JsonPrimitive(it.value) }).toString()

    fun decode(json: String): Map<String, Double> = try {
        Json.parseToJsonElement(json).jsonObject.mapNotNull { (code, value) ->
            (value as? JsonPrimitive)?.doubleOrNull?.let { code to it }
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

class Converters {
    @TypeConverter
    fun microToJson(micro: Map<String, Double>): String = MicroJson.encode(micro)

    @TypeConverter
    fun jsonToMicro(json: String): Map<String, Double> = MicroJson.decode(json)
}
