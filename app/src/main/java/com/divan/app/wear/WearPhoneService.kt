package com.divan.app.wear

import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.divan.app.core.db.entity.Txn
import com.divan.app.core.db.entity.TxnSource
import com.divan.app.core.db.entity.TxnStatus
import com.divan.app.core.db.entity.TxnType
import com.divan.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Calendar

/** Phone-side listener for the Wear OS companion app:
 *  – answers summary requests (balance + month totals + category list)
 *  – receives quick-expense entries from the watch and records them. */
class WearPhoneService : WearableListenerService(), MessageClient.OnMessageReceivedListener {

    @Serializable
    data class IdName(val id: String, val name: String)

    @Serializable
    data class SummaryOut(
        val balance: String, val income: String, val expense: String,
        val currency: String, val categories: List<IdName>,
    )

    @Serializable
    data class QuickExpenseIn(val amountMinor: Long, val categoryId: String? = null, val categoryName: String? = null)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            "/divan/summary_request" -> scope.launch {
                val out = buildSummary()
                runCatching {
                    com.google.android.gms.wearable.Wearable.getMessageClient(applicationContext)
                        .sendMessage(event.sourceNodeId, "/divan/summary", json.encodeToString(SummaryOut.serializer(), out).toByteArray())
                }
            }

            "/divan/quick_expense" -> {
                val msg = runCatching {
                    json.decodeFromString(QuickExpenseIn.serializer(), String(event.data))
                }.getOrNull() ?: return
                scope.launch { insertQuickExpense(msg) }
            }
        }
    }

    private suspend fun buildSummary(): SummaryOut {
        val sl = ServiceLocator
        val base = sl.currencies.baseOnce()
        val accounts = sl.accounts.allActive().first()
        var total = 0L
        accounts.forEach { a ->
            val bal = sl.accounts.balanceOf(a.id)
            total += if (a.currencyCode == base.code) bal
            else sl.txns.converter.toBase(bal, a.currencyCode, base.code)
        }
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val t = sl.txns.totalsBetween(cal.timeInMillis, System.currentTimeMillis())
        val cats = sl.categories.roots().take(8).map { IdName(it.id, it.name) }
        return SummaryOut(
            balance = com.divan.app.widget.WidgetFormat.fmt(total, base.code),
            income = com.divan.app.widget.WidgetFormat.fmt(t.income, base.code),
            expense = com.divan.app.widget.WidgetFormat.fmt(t.expense, base.code),
            currency = base.code,
            categories = cats,
        )
    }

    private suspend fun insertQuickExpense(msg: QuickExpenseIn) {
        val sl = ServiceLocator
        val base = sl.currencies.baseOnce()
        val account = sl.accounts.allActive().first().firstOrNull()
        sl.txns.create(
            Txn(
                type = TxnType.EXPENSE, amountMinor = msg.amountMinor,
                currencyCode = account?.currencyCode ?: base.code,
                accountId = account?.id,
                categoryId = msg.categoryId,
                title = msg.categoryName?.let { "ساعت: $it" } ?: "ثبت از ساعت",
                source = TxnSource.WEAR,
                status = TxnStatus.CONFIRMED,
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
