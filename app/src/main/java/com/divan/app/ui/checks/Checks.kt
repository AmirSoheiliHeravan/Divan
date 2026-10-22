package com.divan.app.ui.checks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.divan.app.core.db.entity.CheckDirection
import com.divan.app.core.db.entity.CheckItem
import com.divan.app.core.db.entity.CheckStatus
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.EmptyView
import com.divan.app.ui.common.MoneyText
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.StatusChip
import com.divan.app.ui.common.fmtDate
import com.divan.app.ui.common.expenseColor
import com.divan.app.ui.common.incomeColor
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ChecksScreen(nav: NavHostController) {
    val checks by ServiceLocator.checks.all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<CheckStatus?>(null) }

    // جمع وضعیت‌ها (پارمیس): در جریان/نزد صندوق + وصول‌شده + برگشتی
    val pending = checks.filter { it.status == CheckStatus.REGISTERED || it.status == CheckStatus.TRANSFERRED }
    val cashed = checks.filter { it.status == CheckStatus.CASHED }
    val bounced = checks.filter { it.status == CheckStatus.BOUNCED }
    val shown = if (filter == null) checks else checks.filter { it.status == filter }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "چک‌ها") { nav.popBackStack() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionCard(Modifier.weight(1f)) {
                Text("در جریان و نزد صندوق", style = MaterialTheme.typography.labelMedium)
                MoneyText(pending.sumOf { it.amountMinor }, "IRR", bold = true, fontSize = 15)
                Text("${pending.size} فقره · نزد صندوق: ${checks.filter { it.status == CheckStatus.TRANSFERRED }.size}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(Modifier.weight(1f)) {
                Text("وصول‌شده", style = MaterialTheme.typography.labelMedium, color = incomeColor)
                MoneyText(cashed.sumOf { it.amountMinor }, "IRR", bold = true, fontSize = 15)
                Text("برگشتی: ${bounced.size} فقره", fontSize = 10.5.sp, color = expenseColor)
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { showAdd = true }) { Text("+ ثبت چک") }
        if (checks.isEmpty()) EmptyView("چکی ثبت نشده است")
        // فیلتر وضعیت — گردش کامل چک (پارمیس)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("همه (${checks.size})", fontSize = 11.sp) })
            }
            items(CheckStatus.entries) { s ->
                val cnt = checks.count { it.status == s }
                FilterChip(
                    selected = filter == s,
                    onClick = { filter = if (filter == s) null else s },
                    label = { Text("${statusLabel(s)} ($cnt)", fontSize = 11.sp) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { c ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    if (c.direction == CheckDirection.RECEIVED) "چک دریافتی" else "چک پرداختی",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.padding(3.dp))
                                StatusChip(
                                    statusLabel(c.status),
                                    when (c.status) {
                                        CheckStatus.CASHED -> incomeColor
                                        CheckStatus.BOUNCED -> expenseColor
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Text(
                                "سررسید: ${fmtDate(c.dueDate)} · ${c.bankName ?: ""} ${c.chequeNumber?.let { "شماره $it" } ?: ""} · ${c.personName ?: ""}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MoneyText(c.amountMinor, c.currencyCode, bold = true)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        if (c.status == CheckStatus.REGISTERED || c.status == CheckStatus.TRANSFERRED) {
                            TextButton(onClick = { scope.launch { ServiceLocator.checks.cash(c.id, System.currentTimeMillis()) } }) {
                                Text("وصول شد")
                            }
                            TextButton(onClick = { scope.launch { ServiceLocator.checks.markBounced(c.id) } }) {
                                Text("برگشتی", color = expenseColor)
                            }
                        }
                        if (c.status == CheckStatus.BOUNCED) {
                            TextButton(onClick = { scope.launch { ServiceLocator.checks.mark(c.id, CheckStatus.SETTLED) } }) {
                                Text("تسویه شد")
                            }
                        }
                        TextButton(onClick = { scope.launch { ServiceLocator.checks.delete(c.id) } }) {
                            Text("حذف", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddCheckDialog(onDismiss = { showAdd = false }, onSave = { c -> scope.launch { ServiceLocator.checks.save(c, true); showAdd = false } })
}

fun statusLabel(s: CheckStatus): String = when (s) {
    CheckStatus.REGISTERED -> "در جریان"
    CheckStatus.TRANSFERRED -> "نزد صندوق"
    CheckStatus.CASHED -> "وصول‌شده"
    CheckStatus.BOUNCED -> "برگشتی"
    CheckStatus.CANCELED -> "باطل"
    CheckStatus.SETTLED -> "تسویه"
}

@Composable
private fun AddCheckDialog(onDismiss: () -> Unit, onSave: (CheckItem) -> Unit) {
    var direction by remember { mutableStateOf(CheckDirection.ISSUED) }
    var amount by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(System.currentTimeMillis() + 30L * 86400000) }
    var showDate by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("چک جدید") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = direction == CheckDirection.ISSUED, onClick = { direction = CheckDirection.ISSUED }, label = { Text("پرداختی") })
                    FilterChip(selected = direction == CheckDirection.RECEIVED, onClick = { direction = CheckDirection.RECEIVED }, label = { Text("دریافتی") })
                }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("مبلغ", amount) { amount = it }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.divan.app.ui.common.Field("بانک", bank, Modifier.weight(1f)) { bank = it }
                    com.divan.app.ui.common.Field("شماره چک", number, Modifier.weight(1f)) { number = it }
                }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("طرف‌حساب", person) { person = it }
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.OutlinedButton(onClick = { showDate = true }) {
                    Text("سررسید: ${fmtDate(dueDate)}")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (com.divan.app.core.money.Money.parseToMinor(amount, 0) ?: 0L) > 0,
                onClick = {
                    val ts = System.currentTimeMillis()
                    onSave(
                        CheckItem(
                            id = UUID.randomUUID().toString(),
                            direction = direction,
                            amountMinor = com.divan.app.core.money.Money.parseToMinor(amount, 0) ?: 0L,
                            bankName = bank.ifBlank { null }, chequeNumber = number.ifBlank { null },
                            issueDate = ts, dueDate = dueDate, personName = person.ifBlank { null },
                            createdAt = ts, updatedAt = ts,
                        )
                    )
                },
            ) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )

    if (showDate) {
        val st = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = dueDate)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = { st.selectedDateMillis?.let { dueDate = it }; showDate = false }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("انصراف") } },
        ) { androidx.compose.material3.DatePicker(state = st) }
    }
}
