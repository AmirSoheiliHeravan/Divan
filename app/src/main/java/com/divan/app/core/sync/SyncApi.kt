package com.divan.app.core.sync

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import kotlinx.serialization.Serializable

@Serializable
data class SyncBatchRequest(
    val deviceId: String,
    val ops: List<SyncOpDto>,
)

@Serializable
data class SyncOpDto(
    val id: String,          // idempotency key
    val entityType: String,
    val entityId: String,
    val op: String,
    val payload: String,
    val createdAt: Long,
)

@Serializable
data class SyncBatchResponse(
    val accepted: List<String> = emptyList(),
    val rejected: List<RejectedOp> = emptyList(),
    val conflicts: List<RejectedOp> = emptyList(),
)

@Serializable
data class RejectedOp(val id: String, val reason: String)

/** Ready-to-use API contract for the future FastAPI server.
 *  The server is expected to be idempotent per op-id and to respect
 *  family-space separation + token auth. */
interface SyncApi {
    @POST("api/v1/sync/batch")
    suspend fun pushBatch(
        @Header("Authorization") bearer: String,
        @Header("Idempotency-Key") batchKey: String,
        @Body batch: SyncBatchRequest,
    ): Response<SyncBatchResponse>
}
