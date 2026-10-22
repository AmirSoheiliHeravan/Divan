package com.divan.app.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import com.divan.app.core.db.entity.PayMethod
import com.divan.app.core.db.entity.Recurring
import com.divan.app.core.db.entity.RecurStatus
import com.divan.app.core.db.entity.RecurUnit
import com.divan.app.core.db.entity.TxnType
import com.divan.app.core.money.Money
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.EmptyView
import com.divan.app.ui.common.EntityDropdown
import com.divan.app.ui.common.MoneyText
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.StatusChip
import com.divan.app.ui.common.fmtDate
import com.divan.app.ui.dashboard.typeLabel
import com.divan.app.ui.nav.Routes
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun RecurringScreen(nav: NavHostController) {
    val items by ServiceLocator.recurring.all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    val accounts by ServiceLocator.accounts.allActive().collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "پرداخت‌های دوره‌ای") { nav.popBackStack() }
        TextButton(onClick = { showAdd = true }) { Text("+ قالب دوره‌ای جدید") }
        if (items.isEmpty()) EmptyView("قالبی تعریف نشده است – حقوق، اجاره و قبض‌ها را اینجا برنامه‌ریزی کنید")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { r ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${typeLabel(r.type)} · هر ${r.intervalCount} ${unitLabel(r.intervalUnit)} · اجرای بعدی: ${fmtDate(r.nextRun)}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (r.autoCreate) "ثبت خودکار فعال" else "فقط یادآوری",
                                fontSize = 11.sp,
                                color = if (r.autoCreate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            MoneyText(r.amountMinor, r.currencyCode, bold = true)
                            if (r.status == RecurStatus.ACTIVE) {
                                TextButton(onClick = {
                                    scope.launch {
                                        ServiceLocator.recurring.save(r.copy(status = RecurStatus.PAUSED), false)
                                    }
                                }) { Text("توقف") }
                            } else {
                                TextButton(onClick = {
                                    scope.launch { ServiceLocator.recurring.save(r.copy(status = RecurStatus.ACTIVE), false) }
                                }) { Text("فعال") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddRecurringDialog(
            accounts = accounts,
            onDismiss = { showAdd = false },
            onSave = { r -> scope.launch { ServiceLocator.recurring.save(r, true); showAdd = false } },
        )
    }
}

fun unitLabel(u: RecurUnit): String = when (u) {
    RecurUnit.DAY -> "روز"
    RecurUnit.WEEK -> "هفته"
    RecurUnit.MONTH -> "ماه"
    RecurUnit.YEAR -> "سال"
}

@Composable
private fun AddRecurringDialog(
    accounts: List<com.divan.app.core.db.entity.Account>,
    onDismiss: () -> Unit,
    onSave: (Recurring) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxnType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<String?>(null) }
    var unit by remember { mutableStateOf(RecurUnit.MONTH) }
    var interval by remember { mutableStateOf("1") }
    var autoCreate by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قالب دوره‌ای") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = type == TxnType.EXPENSE, onClick = { type = TxnType.EXPENSE }, label = { Text("هزینه") })
                    FilterChip(selected = type == TxnType.INCOME, onClick = { type = TxnType.INCOME }, label = { Text("درآمد") })
                }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("عنوان (مثلاً اجاره)", title) { title = it }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("مبلغ", amount) { amount = it }
                Spacer(Modifier.height(6.dp))
                EntityDropdown(
                    label = "حساب", items = accounts,
                    selectedId = accountId, getId = { it.id }, getText = { it.name },
                    onSelect = { accountId = it.id },
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.divan.app.ui.common.Field("هر", interval, Modifier.weight(0.5f)) { interval = it }
                    unitLabel(unit)
                    FilterChip(selected = unit == RecurUnit.DAY, onClick = { unit = RecurUnit.DAY }, label = { Text("روز") })
                    FilterChip(selected = unit == RecurUnit.WEEK, onClick = { unit = RecurUnit.WEEK }, label = { Text("هفته") })
                    FilterChip(selected = unit == RecurUnit.MONTH, onClick = { unit = RecurUnit.MONTH }, label = { Text("ماه") })
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = autoCreate, onCheckedChange = { autoCreate = it })
                    Text("ثبت خودکار تراکنش در سررسید", fontSize = 13.sp)
                }
                Text(
                    "در حالت یادآوری، اعلان می‌گیرید و خودتان ثبت می‌کنید.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && (Money.parseToMinor(amount, 0) ?: 0L) > 0 && accountId != null,
                onClick = {
                    val ts = System.currentTimeMillis()
                    onSave(
                        Recurring(
                            id = UUID.randomUUID().toString(),
                            title = title.trim(), type = type,
                            amountMinor = Money.parseToMinor(amount, 0) ?: 0L,
                            accountId = accountId,
                            intervalUnit = unit, intervalCount = interval.toIntOrNull() ?: 1,
                            startDate = ts, nextRun = ts,
                            autoCreate = autoCreate, method = PayMethod.CASH,
                            createdAt = ts, updatedAt = ts,
                        )
                    )
                },
            ) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
