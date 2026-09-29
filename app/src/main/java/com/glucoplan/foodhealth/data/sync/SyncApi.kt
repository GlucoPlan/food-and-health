package com.glucoplan.foodhealth.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Изменение записи в протоколе сервера (server/README.md). */
data class WireChange(
    val table: String,
    val id: String,
    val data: JsonObject,
    val updatedAt: Long,
    val deleted: Boolean,
)

data class SyncRequest(val deviceId: String, val cursor: Long, val changes: List<WireChange>)

data class SyncResponse(val cursor: Long, val hasMore: Boolean, val changes: List<WireChange>)

sealed class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized : SyncException("Неверный ключ семьи")
    class Http(code: Int) : SyncException("Сервер ответил $code")
    class Network(cause: Throwable) : SyncException("Нет связи с сервером: ${cause.message ?: cause.javaClass.simpleName}", cause)
    class BadResponse(cause: Throwable) : SyncException("Непонятный ответ сервера", cause)
}

/** Сервер синхронизации; в тестах подменяется поддельным. */
interface SyncBackend {
    /** Число записей на сервере; заодно проверяет адрес и ключ. */
    suspend fun health(config: ServerConfig): Int

    suspend fun sync(config: ServerConfig, request: SyncRequest): SyncResponse

    /** Загрузить фото под id телефона; повторная загрузка безопасна. */
    suspend fun uploadPhoto(config: ServerConfig, id: String, jpeg: ByteArray)

    /** Скачать фото; null — на сервере его пока нет. */
    suspend fun downloadPhoto(config: ServerConfig, id: String): ByteArray?
}

@Singleton
class SyncApi @Inject constructor(client: OkHttpClient) : SyncBackend {

    // Первая синхронизация может нести тысячи записей — таймаут больше обычного
    private val http = client.newBuilder().callTimeout(90, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    override suspend fun health(config: ServerConfig): Int {
        val body = call(config, Request.Builder().url("${config.url}/health").get())
        return parse { Json.parseToJsonElement(body).jsonObject.getValue("records").jsonPrimitive.int }
    }

    override suspend fun sync(config: ServerConfig, request: SyncRequest): SyncResponse {
        val body = call(
            config,
            Request.Builder().url("${config.url}/sync").post(SyncJson.encode(request).toRequestBody(JSON)),
        )
        return parse { SyncJson.decode(body) }
    }

    override suspend fun uploadPhoto(config: ServerConfig, id: String, jpeg: ByteArray) {
        call(config, Request.Builder().url("${config.url}/photos/$id").put(jpeg.toRequestBody(JPEG)))
    }

    override suspend fun downloadPhoto(config: ServerConfig, id: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("${config.url}/photos/$id").header("X-Family-Key", config.key).get().build()
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> null
                    response.code == 401 -> throw SyncException.Unauthorized()
                    !response.isSuccessful -> throw SyncException.Http(response.code)
                    else -> response.body.bytes()
                }
            }
        } catch (e: IOException) {
            throw SyncException.Network(e)
        }
    }

    private suspend fun call(config: ServerConfig, builder: Request.Builder): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(builder.header("X-Family-Key", config.key).build()).execute().use { response ->
                when {
                    response.code == 401 -> throw SyncException.Unauthorized()
                    !response.isSuccessful -> throw SyncException.Http(response.code)
                    else -> response.body.string()
                }
            }
        } catch (e: IOException) {
            throw SyncException.Network(e)
        }
    }

    private inline fun <T> parse(block: () -> T): T = try {
        block()
    } catch (e: SyncException) {
        throw e
    } catch (e: Exception) {
        throw SyncException.BadResponse(e)
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        val JPEG = "image/jpeg".toMediaType()
    }
}

/** JSON протокола /sync. */
object SyncJson {

    fun encode(request: SyncRequest): String = JsonObject(
        mapOf(
            "device_id" to JsonPrimitive(request.deviceId),
            "cursor" to JsonPrimitive(request.cursor),
            "changes" to JsonArray(request.changes.map { c ->
                JsonObject(
                    mapOf(
                        "table" to JsonPrimitive(c.table),
                        "id" to JsonPrimitive(c.id),
                        "data" to c.data,
                        "updated_at" to JsonPrimitive(c.updatedAt),
                        "deleted" to JsonPrimitive(c.deleted),
                    )
                )
            }),
        )
    ).toString()

    fun decode(body: String): SyncResponse {
        val obj = Json.parseToJsonElement(body).jsonObject
        return SyncResponse(
            cursor = obj.getValue("cursor").jsonPrimitive.long,
            hasMore = obj.getValue("has_more").jsonPrimitive.boolean,
            changes = obj.getValue("changes").jsonArray.map { e ->
                val c = e.jsonObject
                WireChange(
                    table = c.getValue("table").jsonPrimitive.content,
                    id = c.getValue("id").jsonPrimitive.content,
                    data = c.getValue("data").jsonObject,
                    updatedAt = c.getValue("updated_at").jsonPrimitive.long,
                    deleted = c.getValue("deleted").jsonPrimitive.boolean,
                )
            },
        )
    }
}
