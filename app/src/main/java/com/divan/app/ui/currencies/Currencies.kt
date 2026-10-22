package com.divan.app.ui.currencies

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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.divan.app.core.money.Money
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.UiCtx
import com.divan.app.ui.common.expenseColor
import com.divan.app.ui.common.fmtDateTime
import com.divan.app.ui.common.incomeColor
import com.divan.app.ui.common.toPersianDigits
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CurrenciesScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val currencies by ServiceLocator.currencies.all().collectAsState(initial = emptyList())
    val rates by ServiceLocator.currencies.rates().collectAsState(initial = emptyList())
    val base by ServiceLocator.currencies.base.collectAsState(initial = null)

    var code by remember { mutableStateOf("") }
    var nameFa by remember { mutableStateOf("") }
    var decimals by remember { mutableStateOf("0") }
    var rateCode by remember { mutableStateOf("USD") }
    var rateValue by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "ارزها و نرخ تبدیل") { nav.popBackStack() }
        Text(
            "ارز پایه: ${base?.nameFa ?: "—"} — نرخ زنده از tgju.org و estjt.ir + نرخ دستی؛ نرخ تاریخی هر تراکنش ثابت می‌ماند.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "live") { LiveRatesCard() }
            items(currencies, key = { it.code }) { c ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${c.nameFa} (${c.code}) ${c.symbol}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (c.isBase) "ارز پایه" else "اعشار: ${c.decimals}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!c.isBase) {
                            Column {
                                TextButton(onClick = { scope.launch { ServiceLocator.currencies.setBase(c.code) } }) {
                                    Text("پایه کن")
                                }
                            }
                        }
                    }
                    if (!c.isBase) {
                        Spacer(Modifier.height(4.dp))
                        val latest = rates.filter { it.currencyCode == c.code }.maxByOrNull { it.date }
                        Text(
                            "آخرین نرخ: " + (latest?.let { Money.format((it.rateToBase * 1000000).toLong(), 6, false) + " (تاریخ " + com.divan.app.ui.common.fmtDate(it.date) + ")" } ?: "ثبت نشده"),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        SectionCard(title = "ثبت نرخ تبدیل") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = rateCode, onValueChange = { rateCode = it.uppercase() },
                    label = { Text("کد ارز") }, modifier = Modifier.weight(1f), singleLine = true,
                )
                OutlinedTextField(
                    value = rateValue, onValueChange = { rateValue = it },
                    label = { Text("نرخ به پایه") }, modifier = Modifier.weight(1f), singleLine = true,
                )
            }
            Spacer(Modifier.height(6.dp))
            Button(
                enabled = rateValue.toDoubleOrNull() != null && rateCode.isNotBlank(),
                onClick = {
                    scope.launch {
                        ServiceLocator.currencies.addRate(
                            rateCode.trim(), rateValue.toDouble(), System.currentTimeMillis()
                        )
                        rateValue = ""
                    }
                },
            ) { Text("ثبت نرخ") }
        }
        SectionCard(title = "افزودن ارز سفارشی") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = code, onValueChange = { code = it.uppercase() },
                    label = { Text("کد") }, modifier = Modifier.weight(1f), singleLine = true,
                )
                OutlinedTextField(
                    value = nameFa, onValueChange = { nameFa = it },
                    label = { Text("نام") }, modifier = Modifier.weight(1f), singleLine = true,
                )
                OutlinedTextField(
                    value = decimals, onValueChange = { decimals = it.take(1) },
                    label = { Text("اعشار") }, modifier = Modifier.weight(0.6f), singleLine = true,
                )
            }
            Spacer(Modifier.height(6.dp))
            Button(
                enabled = code.length in 2..6 && nameFa.isNotBlank(),
                onClick = {
                    scope.launch {
                        ServiceLocator.currencies.save(
                            com.divan.app.core.db.entity.CurrencyUnit(
                                code = code.trim(), name = nameFa.trim(),
                                nameFa = nameFa.trim(), symbol = nameFa.trim(),
                                decimals = decimals.toIntOrNull() ?: 0, isCustom = true,
                            ), isNew = true,
                        )
                        code = ""; nameFa = ""; decimals = "0"
                    }
                },
            ) { Text("افزودن ارز") }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { nav.popBackStack() }) { Text("بازگشت") }
    }
}

/** نگاشت نماد زنده به کد ارز برنامه (برای دکمه همگام‌سازی) */
private fun syncCode(id: String): String? = when (id) {
    "usd" -> "USD"; "eur" -> "EUR"; "aed" -> "AED"; "try" -> "TRY"; "gold18" -> "XAU"
    else -> null
}

/** نرخ‌های زنده — tgju.org (ارز و سکه) + estjt.ir (طلا)؛ کش آفلاین + به‌روزرسانی لحظه‌ای
 * کاشی‌ها با «شخصی‌سازی» مدیریت می‌شوند: نمایش/پنهان + ترتیب */
@Composable
private fun LiveRatesCard() {
    val scope = rememberCoroutineScope()
    var rates by remember { mutableStateOf(ServiceLocator.rates.cached()) }
    var refreshing by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showPrefs by remember { mutableStateOf(false) }
    var prefsVer by remember { mutableStateOf(0) }
    var lastAt by remember { mutableStateOf(ServiceLocator.rates.lastUpdate()) }

    fun refresh() {
        refreshing = true
        scope.launch {
            rates = runCatching { ServiceLocator.rates.refresh() }.getOrDefault(rates)
            lastAt = ServiceLocator.rates.lastUpdate()
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { if (rates.isEmpty()) refresh() }

    val shownRates = remember(rates, prefsVer) { ServiceLocator.rates.personalize(rates) }
    val hiddenCount = ServiceLocator.rates.hiddenIds().size

    SectionCard(title = "نرخ‌های زنده — tgju.org (ارز و سکه) · estjt.ir (طلا)") {
        Text(
            if (lastAt > 0) "آخرین به‌روزرسانی: ${fmtDateTime(lastAt)} — خودکار روزانه + لحظه‌ای"
            else "با لمس «به‌روزرسانی» نرخ‌ها از مراجع گرفته می‌شود",
            fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hiddenCount > 0) {
            Text(
                "نمایش ${toPersianDigits(shownRates.size.toString())} از ${toPersianDigits(rates.size.toString())} نماد (شخصی‌سازی‌شده)",
                fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { refresh() }, enabled = !refreshing) {
                Text(if (refreshing) "به‌روزرسانی…" else "به‌روزرسانی لحظه‌ای")
            }
            TextButton(onClick = { showPrefs = true }) { Text("شخصی‌سازی") }
            TextButton(onClick = { showAdd = true }) { Text("+ افزودن نماد") }
        }
        shownRates.forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (r.source == "tgju") "tgju.org" else "estjt.ir", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(toPersianDigits(Money.format(r.valueRial, 0, UiCtx.persianDigits)), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        (if (r.changePercent >= 0) "▲ " else "▼ ") + toPersianDigits(String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(r.changePercent))) + "٪",
                        fontSize = 10.sp,
                        color = if (r.changePercent >= 0) incomeColor else expenseColor,
                    )
                }
                syncCode(r.id)?.let { code ->
                    TextButton(onClick = {
                        scope.launch { ServiceLocator.currencies.addRate(code, r.valueRial / 1_000_000.0, System.currentTimeMillis()) }
                    }) { Text("همگام $code") }
                }
            }
        }
    }
    if (showAdd) AddSymbolDialog(onDismiss = { showAdd = false })
    if (showPrefs) MarketPrefsDialog(onDismiss = { showPrefs = false; prefsVer++ })
}

/** شخصی‌سازی نرخ‌ها — کدام نرخ‌ها لازم‌اند (تیک) + ترتیب (فلش بالا/پایین)؛ روی داشبورد هم اعمال می‌شود */
@Composable
private fun MarketPrefsDialog(onDismiss: () -> Unit) {
    var order by remember { mutableStateOf(ServiceLocator.rates.orderedSymbols()) }
    var hidden by remember { mutableStateOf(ServiceLocator.rates.hiddenIds()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("شخصی‌سازی نرخ‌ها", fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "نمادهای تیک‌خورده نمایش داده می‌شوند؛ ترتیب با فلش‌ها تنظیم می‌شود — روی کاشی‌های داشبورد هم اعمال می‌شود.",
                    fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    items(order, key = { it.id }) { s ->
                        val idx = order.indexOfFirst { it.id == s.id }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = s.id !in hidden,
                                onCheckedChange = { on -> hidden = if (on) hidden - s.id else hidden + s.id },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    textDecoration = if (s.id in hidden) TextDecoration.LineThrough else null,
                                )
                                Text(
                                    if (s.source == "tgju") "tgju.org" else "estjt.ir",
                                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(enabled = idx > 0, onClick = {
                                val l = order.toMutableList()
                                val tmp = l[idx]; l[idx] = l[idx - 1]; l[idx - 1] = tmp
                                order = l
                            }) { Text("▲", fontSize = 11.sp) }
                            TextButton(enabled = idx < order.lastIndex, onClick = {
                                val l = order.toMutableList()
                                val tmp = l[idx]; l[idx] = l[idx + 1]; l[idx + 1] = tmp
                                order = l
                            }) { Text("▼", fontSize = 11.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ServiceLocator.rates.setOrder(order.map { it.id })
                ServiceLocator.rates.setHidden(hidden)
                onDismiss()
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** افزودن نماد دلخواه — هر نماد از tgju.org یا estjt.ir با کلید مرجع */
@Composable
private fun AddSymbolDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("tgju") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن نماد نرخ", fontSize = 16.sp) },
        text = {
            Column {
                com.divan.app.ui.common.Field("نام نماد (مثلاً درهم امارات)", name) { name = it }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("کلید مرجع (tgju: price_aed_rl · estjt: عنوان در صفحه)", key) { key = it }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = source == "tgju", onClick = { source = "tgju" }, label = { Text("tgju.org") })
                    FilterChip(selected = source == "estjt", onClick = { source = "estjt" }, label = { Text("estjt.ir") })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && key.isNotBlank(), onClick = {
                val id = name.trim().lowercase().replace(" ", "-")
                ServiceLocator.rates.addSymbol(id, name.trim(), source, key.trim())
                onDismiss()
            }) { Text("افزودن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
