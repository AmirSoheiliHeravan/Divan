package com.divan.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.divan.app.core.db.entity.SmsRule
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.SectionCard
import kotlinx.coroutines.launch
import java.util.UUID

/** Bank SMS rule manager: list built-in Iranian banks + add ANY bank of any country. */
@Composable
fun SmsRulesScreen(nav: NavHostController) {
    val rules by ServiceLocator.sms.rules().collectAsState(initial = emptyList())
    val inbox by ServiceLocator.sms.inbox().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<SmsRule?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "قواعد پیامک بانکی") { nav.popBackStack() }
        Text(
            "بانک‌های ایران به‌صورت پیش‌فرض تعریف شده‌اند. برای بانک هر کشوری، شماره فرستنده و الگوی پیام را با گروه‌های نام‌دار (amount، card، balance) وارد کنید.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Button(onClick = { adding = true }) { Text("+ افزودن بانک جدید") }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            rules.forEach { r ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${r.bankName} — ${r.sender}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (r.pattern.length > 60) r.pattern.take(60) + "…" else r.pattern,
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = r.enabled, onCheckedChange = { on ->
                            scope.launch { ServiceLocator.sms.setEnabled(r.id, on) }
                        })
                    }
                    Row {
                        TextButton(onClick = { editing = r }) { Text("ویرایش") }
                        if (!r.isBuiltIn) {
                            TextButton(onClick = { scope.launch { ServiceLocator.sms.deleteCustom(r.id) } }) {
                                Text("حذف", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.padding(2.dp))
            }
            if (inbox.isNotEmpty()) {
                SectionCard(title = "📩 صندوق پیامک‌های دریافتی") {
                    inbox.take(30).forEach { item ->
                        Column(Modifier.padding(vertical = 3.dp)) {
                            Text(
                                "${item.sender} · ${com.divan.app.ui.common.fmtDate(item.date)} · ${item.status}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(item.body.take(90), fontSize = 12.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        val initial = editing
        RuleEditorDialog(
            initial = initial,
            onDismiss = { adding = false; editing = null },
            onSave = { r ->
                scope.launch {
                    if (initial == null) ServiceLocator.sms.saveRule(r) else ServiceLocator.sms.updateRule(r)
                    adding = false; editing = null
                }
            },
        )
    }
}

@Composable
private fun RuleEditorDialog(initial: SmsRule?, onDismiss: () -> Unit, onSave: (SmsRule) -> Unit) {
    var bank by remember { mutableStateOf(initial?.bankName ?: "") }
    var sender by remember { mutableStateOf(initial?.sender ?: "") }
    var pattern by remember { mutableStateOf(initial?.pattern ?: "(?:مبلغ|به مبلغ)\\s*(?<amount>[\\d,،]+)\\s*ریال.*?(?:کارت\\s*\\d{4}\\*{2,}(?<card>\\d{4}))?") }
    var depKw by remember { mutableStateOf(initial?.depositKeywords ?: "واریز|وصول|شارژ") }
    var wdKw by remember { mutableStateOf(initial?.withdrawKeywords ?: "برداشت|خرید|پرداخت") }
    var sample by remember { mutableStateOf(initial?.sample ?: "") }
    var testResult by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "بانک جدید" else "ویرایش قاعده") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                com.divan.app.ui.common.Field("نام بانک", bank) { bank = it }
                Spacer(Modifier.height(4.dp))
                com.divan.app.ui.common.Field("شماره فرستنده (مثلاً 9870005151 یا +49...)", sender) { sender = it }
                Spacer(Modifier.height(4.dp))
                com.divan.app.ui.common.Field("الگوی Regex با گروه‌های amount/card/balance", pattern) { pattern = it }
                Spacer(Modifier.height(4.dp))
                com.divan.app.ui.common.Field("کلیدواژه‌های واریز (با | جدا شود)", depKw) { depKw = it }
                Spacer(Modifier.height(4.dp))
                com.divan.app.ui.common.Field("کلیدواژه‌های برداشت", wdKw) { wdKw = it }
                Spacer(Modifier.height(4.dp))
                com.divan.app.ui.common.Field("پیامک نمونه برای تست", sample) { sample = it }
                Spacer(Modifier.height(6.dp))
                val scope2 = rememberCoroutineScope()
                TextButton(onClick = {
                    scope2.launch {
                        val rule = draft(bank, sender, pattern, depKw, wdKw)
                        val res = ServiceLocator.smsParser.test(rule, sample.ifBlank { "مبلغ 250,000 ریال برداشت" })
                        testResult = "مبلغ=${res.amountMinor ?: "؟"} کارت=${res.cardLast4 ?: "؟"} نوع=${res.direction ?: "؟"}"
                    }
                }) { Text("🧪 تست الگو") }
                if (testResult.isNotEmpty()) Text(testResult, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(
                enabled = bank.isNotBlank() && sender.isNotBlank(),
                onClick = { onSave(draft(bank, sender, pattern, depKw, wdKw).let { if (initial != null) it.copy(id = initial.id, isBuiltIn = false) else it }) },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun draft(bank: String, sender: String, pattern: String, depKw: String, wdKw: String): SmsRule =
    SmsRule(
        id = UUID.randomUUID().toString(), bankName = bank.trim(), sender = sender.trim().replace("+", ""),
        pattern = pattern, depositKeywords = depKw, withdrawKeywords = wdKw,
        balanceKeywords = "موجودی|مانده", isBuiltIn = false, sample = null,
        createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
    )
