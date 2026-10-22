package com.divan.app.core.sync

import com.divan.app.core.db.dao.SyncDao
import com.divan.app.core.db.entity.AuditAction
import com.divan.app.core.db.entity.SyncOp
import com.divan.app.core.db.entity.SyncStatus
import kotlinx.serialization.json.Json
import java.util.UUID

/** Offline-first sync queue: every financial write is enqueued with a unique
 *  idempotency key. When a server is configured, SyncWorker drains the queue;
 *  otherwise operations stay PENDING without blocking local work. */
class SyncQueue(private val dao: SyncDao) {

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun enqueue(entityType: String, entityId: String, action: AuditAction, payload: Any?) {
        val op = opType(action)
        val body = when {
            payload == null -> "{}"
            payload is String -> payload
            else -> runCatching { json.encodeToString(kotlinx.serialization.serializer(payload.javaClass), payload) }.getOrNull() ?: "{}"
        }
        dao.enqueue(
            SyncOp(
                id = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                op = op,
                payload = body,
                createdAt = System.currentTimeMillis(),
                status = SyncStatus.PENDING,
            )
        )
    }

    private fun opType(action: AuditAction): String = when (action) {
        AuditAction.INSERT -> "INSERT"
        AuditAction.UPDATE, AuditAction.CONFIRM, AuditAction.RESTORE, AuditAction.SETTINGS -> "UPDATE"
        AuditAction.DELETE, AuditAction.ARCHIVE -> "DELETE"
    }

    suspend fun pendingCountFlow() = dao.pendingCount()
}
