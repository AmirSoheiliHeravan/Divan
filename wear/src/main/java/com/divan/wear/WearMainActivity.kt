package com.divan.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WearSummary(
    val balance: String = "—",
    val income: String = "—",
    val expense: String = "—",
    val currency: String = "IRR",
    val categories: List<IdName> = emptyList(),
)

@Serializable
data class IdName(val id: String, val name: String)

@Serializable
data class QuickExpenseMsg(val amountMinor: Long, val categoryId: String? = null, val categoryName: String? = null)

/** Shared compose state so message callbacks can update the UI. */
object SummaryHolder {
    var summary by mutableStateOf(WearSummary())
}

class WearMainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var amountText by remember { mutableStateOf("0") }
                var selectedCat by remember { mutableStateOf<IdName?>(null) }
                var sending by remember { mutableStateOf(false) }
                val summary = SummaryHolder.summary

                fun bump(step: Long) {
                    val cur = amountText.toLongOrNull() ?: 0L
                    amountText = (cur + step).coerceAtLeast(0).toString()
                }

                Scaffold(timeText = { TimeText() }) {
                    ScalingLazyColumn(state = rememberScalingLazyListState()) {
                        item {
                            Text("دیوان", style = MaterialTheme.typography.title2)
                        }
                        item {
                            Text("موجودی کل", style = MaterialTheme.typography.caption1)
                            Text(summary.balance, style = MaterialTheme.typography.display2)
                        }
                        item {
                            Text(
                                "درآمد ماه: ${summary.income}  |  هزینه: ${summary.expense}",
                                style = MaterialTheme.typography.caption1,
                            )
                        }
                        item {
                            Button(onClick = { scope.launch { WearSummaryClient.request(this@WearMainActivity) } }) {
                                Text("🔄 به‌روزرسانی از گوشی")
                            }
                        }
                        item { Text("ثبت سریع هزینه:", style = MaterialTheme.typography.title3) }
                        item {
                            androidx.wear.compose.foundation.layout.Row {
                                Button(onClick = { bump(1000L) }) { Text("+۱k") }
                                Button(onClick = { bump(10000L) }) { Text("+۱۰k") }
                                Button(onClick = { bump(100000L) }) { Text("+۱۰۰k") }
                            }
                        }
                        item {
                            androidx.wear.compose.foundation.layout.Row {
                                Button(onClick = { bump(-1000L) }) { Text("−۱k") }
                                Button(onClick = { bump(-10000L) }) { Text("−۱۰k") }
                                Button(onClick = { bump(-100000L) }) { Text("−۱۰۰k") }
                            }
                        }
                        item {
                            Text("مبلغ: ${amountText} ریال", style = MaterialTheme.typography.title3)
                        }
                        if (summary.categories.isNotEmpty()) {
                            items(summary.categories) { c ->
                                Chip(
                                    onClick = { selectedCat = c },
                                    label = { Text(if (selectedCat?.id == c.id) "✔ ${c.name}" else c.name) },
                                )
                            }
                        }
                        item {
                            Button(
                                onClick = {
                                    val amt = amountText.toLongOrNull() ?: 0L
                                    if (amt > 0) {
                                        sending = true
                                        scope.launch {
                                            val ok = WearSummaryClient.sendQuickExpense(
                                                this@WearMainActivity,
                                                QuickExpenseMsg(amt, selectedCat?.id, selectedCat?.name),
                                            )
                                            sending = false
                                            if (ok) amountText = "0"
                                        }
                                    }
                                },
                            ) { Text(if (sending) "در حال ارسال…" else "✓ ثبت روی گوشی") }
                        }
                        item {
                            Text(
                                "گوشی باید روشن و برنامه نصب باشد؛ پس از اتصال ثبت می‌شود.",
                                style = MaterialTheme.typography.caption2,
                            )
                        }
                    }
                }

                scope.launch { WearSummaryClient.request(this@WearMainActivity) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == WearSummaryClient.PATH_SUMMARY) {
            val s = runCatching { json.decodeFromString(WearSummary.serializer(), String(event.data)) }.getOrNull() ?: return
            runOnUiThread { SummaryHolder.summary = s }
        }
    }
}
