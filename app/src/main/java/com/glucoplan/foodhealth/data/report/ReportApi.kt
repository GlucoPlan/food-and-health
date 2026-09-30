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
import javax.inject.Inject
import javax.inject.Singleton

/** Отчёты с сервера (ТЗ 17.2). Ошибки — те же, что у синхронизации. */
interface ReportBackend {
    /** Отчёт [kind] за день [date] или за период, в который он входит. */
    suspend fun report(config: ServerConfig, kind: ReportKind, profileId: String, date: LocalDate): Report
}

@Singleton
class ReportApi @Inject constructor(private val client: OkHttpClient) : ReportBackend {

    override suspend fun report(config: ServerConfig, kind: ReportKind, profileId: String, date: LocalDate): Report =
        withContext(Dispatchers.IO) {
            val url = "${config.url}/reports/${kind.code}".toHttpUrl().newBuilder()
                .addQueryParameter("profile_id", profileId)
                .addQueryParameter("date", date.toString())
                .build()
            val body = try {
                client.newCall(Request.Builder().url(url).header("X-Family-Key", config.key).get().build())
                    .execute().use { response ->
                        when {
                            response.code == 401 -> throw SyncException.Unauthorized()
                            !response.isSuccessful -> throw SyncException.Http(response.code)
                            else -> response.body.string()
                        }
                    }
            } catch (e: IOException) {
                throw SyncException.Network(e)
            }
            try {
                ReportJson.parse(body)
            } catch (e: Exception) {
                throw SyncException.BadResponse(e)
            }
        }
}
