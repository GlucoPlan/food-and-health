package com.glucoplan.foodhealth.data.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class SyncJsonTest {

    @Test
    fun `запрос в формате сервера`() {
        val json = SyncJson.encode(
            SyncRequest(
                "dev", 42,
                listOf(WireChange("product", "p1", JsonObject(mapOf("name" to JsonPrimitive("Молоко"))), 7, true)),
            )
        )
        val obj = Json.parseToJsonElement(json).jsonObject
        assertThat(obj["device_id"]).isEqualTo(JsonPrimitive("dev"))
        assertThat(obj["cursor"]).isEqualTo(JsonPrimitive(42))
        val change = obj["changes"]!!.jsonArray.single().jsonObject
        assertThat(change.keys).containsExactly("table", "id", "data", "updated_at", "deleted")
        assertThat(change["deleted"]).isEqualTo(JsonPrimitive(true))
    }

    @Test
    fun `ответ сервера`() {
        val response = SyncJson.decode(
            """{"cursor":5,"has_more":true,"changes":[{"table":"meal","id":"m","data":{"glucose":6.5},""" +
                """"updated_at":3,"deleted":false,"device_id":"other"}]}"""
        )
        assertThat(response.cursor).isEqualTo(5)
        assertThat(response.hasMore).isTrue()
        assertThat(response.changes.single().table).isEqualTo("meal")
        assertThat(response.changes.single().data["glucose"]).isEqualTo(JsonPrimitive(6.5))
    }

    @Test(expected = Exception::class)
    fun `битый ответ — исключение`() {
        SyncJson.decode("""{"cursor":"много"}""")
    }
}
