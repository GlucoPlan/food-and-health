package com.glucoplan.foodhealth.data.sync

import kotlinx.serialization.json.JsonObject

/** Поддельный сервер с той же логикой, что server/app/db.py: seq, «побеждает пришедшее позже», порции. */
class FakeServer(var pageSize: Int = 1000) : SyncBackend {

    data class Rec(
        val table: String, val id: String, val data: JsonObject, val updatedAt: Long,
        val deleted: Boolean, val deviceId: String, val seq: Long,
    )

    val records = linkedMapOf<Pair<String, String>, Rec>()
    var failWith: (() -> SyncException)? = null

    /** Вызывается посреди запроса — например, чтобы изменить данные телефона во время синхронизации. */
    var duringSync: (suspend () -> Unit)? = null

    fun rec(table: String, id: String) = records[table to id]

    override suspend fun health(config: ServerConfig): Int {
        failWith?.let { throw it() }
        return records.size
    }

    override suspend fun sync(config: ServerConfig, request: SyncRequest): SyncResponse {
        failWith?.let { throw it() }
        duringSync?.invoke()
        var seq = records.values.maxOfOrNull { it.seq } ?: 0L
        val first = seq + 1
        request.changes.forEach { c ->
            seq++
            records[c.table to c.id] = Rec(c.table, c.id, c.data, c.updatedAt, c.deleted, request.deviceId, seq)
        }
        val applied = first..seq
        val rows = records.values
            .filter { it.seq > request.cursor && (request.cursor == 0L || it.deviceId != request.deviceId) && it.seq !in applied }
            .sortedBy { it.seq }
        val page = rows.take(pageSize)
        val hasMore = rows.size > pageSize
        val cursor = if (hasMore) page.last().seq else maxOf(request.cursor, records.values.maxOfOrNull { it.seq } ?: 0L)
        return SyncResponse(cursor, hasMore, page.map { WireChange(it.table, it.id, it.data, it.updatedAt, it.deleted) })
    }
}
