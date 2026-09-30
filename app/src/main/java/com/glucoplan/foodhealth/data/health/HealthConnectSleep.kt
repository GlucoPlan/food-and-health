package com.glucoplan.foodhealth.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Откуда берётся сон; в тестах подменяется. */
interface SleepSource {
    /** Health Connect есть на телефоне. */
    fun available(): Boolean

    suspend fun hasPermission(): Boolean

    suspend fun sessions(from: Long, to: Long): List<SleepSession>
}

/** Сон из Health Connect (ТЗ 15.5): только чтение, только с разрешения пользователя. */
@Singleton
class HealthConnectSleep @Inject constructor(
    @ApplicationContext private val context: Context,
) : SleepSource {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override fun available(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    override suspend fun hasPermission(): Boolean =
        available() && READ_SLEEP in client.permissionController.getGrantedPermissions()

    override suspend fun sessions(from: Long, to: Long): List<SleepSession> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to)),
            )
        )
        return response.records.map { SleepSession(it.metadata.id, it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }
    }

    companion object {
        /** Разрешение, которое спрашивается у пользователя. */
        val READ_SLEEP: String = HealthPermission.getReadPermission(SleepSessionRecord::class)
    }
}
