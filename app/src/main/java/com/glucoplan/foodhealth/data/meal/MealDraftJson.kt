package com.glucoplan.foodhealth.data.meal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject
import javax.inject.Singleton

object MealDraftJson {

    fun encode(draft: MealDraft): String = JsonObject(
        buildMap {
            draft.profileId?.let { put("profileId", JsonPrimitive(it)) }
            draft.eatenAt?.let { put("eatenAt", JsonPrimitive(it)) }
            put("items", JsonArray(draft.items.map { item ->
                JsonObject(buildMap {
                    put("key", JsonPrimitive(item.key))
                    put("type", JsonPrimitive(item.type.code))
                    put("refId", JsonPrimitive(item.refId))
                    put("weight", JsonPrimitive(item.weight))
                    item.pieces?.let { put("pieces", JsonPrimitive(it)) }
                })
            }))
        }
    ).toString()

    /** Испорченный или пустой черновик — пустой приём, приложение не падает. */
    fun decode(json: String?): MealDraft {
        if (json.isNullOrBlank()) return MealDraft()
        return try {
            val obj = Json.parseToJsonElement(json).jsonObject
            MealDraft(
                profileId = obj["profileId"]?.jsonPrimitive?.contentOrNull,
                eatenAt = obj["eatenAt"]?.jsonPrimitive?.longOrNull,
                items = obj["items"]?.jsonArray.orEmpty().mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    DraftItem(
                        key = item["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        type = MealItemType.fromCode(item["type"]?.jsonPrimitive?.contentOrNull.orEmpty())
                            ?: return@mapNotNull null,
                        refId = item["refId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        weight = item["weight"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        pieces = item["pieces"]?.jsonPrimitive?.contentOrNull,
                    )
                },
            )
        } catch (e: Exception) {
            MealDraft()
        }
    }
}

/** Черновик приёма в DataStore: один на телефон. */
@Singleton
class MealDraftStore @Inject constructor(private val prefs: DataStore<Preferences>) {

    val draft: Flow<MealDraft> = prefs.data.map { MealDraftJson.decode(it[KEY]) }

    suspend fun save(draft: MealDraft) {
        prefs.edit { if (draft.isEmpty) it.remove(KEY) else it[KEY] = MealDraftJson.encode(draft) }
    }

    private companion object {
        val KEY = stringPreferencesKey("meal_draft")
    }
}
