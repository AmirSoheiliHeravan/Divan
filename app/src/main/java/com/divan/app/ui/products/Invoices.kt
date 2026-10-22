package com.divan.app.ui.products

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.divan.app.core.db.entity.Invoice
import com.divan.app.core.db.entity.InvoiceItem
import com.divan.app.core.db.entity.InvoiceKind
import com.divan.app.core.db.entity.InvoicePayMode
import com.divan.app.core.db.entity.InvoiceStatus
import com.divan.app.core.money.Money
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.EmptyView
import com.divan.app.ui.common.EntityDropdown
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.fmtDate
import com.divan.app.ui.common.fmtMoney
import com.divan.app.ui.common.toPersianDigits
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun InvoicesScreen(nav: NavHostController) {
    val invoices by ServiceLocator.products.invoices().collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "فاکتورها") { nav.popBackStack() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = { nav.navigate(Routes.invoiceEdit("PURCHASE")) }) { Text("+ خرید") }
            TextButton(onClick = { nav.navigate(Routes.invoiceEdit("SALE")) }) { Text("+ فروش") }
        }
        if (invoices.isEmpty()) EmptyView("فاکتوری ثبت نشده است")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(invoices, key = { it.id }) { inv ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(
                                if (inv.kind == InvoiceKind.PURCHASE) "🛒 خرید" else "💰 فروش",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text("${fmtDate(inv.date)} · ${inv.personName ?: "—"}", fontSize = 12.sp)
                        }
                        MoneyTextCompat(inv)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoneyTextCompat(inv: Invoice) {
    var s by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(inv.id) { s = fmtMoney(inv.totalMinor, inv.currencyCode) }
    Text(s, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
}

@Composable
fun InvoiceEditScreen(nav: NavHostController, kindParam: String) {
    val scope = rememberCoroutineScope()
    val kind = if (kindParam == "SALE") InvoiceKind.SALE else InvoiceKind.PURCHASE
    val products by ServiceLocator.products.all().collectAsState(initial = emptyList())
    val accounts by ServiceLocator.accounts.allActive().collectAsState(initial = emptyList())
    val cps by ServiceLocator.counterparties.all().collectAsState(initial = emptyList())

    data class RowDraft(var productId: String?, var name: String, var qty: String, var price: String)

    val rows = remember { mutableStateListOf(RowDraft(null, "", "1", "")) }
    var person by remember { mutableStateOf("") }
    var cpId by remember { mutableStateOf<String?>(null) }
    var accountId by remember { mutableStateOf<String?>(null) }
    var discount by remember { mutableStateOf("0") }
    var tax by remember { mutableStateOf("0") }
    var payMode by remember { mutableStateOf(InvoicePayMode.CASH) }
    var paidAmount by remember { mutableStateOf("") }
    var asInventory by remember { mutableStateOf(true) }

    fun subtotal(): Long = rows.sumOf { r ->
        val q = r.qty.toDoubleOrNull() ?: 0.0
        val p = Money.parseToMinor(r.price, 0) ?: 0L
        (q * p).toLong()
    }

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState())) {
        CurrencyTopBar(title = if (kind == InvoiceKind.PURCHASE) "فاکتور خرید" else "فاکتور فروش") { nav.popBackStack() }
        SectionCard(title = "اطلاعات فاکتور") {
            EntityDropdown(
                label = "طرف‌حساب", items = cps,
                selectedId = cpId, getId = { it.id }, getText = { it.name },
                onSelect = { cpId = it.id; person = it.name },
            )
            Spacer(Modifier.height(6.dp))
            com.divan.app.ui.common.Field("نام (در صورت نبود طرف‌حساب)", person) { person = it }
            Spacer(Modifier.height(6.dp))
            EntityDropdown(
                label = "حساب پرداخت/دریافت", items = accounts,
                selectedId = accountId, getId = { it.id }, getText = { it.name },
                onSelect = { accountId = it.id },
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(checked = asInventory, onCheckedChange = { asInventory = it })
                Text(if (kind == InvoiceKind.PURCHASE) "ثبت در موجودی انبار" else "کسر از موجودی انبار", fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionCard(title = "اقلام") {
            rows.forEachIndexed { i, r ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (products.isNotEmpty()) {
                            EntityDropdown(
                                label = "کالا", items = products,
                                selectedId = r.productId, getId = { it.id }, getText = { it.name },
                                onSelect = { sel ->
                                    r.productId = sel.id; r.name = sel.name
                                    if (r.price.isBlank()) r.price = if (kind == InvoiceKind.PURCHASE) sel.buyPriceMinor.toString() else sel.salePriceMinor.toString()
                                },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = r.qty, onValueChange = { r.qty = it },
                            label = { Text("تعداد") }, modifier = Modifier.weight(0.5f), singleLine = true,
                        )
                        OutlinedTextField(
                            value = r.price, onValueChange = { r.price = it },
                            label = { Text("قیمت واحد") }, modifier = Modifier.weight(1f), singleLine = true,
                        )
                        if (rows.size > 1) {
                            TextButton(onClick = { rows.removeAt(i) }) { Text("✕") }
                        }
                    }
                }
            }
            TextButton(onClick = { rows.add(RowDraft(null, "", "1", "")) }) { Text("+ ردیف") }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.divan.app.ui.common.Field("تخفیف", discount, Modifier.weight(1f)) { discount = it }
                com.divan.app.ui.common.Field("مالیات", tax, Modifier.weight(1f)) { tax = it }
            }
            Spacer(Modifier.height(8.dp))
            val sub = subtotal()
            val total = sub - (Money.parseToMinor(discount, 0) ?: 0L) + (Money.parseToMinor(tax, 0) ?: 0L)
            Text("جمع کل: ${toPersianDigits(Money.format(total, 0, false))}", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        SectionCard(title = "پرداخت") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = payMode == InvoicePayMode.CASH, onClick = { payMode = InvoicePayMode.CASH }, label = { Text("نقدی کامل") })
                FilterChip(selected = payMode == InvoicePayMode.PARTIAL, onClick = { payMode = InvoicePayMode.PARTIAL }, label = { Text("پرداخت جزئی") })
                FilterChip(selected = payMode == InvoicePayMode.CREDIT, onClick = { payMode = InvoicePayMode.CREDIT }, label = { Text("اعتباری") })
            }
            Spacer(Modifier.height(6.dp))
            if (payMode != InvoicePayMode.CREDIT) {
                com.divan.app.ui.common.Field("مبلغ پرداخت‌شده", paidAmount) { paidAmount = it }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = subtotal() > 0 && accountId != null,
            onClick = {
                scope.launch {
                    val ts = System.currentTimeMillis()
                    val total = subtotal() - (Money.parseToMinor(discount, 0) ?: 0L) + (Money.parseToMinor(tax, 0) ?: 0L)
                    val paid = when (payMode) {
                        InvoicePayMode.CASH -> total
                        InvoicePayMode.PARTIAL -> Money.parseToMinor(paidAmount, 0) ?: 0L
                        InvoicePayMode.CREDIT -> 0L
                    }
                    val inv = Invoice(
                        id = UUID.randomUUID().toString(),
                        kind = kind, counterpartyId = cpId, personName = person.ifBlank { null },
                        date = ts, discountMinor = Money.parseToMinor(discount, 0) ?: 0L,
                        taxMinor = Money.parseToMinor(tax, 0) ?: 0L, totalMinor = total,
                        accountId = accountId, payMode = payMode, paidMinor = paid,
                        asInventory = asInventory, status = InvoiceStatus.CONFIRMED,
                        createdAt = ts, updatedAt = ts,
                    )
                    val items = rows.mapNotNull { r ->
                        val q = r.qty.toDoubleOrNull() ?: return@mapNotNull null
                        val up = Money.parseToMinor(r.price, 0) ?: return@mapNotNull null
                        InvoiceItem(
                            id = UUID.randomUUID().toString(), invoiceId = inv.id,
                            productId = r.productId, itemName = r.name.ifBlank { "قلم" },
                            qty = q, unit = products.firstOrNull { it.id == r.productId }?.unit ?: "عدد",
                            unitPriceMinor = up, totalMinor = (q * up).toLong(),
                        )
                    }
                    ServiceLocator.createInvoice(inv, items)
                    nav.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("ثبت فاکتور") }
    }
}
