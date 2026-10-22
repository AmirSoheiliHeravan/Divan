package com.divan.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.divan.app.core.db.entity.TxnType
import com.divan.app.core.sync.WorkScheduler
import com.divan.app.data.TxnRepo
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.MiniBarChart
import com.divan.app.ui.common.MoneyText
import com.divan.app.ui.common.PieChart
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.expenseColor
import com.divan.app.ui.common.fmtMoney
import com.divan.app.ui.common.incomeColor
import com.divan.app.ui.common.parseColorSafe
import kotlinx.coroutines.flow.first
import java.util.Calendar

private enum class Period(val label: String, val months: Int) {
    THIS_MONTH("این ماه", 1), LAST_3("۳ ماه", 3), LAST_6("۶ ماه", 6), YEAR("امسال", 12), FISCAL("سال مالی", 12)
}

@Composable
fun ReportsScreen(nav: NavHostController) {
    var period by remember { mutableStateOf(Period.THIS_MONTH) }
    val base by ServiceLocator.currencies.base.collectAsState(initial = null)
    val members by ServiceLocator.family.members().collectAsState(initial = emptyList())
    var memberFilter by remember { mutableStateOf<String?>(null) }
    val fiscalStart by ServiceLocator.settings.fiscalStartMonth.collectAsState(initial = 1)
    val fiscalMonthNames = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

    var totals by remember { mutableStateOf(TxnRepo.Totals(0, 0, 0, 0)) }
    var catSums by remember { mutableStateOf(mapOf<String, Long>()) }
    var catNames by remember { mutableStateOf(mapOf<String, String>()) }
    var monthly by remember { mutableStateOf(listOf<Pair<String, Double>>()) }
    var memberPerf by remember { mutableStateOf(listOf<Triple<String, Int, Long>>()) }
    var trial by remember { mutableStateOf(listOf<Triple<String, Long, Long>>()) }

    LaunchedEffect(period, memberFilter) {
        com.divan.app.ui.common.UiCtx.refresh()
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MONTH, -(period.months - 1))
        // سال مالی (اصول حسابداری): از ماه شروع تعیین‌شده در تنظیمات تا امروز
        val from = if (period == Period.FISCAL) {
            val startM = ServiceLocator.settings.fiscalStartMonthNow()
            val now = Calendar.getInstance()
            val fy = if ((now.get(Calendar.MONTH) + 1) < startM) now.get(Calendar.YEAR) - 1 else now.get(Calendar.YEAR)
            cal.set(Calendar.YEAR, fy); cal.set(Calendar.MONTH, startM - 1)
            cal.timeInMillis
        } else cal.timeInMillis
        totals = ServiceLocator.txns.totalsBetween(from, System.currentTimeMillis(), memberFilter)
        catSums = ServiceLocator.txns.categorySums(from, System.currentTimeMillis())
        val all = ServiceLocator.categories.allActive().first()
        catNames = all.associate { it.id to it.name }

        // month-by-month expense comparison
        val list = mutableListOf<Pair<String, Double>>()
        for (i in (period.months - 1) downTo 0) {
            val c = Calendar.getInstance()
            c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            c.add(Calendar.MONTH, -i)
            val s = c.timeInMillis
            c.add(Calendar.MONTH, 1)
            val t = ServiceLocator.txns.totalsBetween(s, c.timeInMillis, memberFilter)
            list += "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.YEAR) % 100}" to t.expense.toDouble()
        }
        monthly = list

        // عملکرد اشخاص + ترازنامه آزمایشی — الهام از پارمیس
        val now = System.currentTimeMillis()
        val cps = ServiceLocator.counterparties.all().first()
        val accs = ServiceLocator.accounts.all().first()
        val inRange = runCatching { ServiceLocator.txns.allOnce() }.getOrDefault(emptyList())
            .filter { it.date >= from && it.date <= now }
        val mPerf = linkedMapOf<String, Triple<Int, Long, Long>>()
        val tr = linkedMapOf<String, Pair<Long, Long>>()
        inRange.forEach { t ->
            t.counterpartyId?.let { cid ->
                val name = cps.firstOrNull { it.id == cid }?.name
                if (name != null) {
                    val prev = mPerf[name] ?: Triple(0, 0L, 0L)
                    mPerf[name] =
                        if (t.type == TxnType.INCOME) Triple(prev.first + 1, prev.second, prev.third + t.baseAmountMinor)
                        else Triple(prev.first + 1, prev.second + t.baseAmountMinor, prev.third)
                }
            }
            val accName: (String?) -> String? = { id -> accs.firstOrNull { it.id == id }?.name }
            when {
                t.type == TxnType.TRANSFER -> {
                    accName(t.accountId)?.let { n -> val p = tr[n] ?: (0L to 0L); tr[n] = p.first to (p.second + t.baseAmountMinor) }
                    accName(t.toAccountId)?.let { n -> val p = tr[n] ?: (0L to 0L); tr[n] = (p.first + t.baseAmountMinor) to p.second }
                }
                t.type == TxnType.INCOME ->
                    accName(t.accountId)?.let { n -> val p = tr[n] ?: (0L to 0L); tr[n] = (p.first + t.baseAmountMinor) to p.second }
                else ->
                    accName(t.accountId)?.let { n -> val p = tr[n] ?: (0L to 0L); tr[n] = p.first to (p.second + t.baseAmountMinor) }
            }
        }
        memberPerf = mPerf.map { (k, v) -> Triple(k, v.first, v.second) }.sortedByDescending { it.third }
        trial = tr.map { (k, v) -> Triple(k, v.first, v.second) }
    }

    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("گزارش‌های مالی", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.entries.forEach { p ->
                    FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label, fontSize = 11.sp) })
                }
            }
        }
        if (period == Period.FISCAL) {
            item {
                Text(
                    "سال مالی جاری: از ${fiscalMonthNames[fiscalStart - 1]} تا امروز — بودجه و سود/زیان روی این دوره حساب می‌شود · تغییر شروع از تنظیمات",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (members.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = memberFilter == null, onClick = { memberFilter = null }, label = { Text("همه اعضا", fontSize = 11.sp) })
                    members.forEach { m ->
                        FilterChip(selected = memberFilter == m.id, onClick = { memberFilter = m.id }, label = { Text(m.name, fontSize = 11.sp) })
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionCard(Modifier.weight(1f)) {
                    Text("درآمد", color = incomeColor, style = MaterialTheme.typography.labelMedium)
                    MoneyText(totals.income, base?.code ?: "IRR", bold = true)
                }
                SectionCard(Modifier.weight(1f)) {
                    Text("هزینه", color = expenseColor, style = MaterialTheme.typography.labelMedium)
                    MoneyText(totals.expense, base?.code ?: "IRR", bold = true)
                }
                SectionCard(Modifier.weight(1f)) {
                    Text("مانده", style = MaterialTheme.typography.labelMedium)
                    MoneyText(totals.net, base?.code ?: "IRR", bold = true)
                }
            }
        }
        item {
            SectionCard(title = "ترکیب هزینه بر اساس دسته‌بندی") {
                val items = catSums.entries.sortedByDescending { it.value }.take(8)
                if (items.isEmpty()) {
                    Text("داده‌ای برای این بازه نیست", fontSize = 13.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PieChart(items.mapIndexed { i, e -> parseColorSafe(palette(i)) to e.value.toDouble() })
                        Spacer(Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) {
                            items.forEachIndexed { i, e ->
                                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("■", color = parseColorSafe(palette(i)), fontSize = 14.sp)
                                    Spacer(Modifier.padding(3.dp))
                                    Text(catNames[e.key] ?: "—", fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                    MoneyText(e.value, base?.code ?: "IRR", fontSize = 11)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard(title = "مقایسه هزینه ماه‌ها") {
                MiniBarChart(monthly, barColor = expenseColor)
            }
        }
        if (memberPerf.isNotEmpty()) {
            item {
                SectionCard(title = "عملکرد اشخاص و اعضا (شخص مرتبط)") {
                    memberPerf.forEach { (name, cnt, exp) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$name ($cnt مورد)", fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            MoneyText(exp, base?.code ?: "IRR", fontSize = 12)
                        }
                    }
                }
            }
        }
        if (trial.isNotEmpty()) {
            item {
                SectionCard(title = "ترازنامه آزمایشی (جریان هر حساب)") {
                    trial.forEach { (name, inAmt, outAmt) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Column(horizontalAlignment = Alignment.End) {
                                Text("دریافت: ${fmtMoney(inAmt, base?.code ?: "IRR")}", fontSize = 10.5.sp, color = incomeColor)
                                Text("پرداخت: ${fmtMoney(outAmt, base?.code ?: "IRR")}", fontSize = 10.5.sp, color = expenseColor)
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionCard(title = "خروجی و ابزار") {
                TextButton(onClick = { exportCsv(nav) }) { Text("📄 خروجی CSV تراکنش‌های بازه") }
                TextButton(onClick = { nav.navigate(com.divan.app.ui.nav.Routes.FAMILY) }) { Text("👨‍👩‍👧 گزارش اعضای خانواده") }
                TextButton(onClick = { WorkScheduler.triggerSyncNow(ServiceLocator.context()) }) { Text("🔄 تلاش برای همگام‌سازی") }
            }
        }
    }
}

private fun exportCsv(nav: NavHostController) {
    // handled in Settings → Backup screen (SAF file creation)
    nav.navigate(com.divan.app.ui.nav.Routes.BACKUP)
}
private fun palette(i: Int): String = listOf(
    "#2E7D5B", "#D9B25F", "#4A7FD4", "#E2574C", "#8E6BC1", "#3AAFA9", "#E88D3C", "#6B8E23",
)[i % 8]
