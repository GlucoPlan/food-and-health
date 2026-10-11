package com.glucoplan.foodhealth.data.report

import com.glucoplan.foodhealth.data.sync.ServerConfig
import com.glucoplan.foodhealth.data.sync.SyncException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Отчёты с сервера (ТЗ 17.2). Ошибки — те же, что у синхронизации. */
interface ReportBackend {
    /** Отчёт [kind] за день [date] или за период, в который он входит. */
    suspend fun report(config: ServerConfig, kind: ReportKind, profileId: String, date: LocalDate): Report

    /** PDF для врача (ТЗ 17.11) за дни с [first] по [last] включительно. */
    suspend fun doctorPdf(config: ServerConfig, profileId: String, first: LocalDate, last: LocalDate): ByteArray
}

@Singleton
class ReportApi @Inject constructor(private val client: OkHttpClient) : ReportBackend {

    // PDF за долгий период сервер собирает дольше обычного отчёта
    private val pdfClient = client.newBuilder().callTimeout(90, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    override suspend fun report(config: ServerConfig, kind: ReportKind, profileId: String, date: LocalDate): Report {
        val body = get(client, config, kind.code, "profile_id" to profileId, "date" to date.toString()).decodeToString()
        return try {
            ReportJson.parse(body)
        } catch (e: Exception) {
            throw SyncException.BadResponse(e)
        }
    }

    override suspend fun doctorPdf(config: ServerConfig, profileId: String, first: LocalDate, last: LocalDate): ByteArray {
        val pdf = get(pdfClient, config, "doctor", "profile_id" to profileId, "first" to first.toString(), "last" to last.toString())
        if (!pdf.startsWith("%PDF".toByteArray())) throw SyncException.BadResponse(IllegalStateException("не PDF"))
        return pdf
    }

    private suspend fun get(http: OkHttpClient, config: ServerConfig, path: String, vararg params: Pair<String, String>): ByteArray =
        withContext(Dispatchers.IO) {
            val url = "${config.url}/reports/$path".toHttpUrl().newBuilder()
                .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
                .build()
            try {
                http.newCall(Request.Builder().url(url).header("X-Family-Key", config.key).get().build())
                    .execute().use { response ->
                        when {
                            response.code == 401 -> throw SyncException.Unauthorized()
                            !response.isSuccessful -> throw SyncException.Http(response.code)
                            else -> response.body.bytes()
                        }
                    }
            } catch (e: IOException) {
                throw SyncException.Network(e)
            }
        }

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
