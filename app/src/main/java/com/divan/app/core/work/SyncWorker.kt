package com.divan.app.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.divan.app.core.db.entity.SyncOp
import com.divan.app.core.db.entity.SyncStatus
import com.divan.app.core.sync.SyncBatchRequest
import com.divan.app.core.sync.SyncBatchResponse
import com.divan.app.core.sync.SyncOpDto
import com.divan.app.di.ServiceLocator
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Drains the local sync queue to the configured server (FastAPI-ready).
 * If no server URL is configured, operations stay PENDING (offline-first);
 * the queue is preserved – nothing is lost and nothing is sent.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun doWork(): Result {
        val sl = ServiceLocator
        val url = sl.settings.serverUrl.first()
        val token = sl.settings.serverToken.first()
        if (url.isBlank()) return Result.success() // offline mode – queue intact

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val mediaType = "application/json".toMediaType()
        val endpoint = (if (url.endsWith("/")) url else "$url/") + "api/v1/sync/batch"

        return runCatching {
            var allOk = true
            while (true) {
                val batch: List<SyncOp> = sl.db.syncDao().pendingBatch(50)
                if (batch.isEmpty()) break

                val body = json.encodeToString(
                    SyncBatchRequest.serializer(),
                    SyncBatchRequest(
                        deviceId = sl.settings.deviceIdNow(),
                        ops = batch.map {
                            SyncOpDto(it.id, it.entityType, it.entityId, it.op, it.payload, it.createdAt)
                        },
                    ),
                )
                val req = Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer $token")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .post(body.toRequestBody(mediaType))
                    .build()

                val resp = client.newCall(req).execute()
                resp.use { r ->
                    if (!r.isSuccessful) {
                        batch.forEach { mark(it, SyncStatus.FAILED, "HTTP ${r.code}") }
                        allOk = false
                        return@runCatching Result.retry()
                    }
                    val text = r.body?.string() ?: ""
                    val parsed = runCatching {
                        json.decodeFromString(SyncBatchResponse.serializer(), text)
                    }.getOrNull()
                    if (parsed != null) {
                        parsed.accepted.forEach { id ->
                            sl.db.syncDao().byId(id)?.let { mark(it, SyncStatus.DONE, null) }
                        }
                        (parsed.rejected + parsed.conflicts).forEach { bad ->
                            sl.db.syncDao().byId(bad.id)?.let { mark(it, SyncStatus.FAILED, bad.reason) }
                        }
                    } else {
                        batch.forEach { mark(it, SyncStatus.DONE, null) }
                    }
                }
                if (batch.size < 50) break
            }
            if (allOk) Result.success() else Result.retry()
        }.getOrElse { Result.retry() }
    }

    private suspend fun mark(op: SyncOp, status: SyncStatus, err: String?) {
        runCatching {
            ServiceLocator.db.syncDao().update(
                op.copy(status = status, attempts = op.attempts + 1, lastError = err)
            )
        }
    }
}
