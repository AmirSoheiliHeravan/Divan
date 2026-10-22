package com.divan.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Phone↔Watch messaging via the Wearable Data Layer API. */
object WearSummaryClient {
    const val PATH_SUMMARY_REQUEST = "/divan/summary_request"
    const val PATH_SUMMARY = "/divan/summary"
    const val PATH_QUICK_EXPENSE = "/divan/quick_expense"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun request(context: Context): Boolean = runCatching {
        val client = Wearable.getMessageClient(context)
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        var sent = false
        nodes.forEach { node ->
            runCatching {
                client.sendMessage(node.id, PATH_SUMMARY_REQUEST, ByteArray(0)).await()
                sent = true
            }
        }
        sent
    }.getOrDefault(false)

    suspend fun sendQuickExpense(context: Context, msg: QuickExpenseMsg): Boolean = runCatching {
        val client = Wearable.getMessageClient(context)
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        val body = json.encodeToString(msg).toByteArray(Charsets.UTF_8)
        var sent = false
        nodes.forEach { node ->
            runCatching {
                client.sendMessage(node.id, PATH_QUICK_EXPENSE, body).await()
                sent = true
            }
        }
        sent
    }.getOrDefault(false)

    /** Phone side: publish summary as a DataItem (watch can read it anytime). */
    fun publishSummary(context: Context, summaryJson: String) {
        runCatching {
            val req = PutDataMapRequest.create("/divan/summary_data").apply {
                dataMap.putString("json", summaryJson)
                dataMap.putLong("ts", System.currentTimeMillis())
            }
            Wearable.getDataClient(context).putDataItem(req.asPutDataRequest().setUrgent())
        }
    }
}
