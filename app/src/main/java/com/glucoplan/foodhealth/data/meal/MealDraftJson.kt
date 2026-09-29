package com.glucoplan.foodhealth.data.meal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.glucoplan.foodhealth.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

/**
 * Черновик приёма: один на телефон, общий для экрана «Приём пищи» и «Повторить» из истории.
 * Держится в памяти и каждое изменение сразу пишется в DataStore (ТЗ 4.1).
 */
@Singleton
class MealDraftStore @Inject constructor(
    private val prefs: DataStore<Preferences>,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _draft = MutableStateFlow<MealDraft?>(null)

    /** null — ещё читается из DataStore. */
    val draft: StateFlow<MealDraft?> = _draft.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    /** Сообщение для экрана приёма, например о пропущенном при «Повторить». */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        scope.launch {
            _draft.value = MealDraftJson.decode(prefs.data.first()[KEY])
            _draft.filterNotNull().drop(1).collect { draft ->
                prefs.edit { if (draft.isEmpty) it.remove(KEY) else it[KEY] = MealDraftJson.encode(draft) }
            }
        }
    }

    /** Изменить черновик; до загрузки из DataStore изменения не принимаются. */
    fun update(change: (MealDraft) -> MealDraft) {
        _draft.update { it?.let(change) }
    }

    fun replace(draft: MealDraft, notice: String? = null) {
        _draft.value = draft
        _notice.value = notice
    }

    fun onNoticeShown() {
        _notice.value = null
    }

    private companion object {
        val KEY = stringPreferencesKey("meal_draft")
    }
}
