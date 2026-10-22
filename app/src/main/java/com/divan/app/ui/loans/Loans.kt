package com.divan.app.ui.loans

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
import androidx.compose.runtime.LaunchedEffect
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
import com.divan.app.core.db.entity.Installment
import com.divan.app.core.db.entity.InstallmentStatus
import com.divan.app.core.db.entity.Loan
import com.divan.app.core.db.entity.LoanDirection
import com.divan.app.core.db.entity.LoanPeriodUnit
import com.divan.app.core.db.entity.LoanRateMode
import com.divan.app.core.money.Money
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.EmptyView
import com.divan.app.ui.common.EntityDropdown
import com.divan.app.ui.common.MoneyText
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.StatusChip
import com.divan.app.ui.common.fmtDate
import com.divan.app.ui.common.expenseColor
import com.divan.app.ui.common.incomeColor
import com.divan.app.ui.nav.Routes
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

@Composable
fun LoansScreen(nav: NavHostController) {
    val loans by ServiceLocator.loans.all().collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "وام‌ها و اقساط") { nav.popBackStack() }
        TextButton(onClick = { nav.navigate(Routes.loanEdit()) }) { Text("+ وام جدید") }
        if (loans.isEmpty()) EmptyView("وامی ثبت نشده است")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(loans, key = { it.id }) { loan ->
                LoanCard(loan) { nav.navigate(Routes.loanDetail(loan.id)) }
            }
        }
    }
}

@Composable
private fun LoanCard(loan: Loan, onOpen: () -> Unit) {
    var remaining by remember { mutableStateOf(0L) }
    var paidTotal by remember { mutableStateOf(0L) }
    LaunchedEffect(loan.id) {
        remaining = ServiceLocator.loans.loanRemaining(loan.id)
        val p = ServiceLocator.loans.paidStats(loan.id); paidTotal = p.first
    }
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(loan.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${if (loan.direction == LoanDirection.TAKEN) "وام گرفته‌شده" else "وام داده‌شده"} · ${loan.installmentsCount} قسط · ${loan.rateMode}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(
                if (loan.direction == LoanDirection.TAKEN) "بدهی" else "طلب",
                if (loan.direction == LoanDirection.TAKEN) expenseColor else incomeColor,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("مانده", fontSize = 11.sp)
                MoneyText(remaining, loan.currencyCode, bold = true, fontSize = 15)
            }
            Column {
                Text("پرداخت‌شده", fontSize = 11.sp)
                MoneyText(paidTotal, loan.currencyCode, fontSize = 15)
            }
            TextButton(onClick = onOpen) { Text("اقساط ›") }
        }
    }
}

@Composable
fun LoanEditScreen(nav: NavHostController, id: String) {
    val scope = rememberCoroutineScope()
    val existing = remember(id) { kotlinx.coroutines.runBlocking { id.takeIf { it.isNotBlank() }?.let { ServiceLocator.loans.byId(it) } } }
    val accounts by ServiceLocator.accounts.allActive().collectAsState(initial = emptyList())
    val cps by ServiceLocator.counterparties.all().collectAsState(initial = emptyList())

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var direction by remember { mutableStateOf(existing?.direction ?: LoanDirection.TAKEN) }
    var cpId by remember { mutableStateOf(existing?.counterpartyId) }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(existing?.ratePercent?.toString() ?: "0") }
    var rateMode by remember { mutableStateOf(existing?.rateMode ?: LoanRateMode.NONE) }
    var count by remember { mutableStateOf(existing?.installmentsCount?.toString() ?: "12") }
    var unit by remember { mutableStateOf(existing?.periodUnit ?: LoanPeriodUnit.MONTH) }
    var dueDay by remember { mutableStateOf((existing?.dueDay ?: 5).toString()) }
    var accountId by remember { mutableStateOf<String?>(null) }
    var collateral by remember { mutableStateOf(existing?.collateral ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var disburse by remember { mutableStateOf(true) }

    LaunchedEffect(existing) {
        existing?.let {
            val d = ServiceLocator.currencies.byCode(it.currencyCode)?.decimals ?: 0
            principal = Money.format(it.principalMinor, d, false)
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState())) {
        CurrencyTopBar(title = if (existing == null) "وام جدید" else "ویرایش وام") { nav.popBackStack() }
        SectionCard {
            com.divan.app.ui.common.Field("عنوان", title) { title = it }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = direction == LoanDirection.TAKEN, onClick = { direction = LoanDirection.TAKEN }, label = { Text("گرفته‌ام") })
                FilterChip(selected = direction == LoanDirection.GIVEN, onClick = { direction = LoanDirection.GIVEN }, label = { Text("داده‌ام") })
            }
            Spacer(Modifier.height(6.dp))
            EntityDropdown(
                label = "طرف‌حساب (وام‌دهنده/گیرنده)", items = cps,
                selectedId = cpId, getId = { it.id }, getText = { it.name },
                onSelect = { cpId = it.id },
            )
            Spacer(Modifier.height(6.dp))
            com.divan.app.ui.common.Field("مبلغ اصل وام", principal) { principal = it }
            Spacer(Modifier.height(6.dp))
            Text("روش سود", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = rateMode == LoanRateMode.NONE, onClick = { rateMode = LoanRateMode.NONE }, label = { Text("بدون سود") })
                FilterChip(selected = rateMode == LoanRateMode.FLAT, onClick = { rateMode = LoanRateMode.FLAT }, label = { Text("سود ثابت") })
                FilterChip(selected = rateMode == LoanRateMode.SIMPLE, onClick = { rateMode = LoanRateMode.SIMPLE }, label = { Text("مستهلک‌شونده") })
            }
            if (rateMode != LoanRateMode.NONE) {
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("نرخ سود سالانه (٪)", rate) { rate = it }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.divan.app.ui.common.Field("تعداد اقساط", count, Modifier.weight(1f)) { count = it }
                com.divan.app.ui.common.Field("روز سررسید ماه", dueDay, Modifier.weight(1f)) { dueDay = it }
            }
            Spacer(Modifier.height(6.dp))
            Text("دوره پرداخت", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(LoanPeriodUnit.WEEK, LoanPeriodUnit.MONTH, LoanPeriodUnit.YEAR).forEach { u ->
                    FilterChip(selected = unit == u, onClick = { unit = u },
                        label = { Text(when (u) { LoanPeriodUnit.WEEK -> "هفتگی"; LoanPeriodUnit.MONTH -> "ماهانه"; else -> "سالانه" }) })
                }
            }
            Spacer(Modifier.height(6.dp))
            EntityDropdown(
                label = "حساب دریافت/پرداخت", items = accounts,
                selectedId = accountId, getId = { it.id }, getText = { it.name },
                onSelect = { accountId = it.id },
            )
            Spacer(Modifier.height(6.dp))
            com.divan.app.ui.common.Field("وثیقه / ضامن (اختیاری)", collateral) { collateral = it }
            Spacer(Modifier.height(6.dp))
            com.divan.app.ui.common.Field("یادداشت", note) { note = it }
            if (existing == null && direction == LoanDirection.TAKEN) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = disburse, onCheckedChange = { disburse = it })
                    Text("ثبت دریافت وجه وام در حساب", fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = title.isNotBlank() && (Money.parseToMinor(principal, 0) ?: 0L) > 0,
            onClick = {
                scope.launch {
                    val ts = System.currentTimeMillis()
                    val p = Money.parseToMinor(principal, 0) ?: 0L
                    val loan = Loan(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        direction = direction, title = title.trim(),
                        counterpartyId = cpId, principalMinor = p,
                        ratePercent = rate.toDoubleOrNull() ?: 0.0, rateMode = rateMode,
                        installmentsCount = count.toIntOrNull() ?: 0,
                        periodUnit = unit, startDate = existing?.startDate ?: ts,
                        dueDay = dueDay.toIntOrNull() ?: 1,
                        collateral = collateral.ifBlank { null },
                        accountId = accountId, note = note.ifBlank { null },
                        createdAt = existing?.createdAt ?: ts, updatedAt = ts,
                    )
                    ServiceLocator.loans.create(loan, alsoDisburse = disburse && existing == null, accountId = accountId)
                    nav.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("ذخیره و ساخت برنامه اقساط") }
    }
}

@Composable
fun LoanDetailScreen(nav: NavHostController, id: String) {
    val loan by ServiceLocator.loans.byIdFlow(id).collectAsState(initial = null)
    val insts by ServiceLocator.loans.installments(id).collectAsState(initial = emptyList())
    val accounts by ServiceLocator.accounts.allActive().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var payTarget by remember { mutableStateOf<Installment?>(null) }
    var payAmount by remember { mutableStateOf("") }
    var payAccount by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = loan?.title ?: "وام") { nav.popBackStack() }
        loan ?: return@Column
        Text("مانده کل: ", fontSize = 13.sp)
        MoneyText(
            insts.sumOf { it.principalMinor + it.interestMinor - it.paidMinor },
            loan.currencyCode, bold = true, fontSize = 18,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(insts, key = { it.id }) { inst ->
                val total = inst.principalMinor + inst.interestMinor
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column {
                            Text("قسط ${inst.seq}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "سررسید: ${fmtDate(inst.dueDate)}",
                                fontSize = 12.sp,
                                color = if (inst.dueDate < System.currentTimeMillis() && inst.status != InstallmentStatus.PAID) expenseColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "اصل ${inst.principalMinor / 1000} + سود ${inst.interestMinor / 1000}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            StatusChip(
                                when (inst.status) {
                                    InstallmentStatus.PAID -> "پرداخت‌شده"
                                    InstallmentStatus.PARTIAL -> "نیمه‌پرداخت"
                                    InstallmentStatus.LATE -> "معوق"
                                    else -> "در انتظار"
                                },
                                when (inst.status) {
                                    InstallmentStatus.PAID -> incomeColor
                                    InstallmentStatus.PARTIAL -> com.divan.app.ui.theme.Gold
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            MoneyText(total - inst.paidMinor, loan.currencyCode, bold = true, fontSize = 14)
                            if (inst.status != InstallmentStatus.PAID) {
                                TextButton(onClick = {
                                    payTarget = inst
                                    payAmount = Money.format(total - inst.paidMinor, 0, false)
                                    payAccount = accounts.firstOrNull()?.id
                                }) { Text("پرداخت") }
                            }
                        }
                    }
                }
            }
        }
    }

    payTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { payTarget = null },
            title = { Text("پرداخت قسط ${target.seq}") },
            text = {
                Column {
                    com.divan.app.ui.common.Field("مبلغ پرداختی", payAmount) { payAmount = it }
                    Spacer(Modifier.height(6.dp))
                    EntityDropdown(
                        label = "از حساب", items = accounts,
                        selectedId = payAccount, getId = { it.id }, getText = { it.name },
                        onSelect = { payAccount = it.id },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val amt = Money.parseToMinor(payAmount, 0) ?: return@launch
                        ServiceLocator.loans.payInstallment(
                            target.id, amt, payAccount,
                            System.currentTimeMillis(),
                            com.divan.app.core.db.entity.PayMethod.TRANSFER,
                        )
                        payTarget = null
                    }
                }) { Text("پرداخت و ثبت") }
            },
            dismissButton = { TextButton(onClick = { payTarget = null }) { Text("انصراف") } },
        )
    }
}
