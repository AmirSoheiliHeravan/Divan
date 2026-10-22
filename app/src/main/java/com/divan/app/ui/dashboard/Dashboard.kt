package com.divan.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import com.divan.app.core.db.entity.TxnType
import com.divan.app.core.sync.WorkScheduler
import com.divan.app.data.LiveRate
import com.divan.app.data.TxnRepo
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.MoneyText
import com.divan.app.ui.common.PieChart
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.expenseColor
import com.divan.app.ui.common.fmtDate
import com.divan.app.ui.common.fmtMoney
import com.divan.app.ui.common.incomeColor
import com.divan.app.ui.common.toPersianDigits
import com.divan.app.ui.common.parseColorSafe
import com.divan.app.ui.nav.Routes
import java.util.Calendar

@Composable
fun DashboardScreen(nav: NavHostController) {
    val accounts by ServiceLocator.accounts.allActive().collectAsState(initial = emptyList())
    val recent by ServiceLocator.txns.recent(8).collectAsState(initial = emptyList())
    val base by ServiceLocator.currencies.base.collectAsState(initial = null)
    val upcoming by ServiceLocator.loans.upcoming(5).collectAsState(initial = emptyList())
    val familyName by ServiceLocator.settings.familyName.collectAsState(initial = "")

    var totals by remember { mutableStateOf(TxnRepo.Totals(0, 0, 0, 0)) }
    var balances by remember { mutableStateOf(mapOf<String, Long>()) }
    var catSums by remember { mutableStateOf(mapOf<String, Long>()) }
    var categoriesById by remember { mutableStateOf(mapOf<String, com.divan.app.core.db.entity.Category>()) }
    var syncPending by remember { mutableStateOf(0) }
    // نموداری به سبک پولکی: هزینه ۷ روز اخیر + نرخ‌های زنده
    var weekExpense by remember { mutableStateOf(listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L)) }
    var liveRates by remember { mutableStateOf(listOf<LiveRate>()) }

    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
    val monthStart = cal.timeInMillis
    val monthEnd = monthStart + 32L * 86400000

    LaunchedEffect(accounts) {
        com.divan.app.ui.common.UiCtx.refresh()
        totals = ServiceLocator.txns.totalsBetween(monthStart, System.currentTimeMillis())
        balances = accounts.associate { it.id to ServiceLocator.accounts.balanceOf(it.id) }
        catSums = ServiceLocator.txns.categorySums(monthStart, System.currentTimeMillis())
        categoriesById = ServiceLocator.categories.allActive().first().associateBy { it.id }
    }
    LaunchedEffect(Unit) {
        ServiceLocator.syncQueue.pendingCountFlow().collect { syncPending = it }
    }
    // هزینه هر روز از ۷ روز اخیر (برای نمودار میله‌ای)
    LaunchedEffect(Unit) {
        val out = mutableListOf<Long>()
        for (i in 6 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_MONTH, -i)
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            val start = c.timeInMillis
            val end = start + 86400000L
            out += runCatching { ServiceLocator.txns.totalsBetween(start, end).expense }.getOrDefault(0L)
        }
        weekExpense = out
    }
    // نرخ‌های زنده — tgju.org (ارز/سکه) + estjt.ir (طلا): ابتدا کش، بعد واکشی لحظه‌ای
    LaunchedEffect(Unit) {
        liveRates = ServiceLocator.rates.cached()
        if (liveRates.isEmpty()) {
            val r = runCatching { ServiceLocator.rates.refresh() }.getOrDefault(emptyList())
            if (r.isNotEmpty()) liveRates = r
        } else {
            val r = runCatching { ServiceLocator.rates.refresh() }.getOrDefault(emptyList())
            if (r.isNotEmpty()) liveRates = r
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(if (familyName.isBlank()) "دیوان" else familyName, style = MaterialTheme.typography.headlineMedium)
                Text(fmtDate(System.currentTimeMillis()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard {
                Text("موجودی کل حساب‌ها", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                val totalBase = balances.values.sum()
                MoneyText(totalBase, base?.code ?: "IRR", bold = true, fontSize = 28)
                Spacer(Modifier.height(8.dp))
                accounts.forEach { a ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.divan.app.ui.common.ColorDot(a.colorHex)
                            Spacer(Modifier.padding(3.dp))
                            Text(a.name, fontSize = 14.sp)
                        }
                        MoneyText(balances[a.id] ?: 0, a.currencyCode, fontSize = 14)
                    }
                }
                if (accounts.isEmpty()) Text("حسابی اضافه نشده – از تنظیمات → حساب‌ها", fontSize = 13.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionCard(Modifier.weight(1f)) {
                    Text("درآمد ماه", style = MaterialTheme.typography.labelMedium, color = incomeColor)
                    MoneyText(totals.income, base?.code ?: "IRR", bold = true)
                    Spacer(Modifier.height(4.dp))
                    val maxV = maxOf(totals.income, totals.expense, 1L)
                    LinearProgressIndicator(
                        progress = { (totals.income.toFloat() / maxV).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = incomeColor, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                SectionCard(Modifier.weight(1f)) {
                    Text("هزینه ماه", style = MaterialTheme.typography.labelMedium, color = expenseColor)
                    MoneyText(totals.expense, base?.code ?: "IRR", bold = true)
                    Spacer(Modifier.height(4.dp))
                    val maxV = maxOf(totals.income, totals.expense, 1L)
                    LinearProgressIndicator(
                        progress = { (totals.expense.toFloat() / maxV).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = expenseColor, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
        if (liveRates.isNotEmpty()) {
            item {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("نرخ لحظه‌ای", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "tgju.org · estjt.ir",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // شخصی‌سازی نرخ‌ها: فقط نرخ‌های لازمِ کاربر با ترتیب دلخواه (تنظیم از صفحه ارزها)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ServiceLocator.rates.personalize(liveRates), key = { it.id }) { r ->
                            SectionCard(Modifier.width(132.dp)) {
                                Text(r.name, fontSize = 11.sp, maxLines = 1)
                                Text(
                                    toPersianDigits(com.divan.app.core.money.Money.format(r.valueRial, base?.decimals ?: 0, com.divan.app.ui.common.UiCtx.persianDigits)),
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    (if (r.changePercent >= 0) "▲ " else "▼ ") + com.divan.app.ui.common.toPersianDigits(String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(r.changePercent))) + "٪",
                                    fontSize = 10.sp,
                                    color = if (r.changePercent >= 0) incomeColor else expenseColor,
                                )
                                Text(if (r.source == "tgju") "tgju.org" else "estjt.ir", fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "به‌روزرسانی خودکار روزانه + لحظه‌ای · کاربر می‌تواند نماد دلخواه اضافه کند",
                        fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            // هزینه ۷ روز اخیر — نمودار میله‌ای (پولکی‌سبک)
            SectionCard(title = "هزینه ۷ روز اخیر") {
                val maxV = (weekExpense.maxOrNull() ?: 0L).coerceAtLeast(1L)
                Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                    val bw = size.width / 7f
                    weekExpense.forEachIndexed { i, v ->
                        val h = (v.toFloat() / maxV) * (size.height * 0.86f)
                        drawRoundRect(
                            color = if (i == 6) com.divan.app.ui.theme.Gold else com.divan.app.ui.theme.Green700,
                            topLeft = Offset(i * bw + bw * 0.22f, size.height - h.coerceAtLeast(4f)),
                            size = Size(bw * 0.56f, h.coerceAtLeast(4f)),
                            cornerRadius = CornerRadius(10f, 10f),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { d ->
                        Text(d, fontSize = 9.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "جمع هفته: ${toPersianDigits(com.divan.app.core.money.Money.format(weekExpense.sum(), base?.decimals ?: 0, com.divan.app.ui.common.UiCtx.persianDigits))}",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (upcoming.isNotEmpty()) {
            item {
                SectionCard(title = "اقساط نزدیک", trailing = {
                    androidx.compose.material3.TextButton(onClick = { nav.navigate(Routes.LOANS) }) { Text("همه") }
                }) {
                    upcoming.forEach { inst ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("قسط ${inst.seq}", fontSize = 14.sp)
                            Text(
                                fmtDate(inst.dueDate),
                                fontSize = 13.sp,
                                color = if (inst.dueDate < System.currentTimeMillis()) expenseColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (catSums.isNotEmpty()) {
            item {
                SectionCard(title = "دسته‌بندی هزینه‌های ماه") {
                    val items = catSums.entries.sortedByDescending { it.value }.take(6)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PieChart(
                            items.mapIndexed { i, e ->
                                parseColorSafe(categoriesById[e.key]?.colorHex ?: paletteFor(i)) to e.value.toDouble()
                            }
                        )
                        Spacer(Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) {
                            items.forEachIndexed { i, e ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(
                                        Modifier.height(10.dp).width(10.dp)
                                            .background(parseColorSafe(categoriesById[e.key]?.colorHex ?: paletteFor(i)),
                                                androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                                    )
                                    Spacer(Modifier.padding(3.dp))
                                    Text(categoriesById[e.key]?.name ?: "—", fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                    MoneyText(e.value, base?.code ?: "IRR", fontSize = 12)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard(title = "آخرین تراکنش‌ها", trailing = {
                androidx.compose.material3.TextButton(onClick = { nav.navigate(Routes.TXNS) }) { Text("همه") }
            }) {
                if (recent.isEmpty()) Text("هنوز تراکنشی ثبت نشده است", fontSize = 13.sp)
                recent.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title.ifBlank { typeLabel(t.type) }, fontSize = 14.sp, maxLines = 1)
                            Text(fmtDate(t.date), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MoneyText(
                            t.amountMinor, t.currencyCode,
                            color = if (t.type in moneyIn) incomeColor else expenseColor,
                            bold = true, fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (syncPending > 0) "صف همگام‌سازی: $syncPending عملیات در انتظار سرور" else "آفلاین کامل – همه داده‌ها روی گوشی",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.TextButton(onClick = {
                    WorkScheduler.triggerSyncNow(ServiceLocator.context())
                }) { Text("تلاش برای همگام‌سازی") }
            }
        }
    }
}

private val moneyIn = setOf(
    TxnType.INCOME, TxnType.LOAN_DISBURSE, TxnType.DEBT_COLLECT_IN, TxnType.ASSET_SELL, TxnType.SALE, TxnType.SAVINGS_OUT, TxnType.INVEST_OUT,
)

fun typeLabel(t: TxnType): String = when (t) {
    TxnType.INCOME -> "درآمد"
    TxnType.EXPENSE -> "هزینه"
    TxnType.TRANSFER -> "انتقال"
    TxnType.LOAN_DISBURSE -> "دریافت وام"
    TxnType.LOAN_REPAY -> "بازپرداخت وام"
    TxnType.DEBT_COLLECT_IN -> "دریافت طلب"
    TxnType.DEBT_REPAY_OUT -> "پرداخت بدهی"
    TxnType.SAVINGS_IN -> "پس‌انداز"
    TxnType.SAVINGS_OUT -> "برداشت پس‌انداز"
    TxnType.INVEST_IN -> "سرمایه‌گذاری"
    TxnType.INVEST_OUT -> "برداشت سرمایه"
    TxnType.ASSET_BUY -> "خرید دارایی"
    TxnType.ASSET_SELL -> "فروش دارایی"
    TxnType.PURCHASE -> "خرید"
    TxnType.SALE -> "فروش"
    TxnType.BALANCE_ADJUST -> "اصلاح موجودی"
    TxnType.FEE -> "کارمزد"
}

private fun paletteFor(i: Int): String = listOf(
    "#2E7D5B", "#D9B25F", "#4A7FD4", "#E2574C", "#8E6BC1", "#3AAFA9", "#E88D3C",
)[i % 7]
