package com.divan.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.divan.app.core.db.entity.Account
import com.divan.app.core.db.entity.AccountStatus
import com.divan.app.core.db.entity.AccountType
import com.divan.app.core.money.Money
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.EntityDropdown
import com.divan.app.ui.common.EmptyView
import com.divan.app.ui.common.MoneyText
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.expenseColor
import com.divan.app.ui.common.fmtMoney
import com.divan.app.ui.common.incomeColor
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AccountsScreen(nav: NavHostController) {
    val accounts by ServiceLocator.accounts.all().collectAsState(initial = emptyList())
    var balances by remember { mutableStateOf(mapOf<String, Long>()) }
    var stmtFor by remember { mutableStateOf<com.divan.app.core.db.entity.Account?>(null) }
    LaunchedEffect(accounts) {
        balances = accounts.associate { it.id to ServiceLocator.accounts.balanceOf(it.id) }
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "حساب‌ها و کیف پول‌ها") { nav.popBackStack() }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { nav.navigate(Routes.accountEdit()) }) { Text("+ حساب جدید") }
        if (accounts.isEmpty()) EmptyView("هنوز حسابی اضافه نشده است")
        // گروه‌بندی سه‌سطحی حساب‌ها (پارمیس): گروه ← بانک/ارائه‌دهنده ← حساب، با جمع هر گروه
        val grouped = remember(accounts) {
            accounts.sortedBy { accountGroupOrder(it.type) }.groupBy { accountGroupOf(it.type) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            grouped.forEach { (g, list) ->
                item(key = "hdr_$g") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(g, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${list.size} حساب · جمع: ${fmtMoney(list.sumOf { balances[it.id] ?: 0L }, "IRR")}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(list, key = { it.id }) { a ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                com.divan.app.ui.common.ColorDot(a.colorHex)
                                Spacer(Modifier.padding(3.dp))
                                Text(a.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                listOfNotNull(
                                    a.bankName,
                                    typeLabel(a.type),
                                    a.cardLast4?.let { "کارت $it" },
                                    if (a.status != AccountStatus.ACTIVE) "(${statusLabel(a.status)})" else null,
                                ).joinToString(" · "),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            MoneyText(balances[a.id] ?: 0, a.currencyCode, bold = true)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        TextButton(onClick = { stmtFor = a }) { Text("گردش حساب") }
                        TextButton(onClick = { nav.navigate(Routes.accountEdit(a.id)) }) { Text("ویرایش") }
                        TextButton(onClick = {
                            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                            scope.launch { ServiceLocator.accounts.delete(a.id) }
                        }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                }
                }
            }
        }
    }

    // صورتحساب و ریز گردش (الهام از پارمیس)
    stmtFor?.let { acc ->
        AccountStatementDialog(account = acc, onDismiss = { stmtFor = null })
    }
}

/** گردش حساب: تراکنش‌های حساب + مانده رونده + خروجی CSV */
@Composable
private fun AccountStatementDialog(account: com.divan.app.core.db.entity.Account, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var rows by remember(account.id) { mutableStateOf<List<Triple<com.divan.app.core.db.entity.Txn, Long, Long>>>(emptyList()) }

    LaunchedEffect(account.id) {
        val all = runCatching { ServiceLocator.txns.allOnce() }.getOrDefault(emptyList())
            .filter { it.accountId == account.id || it.toAccountId == account.id }
            .sortedBy { it.date }
        val current = runCatching { ServiceLocator.accounts.balanceOf(account.id) }.getOrDefault(0L)
        val sum = all.sumOf { stmtDelta(it, account.id) }
        var run = current - sum
        rows = all.map { t ->
            val d = stmtDelta(t, account.id)
            run += d
            Triple(t, d, run)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val head = listOf("تاریخ", "شرح", "مبلغ (${account.currencyCode})", "مانده پس از تراکنش")
                val body = rows.map { (t, d, run) ->
                    listOf(
                        com.divan.app.ui.util.Jalali.fmt(t.date),
                        t.title.ifBlank { "تراکنش" },
                        (if (d >= 0) "+" else "-") + (d / 1_000_000.0).let { String.format(java.util.Locale.US, "%.0f", kotlin.math.abs(it)) },
                        (run / 1_000_000.0).let { String.format(java.util.Locale.US, "%.0f", it) },
                    )
                }
                val csv = "\uFEFF" + (listOf(head) + body).joinToString("\n") { r -> r.joinToString(",") { "\"$it\"" } }
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, csv)
                    putExtra(android.content.Intent.EXTRA_TITLE, "گردش حساب ${account.name}")
                }
                ctx.startActivity(android.content.Intent.createChooser(intent, "اشتراک گردش حساب"))
            }) { Text("خروجی / اشتراک CSV") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
        title = { Text("گردش حساب · ${account.name}", fontSize = 16.sp) },
        text = {
            if (rows.isEmpty()) {
                Text("تراکنشی برای این حساب ثبت نشده است", fontSize = 13.sp)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(rows, key = { it.first.id }) { (t, d, run) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title.ifBlank { "تراکنش" }, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    com.divan.app.ui.util.Jalali.fmt(t.date),
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    (if (d >= 0) "+" else "−") + fmtMoney(kotlin.math.abs(d), account.currencyCode),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (d >= 0) incomeColor else expenseColor,
                                )
                                Text("مانده: " + fmtMoney(run, account.currencyCode), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
    )
}

/** تغییر مانده حساب از دید این حساب (منطبق با balanceOf) */
private fun stmtDelta(t: com.divan.app.core.db.entity.Txn, accountId: String): Long = when {
    t.accountId == accountId -> com.divan.app.core.money.Money.signedDelta(t.type, t.amountMinor)
    t.toAccountId == accountId && t.type == com.divan.app.core.db.entity.TxnType.TRANSFER -> t.amountMinor
    else -> 0L
}

/** گروه سطح‌یک حساب برای نمایش سه‌سطحی (پارمیس) */
fun accountGroupOf(t: AccountType): String = when (t) {
    AccountType.CHECKING, AccountType.SAVINGS, AccountType.CARD, AccountType.JOINT -> "حساب‌های بانکی"
    AccountType.CASH -> "نقد و کیف پول"
    AccountType.FOREIGN, AccountType.DIGITAL_WALLET -> "ارز و کیف دیجیتال"
    AccountType.INVESTMENT -> "سرمایه‌گذاری"
    else -> "صندوق‌ها و سایر"
}

fun accountGroupOrder(t: AccountType): Int = when (t) {
    AccountType.CHECKING, AccountType.SAVINGS, AccountType.CARD, AccountType.JOINT -> 0
    AccountType.CASH -> 1
    AccountType.FOREIGN, AccountType.DIGITAL_WALLET -> 2
    AccountType.INVESTMENT -> 3
    else -> 4
}

fun typeLabel(t: AccountType): String = when (t) {
    AccountType.CHECKING -> "جاری"
    AccountType.SAVINGS -> "پس‌انداز"
    AccountType.FOREIGN -> "ارزی"
    AccountType.CARD -> "کارت بانکی"
    AccountType.CASH -> "نقد"
    AccountType.DIGITAL_WALLET -> "کیف پول دیجیتال"
    AccountType.FAMILY_FUND -> "صندوق خانوادگی"
    AccountType.JOINT -> "مشترک"
    AccountType.GOAL_SAVINGS -> "پس‌انداز هدفمند"
    AccountType.INVESTMENT -> "سرمایه‌گذاری"
    AccountType.DEPOSIT_HELD -> "ودیعه/امانی"
    AccountType.DEBT_TRACK -> "بدهی و مطالبات"
    AccountType.SETTLEMENT -> "تسویه"
    AccountType.CUSTOM -> "سفارشی"
}

fun statusLabel(s: AccountStatus): String = when (s) {
    AccountStatus.ACTIVE -> "فعال"
    AccountStatus.CLOSED -> "بسته"
    AccountStatus.ARCHIVED -> "آرشیو"
}

@Composable
fun AccountEditScreen(nav: NavHostController, id: String) {
    val scope = rememberCoroutineScope()
    val existing = remember(id) { kotlinx.coroutines.runBlocking { id.takeIf { it.isNotBlank() }?.let { ServiceLocator.accounts.byId(it) } } }
    val currencies by ServiceLocator.currencies.all().collectAsState(initial = emptyList())
    val members by ServiceLocator.family.members().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var bank by remember { mutableStateOf(existing?.bankName ?: "") }
    var card4 by remember { mutableStateOf(existing?.cardLast4 ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.CASH) }
    var status by remember { mutableStateOf(existing?.status ?: AccountStatus.ACTIVE) }
    var currency by remember { mutableStateOf(existing?.currencyCode ?: "IRR") }
    var opening by remember { mutableStateOf("") }
    var minBal by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var owner by remember { mutableStateOf(existing?.ownerMemberId) }

    LaunchedEffect(existing) {
        existing?.let {
            val d = ServiceLocator.currencies.byCode(it.currencyCode)?.decimals ?: 0
            if (it.openingBalanceMinor != 0L) opening = Money.format(it.openingBalanceMinor, d, false)
            it.minBalanceMinor?.let { m -> minBal = Money.format(m, d, false) }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState())) {
        CurrencyTopBar(title = if (existing == null) "حساب جدید" else "ویرایش حساب") { nav.popBackStack() }
        Spacer(Modifier.height(10.dp))
        SectionCard {
            com.divan.app.ui.common.Field("نام حساب", name) { name = it }
            Spacer(Modifier.height(6.dp))
            com.divan.app.ui.common.Field("بانک / ارائه‌دهنده (اختیاری)", bank) { bank = it }
            Spacer(Modifier.height(6.dp))
            com.divan.app.ui.common.Field("۴ رقم آخر کارت (برای تشخیص خودکار پیامک)", card4) { card4 = it.take(4) }
            Spacer(Modifier.height(8.dp))
            Text("نوع حساب", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(AccountType.CASH, AccountType.CHECKING, AccountType.SAVINGS, AccountType.CARD, AccountType.DIGITAL_WALLET, AccountType.FOREIGN, AccountType.FAMILY_FUND, AccountType.JOINT).chunked(4).forEach { row ->
                    row.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(typeLabel(t), fontSize = 11.sp) }) }
                }
            }
            Spacer(Modifier.height(8.dp))
            EntityDropdown(
                label = "ارز", items = currencies,
                selectedId = currency, getId = { it.code }, getText = { "${it.nameFa} (${it.code})" },
                onSelect = { currency = it.code },
            )
            Spacer(Modifier.height(6.dp))
            com.divan.app.ui.common.Field("موجودی افتتاحیه", opening) { opening = it }
            Spacer(Modifier.height(6.dp))
            com.divan.app.ui.common.Field("حداقل موجودی دلخواه (هشدار)", minBal) { minBal = it }
            Spacer(Modifier.height(6.dp))
            com.divan.app.ui.common.Field("یادداشت", note) { note = it }
            if (members.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                EntityDropdown(
                    label = "مالک حساب", items = members,
                    selectedId = owner, getId = { it.id }, getText = { it.name },
                    onSelect = { owner = it.id },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = status == AccountStatus.ACTIVE, onClick = { status = AccountStatus.ACTIVE }, label = { Text("فعال") })
                FilterChip(selected = status == AccountStatus.CLOSED, onClick = { status = AccountStatus.CLOSED }, label = { Text("بسته") })
                FilterChip(selected = status == AccountStatus.ARCHIVED, onClick = { status = AccountStatus.ARCHIVED }, label = { Text("آرشیو") })
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = name.isNotBlank(),
            onClick = {
                scope.launch {
                    val dec = ServiceLocator.currencies.byCode(currency)?.decimals ?: 0
                    val openingMinor = Money.parseToMinor(opening, dec) ?: 0L
                    val minMinor = Money.parseToMinor(minBal, dec)
                    val acc = Account(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(), bankName = bank.ifBlank { null },
                        cardLast4 = card4.ifBlank { null }, type = type, status = status,
                        currencyCode = currency,
                        openingBalanceMinor = openingMinor,
                        openingDate = existing?.openingDate ?: System.currentTimeMillis(),
                        minBalanceMinor = minMinor, note = note.ifBlank { null },
                        ownerMemberId = owner,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    )
                    ServiceLocator.accounts.save(acc, isNew = existing == null)
                    nav.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("ذخیره") }
    }
}
