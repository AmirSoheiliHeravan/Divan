package com.divan.app.ui.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.divan.app.core.db.entity.DebtCredit
import com.divan.app.core.db.entity.DebtDirection
import com.divan.app.core.db.entity.DebtStatus
import com.divan.app.core.money.Money
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.EmptyView
import com.divan.app.ui.common.MoneyText
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.StatusChip
import com.divan.app.ui.common.expenseColor
import com.divan.app.ui.common.incomeColor
import com.divan.app.ui.common.toPersianDigits
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun DebtsScreen(nav: NavHostController) {
    val debts by ServiceLocator.debts.all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var payTarget by remember { mutableStateOf<DebtCredit?>(null) }
    var payAmount by remember { mutableStateOf("") }

    val cps by ServiceLocator.counterparties.all().collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "بدهی‌ها و طلب‌ها") { nav.popBackStack() }
        TextButton(onClick = { showAdd = true }) { Text("+ ثبت بدهی / طلب") }
        if (debts.isEmpty()) EmptyView("موردی ثبت نشده است")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(debts, key = { it.id }) { d ->
                var paid by remember(d.id) { mutableStateOf(0L) }
                androidx.compose.runtime.LaunchedEffect(d.id) {
                    paid = ServiceLocator.debts.paidSum(d.id)
                }
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(d.personName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${if (d.direction == DebtDirection.I_OWE) "بدهی من" else "طلب من"} · ${toPersianDigits(com.divan.app.core.money.Money.format(d.amountMinor, 0, false))} مانده",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            StatusChip(
                                if (d.direction == DebtDirection.I_OWE) "بدهکار" else "بستانکار",
                                if (d.direction == DebtDirection.I_OWE) expenseColor else incomeColor,
                            )
                            if (d.status == DebtStatus.OPEN) {
                                TextButton(onClick = {
                                    payTarget = d
                                    payAmount = Money.format(d.amountMinor - paid, 0, false)
                                }) { Text("تسویه/پرداخت") }
                            } else Text("تسویه‌شده ✔", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddDebtDialog(
            counterparties = cps,
            onDismiss = { showAdd = false },
            onSave = { d -> scope.launch { ServiceLocator.debts.create(d); showAdd = false } },
        )
    }

    payTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { payTarget = null },
            title = { Text("پرداخت به/از ${target.personName}") },
            text = {
                Column {
                    com.divan.app.ui.common.Field("مبلغ", payAmount) { payAmount = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val amt = Money.parseToMinor(payAmount, 0) ?: 0L
                        if (amt > 0) ServiceLocator.debts.addPayment(target.id, amt, null, System.currentTimeMillis(), null)
                        payTarget = null
                    }
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { payTarget = null }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun AddDebtDialog(
    counterparties: List<com.divan.app.core.db.entity.Counterparty>,
    onDismiss: () -> Unit,
    onSave: (DebtCredit) -> Unit,
) {
    var direction by remember { mutableStateOf(DebtDirection.I_OWE) }
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cpId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بدهی / طلب جدید") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = direction == DebtDirection.I_OWE, onClick = { direction = DebtDirection.I_OWE }, label = { Text("من بدهکارم") })
                    FilterChip(selected = direction == DebtDirection.THEY_OWE, onClick = { direction = DebtDirection.THEY_OWE }, label = { Text("به من بدهکارند") })
                }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("نام شخص", person) { person = it }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("مبلغ", amount) { amount = it }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("یادداشت", note) { note = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = person.isNotBlank() && (Money.parseToMinor(amount, 0) ?: 0L) > 0,
                onClick = {
                    val ts = System.currentTimeMillis()
                    onSave(
                        DebtCredit(
                            id = UUID.randomUUID().toString(),
                            direction = direction, personName = person.trim(),
                            counterpartyId = counterparties.firstOrNull { it.name == person.trim() }?.id ?: cpId,
                            amountMinor = Money.parseToMinor(amount, 0) ?: 0L,
                            startDate = ts, note = note.ifBlank { null },
                            createdAt = ts, updatedAt = ts,
                        )
                    )
                },
            ) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
