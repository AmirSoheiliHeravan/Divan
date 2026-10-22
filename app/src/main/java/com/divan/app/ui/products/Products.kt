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
import com.divan.app.core.db.entity.Invoice
import com.divan.app.core.db.entity.InvoiceItem
import com.divan.app.core.db.entity.InvoiceKind
import com.divan.app.core.db.entity.InvoicePayMode
import com.divan.app.core.db.entity.MoveKind
import com.divan.app.core.db.entity.Product
import com.divan.app.core.db.entity.ProductStatus
import com.divan.app.core.money.Money
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.EmptyView
import com.divan.app.ui.common.EntityDropdown
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.toPersianDigits
import com.divan.app.ui.nav.Routes
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ProductsScreen(nav: NavHostController) {
    val products by ServiceLocator.products.all().collectAsState(initial = emptyList())
    val low by ServiceLocator.products.lowStock().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var adjustTarget by remember { mutableStateOf<Product?>(null) }
    var adjustQty by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "کالا و انبار") { nav.popBackStack() }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = { nav.navigate(Routes.productEdit()) }) { Text("+ کالای جدید") }
            TextButton(onClick = { nav.navigate(Routes.invoiceEdit("PURCHASE")) }) { Text("🛒 فاکتور خرید") }
            TextButton(onClick = { nav.navigate(Routes.invoiceEdit("SALE")) }) { Text("💰 فاکتور فروش") }
        }
        TextButton(onClick = { nav.navigate(Routes.INVOICES) }) { Text("همه فاکتورها ›") }
        if (low.isNotEmpty()) {
            SectionCard(title = "⚠️ هشدار کم‌موجودی") {
                low.forEach { Text("• ${it.name}: ${toPersianDigits(it.stockQty.toString())}", fontSize = 13.sp) }
            }
        }
        if (products.isEmpty()) EmptyView("کالایی ثبت نشده است")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.id }) { p ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    p.brand,
                                    if (p.stockQty > 0) "موجودی: ${toPersianDigits(p.stockQty.toString())} ${p.unit}" else "بدون موجودی",
                                    "خرید ${toPersianDigits(Money.format(p.buyPriceMinor, 0, false))}",
                                    "فروش ${toPersianDigits(Money.format(p.salePriceMinor, 0, false))}",
                                    p.barcode?.let { "بارکد $it" },
                                ).joinToString(" · "),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { adjustTarget = p }) { Text("موجودی") }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row {
                        TextButton(onClick = { nav.navigate(Routes.productEdit(p.id)) }) { Text("ویرایش") }
                        TextButton(onClick = { scope.launch { ServiceLocator.products.delete(p.id) } }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    adjustTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { adjustTarget = null },
            title = { Text("اصلاح موجودی: ${p.name}") },
            text = {
                Column {
                    Text("موجودی فعلی: ${toPersianDigits(p.stockQty.toString())} ${p.unit}")
                    Spacer(Modifier.height(6.dp))
                    com.divan.app.ui.common.Field("تغییر (مثلاً 5 یا -3)", adjustQty) { adjustQty = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = true, onClick = {}, label = { Text("رسید/برداشت با دلیل") })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val q = adjustQty.toDoubleOrNull() ?: 0.0
                        if (q != 0.0) {
                            ServiceLocator.products.adjustStock(
                                p.id,
                                if (q > 0) MoveKind.RECEIVE else MoveKind.ISSUE,
                                kotlin.math.abs(q),
                                "اصلاح دستی",
                            )
                        }
                        adjustTarget = null; adjustQty = ""
                    }
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { adjustTarget = null }) { Text("انصراف") } },
        )
    }
}

@Composable
fun ProductEditScreen(nav: NavHostController, id: String) {
    val scope = rememberCoroutineScope()
    val existing = remember(id) { kotlinx.coroutines.runBlocking { id.takeIf { it.isNotBlank() }?.let { ServiceLocator.products.byId(it) } } }
    val cats by ServiceLocator.categories.allActive().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var brand by remember { mutableStateOf(existing?.brand ?: "") }
    var barcode by remember { mutableStateOf(existing?.barcode ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "عدد") }
    var buyPrice by remember { mutableStateOf(existing?.buyPriceMinor?.let { Money.format(it, 0, false) } ?: "") }
    var salePrice by remember { mutableStateOf(existing?.salePriceMinor?.let { Money.format(it, 0, false) } ?: "") }
    var minQty by remember { mutableStateOf(existing?.minQty?.toString() ?: "0") }
    var catId by remember { mutableStateOf(existing?.categoryId) }
    var expiry by remember { mutableStateOf(existing?.expiryDate) }
    var showDate by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(existing?.status ?: ProductStatus.ACTIVE) }

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState())) {
        CurrencyTopBar(title = if (existing == null) "کالای جدید" else "ویرایش کالا") { nav.popBackStack() }
        SectionCard {
            com.divan.app.ui.common.Field("نام کالا", name) { name = it }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.divan.app.ui.common.Field("برند", brand, Modifier.weight(1f)) { brand = it }
                com.divan.app.ui.common.Field("بارکد/SKU", barcode, Modifier.weight(1f)) { barcode = it }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.divan.app.ui.common.Field("واحد", unit, Modifier.weight(1f)) { unit = it }
                com.divan.app.ui.common.Field("حداقل موجودی", minQty, Modifier.weight(1f)) { minQty = it }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.divan.app.ui.common.Field("قیمت خرید", buyPrice, Modifier.weight(1f)) { buyPrice = it }
                com.divan.app.ui.common.Field("قیمت فروش", salePrice, Modifier.weight(1f)) { salePrice = it }
            }
            Spacer(Modifier.height(6.dp))
            Text("دسته کالا", style = MaterialTheme.typography.labelMedium)
            com.divan.app.ui.common.FilterChipRow(
                options = cats.map { it.id to it.name }, selected = catId, onSelect = { catId = it },
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.OutlinedButton(onClick = { showDate = true }) {
                Text("تاریخ انقضا: " + (expiry?.let { com.divan.app.ui.common.fmtDate(it) } ?: "ندارد"))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = status == ProductStatus.ACTIVE, onClick = { status = ProductStatus.ACTIVE }, label = { Text("فعال") })
                FilterChip(selected = status == ProductStatus.CONSUMED, onClick = { status = ProductStatus.CONSUMED }, label = { Text("مصرف‌شده") })
                FilterChip(selected = status == ProductStatus.SOLD, onClick = { status = ProductStatus.SOLD }, label = { Text("فروخته‌شده") })
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            enabled = name.isNotBlank(),
            onClick = {
                scope.launch {
                    val ts = System.currentTimeMillis()
                    ServiceLocator.products.save(
                        Product(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(), brand = brand.ifBlank { null },
                            barcode = barcode.ifBlank { null }, categoryId = catId,
                            unit = unit,
                            buyPriceMinor = Money.parseToMinor(buyPrice, 0) ?: 0L,
                            buyPriceDate = if (buyPrice.isNotBlank()) ts else null,
                            salePriceMinor = Money.parseToMinor(salePrice, 0) ?: 0L,
                            salePriceDate = if (salePrice.isNotBlank()) ts else null,
                            minQty = minQty.toDoubleOrNull() ?: 0.0,
                            expiryDate = expiry, status = status,
                            createdAt = existing?.createdAt ?: ts, updatedAt = ts,
                        ), isNew = existing == null,
                    )
                    nav.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("ذخیره") }
    }
    if (showDate) {
        val st = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = expiry ?: System.currentTimeMillis())
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { expiry = it }; showDate = false }) { Text("تأیید") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("انصراف") } },
        ) { androidx.compose.material3.DatePicker(state = st) }
    }
}
