package com.divan.app.ui.transactions

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.navigation.NavHostController
import com.divan.app.core.db.entity.Counterparty
import com.divan.app.core.db.entity.PayMethod
import com.divan.app.core.db.entity.Txn
import com.divan.app.core.db.entity.TxnStatus
import com.divan.app.core.db.entity.TxnType
import com.divan.app.core.money.Money
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CounterpartyPickerDialog
import com.divan.app.ui.common.EntityDropdown
import com.divan.app.ui.common.JalaliDatePickerDialog
import com.divan.app.ui.common.ItemsPickerDialog
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.fmtMoney
import com.divan.app.ui.common.selItemsFromJson
import com.divan.app.ui.nav.Routes
import com.divan.app.ui.util.Jalali
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(nav: NavHostController, typeParam: String) {
    val scope = rememberCoroutineScope()
    val editId = nav.currentBackStackEntry?.arguments?.getString("id")

    var type by remember { mutableStateOf(if (typeParam == "income") TxnType.INCOME else if (typeParam == "transfer") TxnType.TRANSFER else TxnType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var feeText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val accounts by ServiceLocator.accounts.allActive().collectAsState(initial = emptyList())
    val cats by ServiceLocator.categories.allActive().collectAsState(initial = emptyList())
    val counterparties by ServiceLocator.counterparties.all().collectAsState(initial = emptyList())
    val base by ServiceLocator.currencies.base.collectAsState(initial = null)
    var accountId by remember { mutableStateOf<String?>(null) }
    var toAccountId by remember { mutableStateOf<String?>(null) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var counterpartyId by remember { mutableStateOf<String?>(null) }
    var showCpPicker by remember { mutableStateOf(false) }
    var showItemsPicker by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(PayMethod.CASH) }
    var itemsJson by remember { mutableStateOf<String?>(null) }

    val cal = Calendar.getInstance()
    var date by remember { mutableStateOf(cal.timeInMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    val txnFlow = remember(editId) { editId?.takeIf { it.isNotBlank() }?.let { ServiceLocator.txns.byIdFlow(it) } }
    val editing = txnFlow?.collectAsState(initial = null)?.value
    androidx.compose.runtime.LaunchedEffect(editing) {
        editing?.let { t ->
            type = t.type; amountText = fmtMoney(t.amountMinor, t.currencyCode).split(" ")[0]
            title = t.title; note = t.note ?: ""
            accountId = t.accountId; toAccountId = t.toAccountId; categoryId = t.categoryId
            counterpartyId = t.counterpartyId; method = t.method; date = t.date
            itemsJson = t.itemsJson
        }
    }
    androidx.compose.runtime.LaunchedEffect(accounts) {
        if (accountId == null && accounts.isNotEmpty()) accountId = accounts.first().id
    }

    val currency = accounts.firstOrNull { it.id == accountId }?.currencyCode ?: base?.code ?: "IRR"
    var decimals by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(currency) {
        decimals = ServiceLocator.currencies.byCode(currency)?.decimals ?: 0
    }

    if (showDatePicker) {
        // تقویم شمسی لمس‌کردنی (به‌جای دیالوگ گرگوری)
        JalaliDatePickerDialog(initial = date, onPick = { date = it }, onDismiss = { showDatePicker = false })
    }
    if (showItemsPicker) {
        // پنجره ویزاردی واحد — دسته و اقلام با هم (بدون تکرار)
        ItemsPickerDialog(initialJson = itemsJson, onConfirm = { json, gname ->
            itemsJson = json.takeIf { selItemsFromJson(it).isNotEmpty() }
            // نام مجموعه به‌عنوان دسته تراکنش ثبت می‌شود (تطبیق با دسته‌های اصلی)
            if (gname != null) {
                categoryId = cats.firstOrNull { it.parentId == null && it.name == gname }?.id
                    ?: cats.firstOrNull { it.name == gname }?.id
                    ?: categoryId
            }
        }, onDismiss = { showItemsPicker = false })
    }

    if (showCpPicker) {
        CounterpartyPickerDialog(onSelect = { counterpartyId = it; showCpPicker = false }, onDismiss = { showCpPicker = false })
    }

    val amountMinor = remember(amountText, decimals) { Money.parseToMinor(amountText, decimals) ?: 0L }
    val feeMinor = remember(feeText, decimals) { Money.parseToMinor(feeText, decimals) ?: 0L }

    // اخیراً استفاده‌شده — الهام از قیاس: ثبت سریع با انتخاب‌های اخیر
    var recentTxns by remember { mutableStateOf<List<Txn>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        recentTxns = runCatching { ServiceLocator.txns.recentWithCategory(8) }.getOrDefault(emptyList())
    }
    val recentCats = remember(recentTxns) {
        val seen = mutableSetOf<String?>()
        recentTxns.filter { it.categoryId != null && seen.add(it.categoryId) }.take(6)
    }

    // resolved names for selected category (root › sub)
    val selCat = cats.firstOrNull { it.id == categoryId }
    val catLabel = selCat?.let { c ->
        val parent = c.parentId?.let { pid -> cats.firstOrNull { it.id == pid } }
        if (parent != null) "${parent.name} › ${c.name}" else c.name
    }

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = type == TxnType.EXPENSE, onClick = { type = TxnType.EXPENSE }, label = { Text("هزینه") })
            FilterChip(selected = type == TxnType.INCOME, onClick = { type = TxnType.INCOME }, label = { Text("درآمد") })
            FilterChip(selected = type == TxnType.TRANSFER, onClick = { type = TxnType.TRANSFER }, label = { Text("انتقال") })
        }
        Spacer(Modifier.height(10.dp))

        if (recentCats.isNotEmpty()) {
            Text("اخیراً استفاده‌شده (ثبت سریع):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recentCats.forEach { t ->
                    val cat = cats.firstOrNull { it.id == t.categoryId }
                    FilterChip(
                        selected = categoryId == t.categoryId,
                        onClick = {
                            categoryId = t.categoryId
                            if (title.isBlank()) title = t.title
                        },
                        label = { Text(cat?.name ?: t.title.ifBlank { "اخیر" }, fontSize = 10.sp, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        SectionCard {
            OutlinedTextField(
                value = amountText, onValueChange = { s ->
                    amountText = s.filter { it.isDigit() || it == '.' || it in "۰۱۲۳۴۵۶۷۸۹" }
                },
                label = { Text("مبلغ ($currency)") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("شرح (مثلاً خرید نان)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }

        Spacer(Modifier.height(10.dp))
        SectionCard(title = "حساب") {
            EntityDropdown(
                label = if (type == TxnType.TRANSFER) "از حساب" else "حساب",
                items = accounts, selectedId = accountId,
                getId = { it.id }, getText = { it.name }, onSelect = { accountId = it.id },
            )
            if (type == TxnType.TRANSFER) {
                Spacer(Modifier.height(6.dp))
                EntityDropdown(
                    label = "به حساب", items = accounts, selectedId = toAccountId,
                    getId = { it.id }, getText = { it.name }, onSelect = { toAccountId = it.id },
                )
            }
        }

        if (type != TxnType.INCOME) {
            Spacer(Modifier.height(10.dp))
            SectionCard(title = "کارمزد") {
                OutlinedTextField(
                    value = feeText, onValueChange = { s -> feeText = s.filter { it.isDigit() || it == '.' || it in "۰۱۲۳۴۵۶۷۸۹" } },
                    label = { Text("کارمزد (اختیاری) – $currency") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
            }
        }

        if (type != TxnType.TRANSFER) {
            Spacer(Modifier.height(10.dp))
            // دسته و اقلام — یک پنجره واحد ویزاردی (گرید/دکمه تکراری دسته حذف شد)
            SectionCard(title = "دسته و اقلام (مجموعه ← قلم)") {
                val selItems = remember(itemsJson) { selItemsFromJson(itemsJson) }
                val itemsTotal = selItems.sumOf { it.priceMinor * it.qty }
                OutlinedButton(onClick = { showItemsPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            selItems.isNotEmpty() ->
                                "${selItems.first().groupName} · ${Jalali.faNum(selItems.size)} قلم · ${fmtMoney(itemsTotal, currency)}"
                            catLabel != null -> catLabel + " (تغییر…)"
                            else -> "انتخاب دسته و اقلام + افزودن/حذف آیتم…"
                        },
                        maxLines = 1,
                    )
                }
            }
        }

        // شخص مرتبط — در همه انواع تراکنش
        Spacer(Modifier.height(10.dp))
        SectionCard(title = "شخص مرتبط") {
            OutlinedButton(onClick = { showCpPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    counterpartyId?.let { id -> counterparties.firstOrNull { it.id == id }?.name ?: "شخص انتخاب شد ✔" }
                        ?: "انتخاب شخص (عضو خانواده / مخاطبین گوشی)…",
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionCard(title = "جزئیات") {
            Text("روش پرداخت", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(PayMethod.CASH to "نقد", PayMethod.CARD to "کارت", PayMethod.TRANSFER to "انتقالی", PayMethod.CREDIT to "اعتباری", PayMethod.CHEQUE to "چک").forEach { (m, lbl) ->
                    FilterChip(selected = method == m, onClick = { method = m }, label = { Text(lbl) })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showDatePicker = true }) { Text("تاریخ (تقویم شمسی): " + Jalali.fmt(date)) }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("یادداشت") }, modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(14.dp))
        Button(
            enabled = amountMinor > 0 && (type == TxnType.TRANSFER || accountId != null) && (type != TxnType.TRANSFER || (accountId != null && toAccountId != null)),
            onClick = {
                scope.launch {
                    val ts = System.currentTimeMillis()
                    val base0 = ServiceLocator.txns.baseCode()
                    val rate = ServiceLocator.txns.converter.rateAt(currency, base0, date)
                    val baseCur = ServiceLocator.currencies.byCode(base0)
                    val fromCur = ServiceLocator.currencies.byCode(currency)
                    val baseMinor = Money.convert(amountMinor, fromCur?.decimals ?: 0, baseCur?.decimals ?: 0, rate)
                    val t = Txn(
                        id = editing?.id ?: "",
                        type = type, amountMinor = amountMinor, feeMinor = feeMinor,
                        currencyCode = currency,
                        rateUsed = rate, baseAmountMinor = baseMinor,
                        accountId = accountId, toAccountId = toAccountId,
                        categoryId = categoryId, counterpartyId = counterpartyId,
                        method = method, date = date, title = title.trim(),
                        note = note.ifBlank { null },
                        itemsJson = itemsJson?.takeIf { selItemsFromJson(it).isNotEmpty() },
                        status = TxnStatus.CONFIRMED,
                        createdAt = editing?.createdAt ?: ts, updatedAt = ts,
                    )
                    if (editing != null) ServiceLocator.txns.update(t) else ServiceLocator.txns.create(t)
                    nav.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (editing != null) "ذخیره تغییرات" else "ثبت تراکنش") }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("انصراف") }
    }
}
