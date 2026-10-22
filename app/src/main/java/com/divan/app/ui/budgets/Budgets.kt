package com.divan.app.ui.budgets

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import com.divan.app.core.db.entity.Budget
import com.divan.app.core.db.entity.BudgetScope
import com.divan.app.core.money.Money
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.EmptyView
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.expenseColor
import com.divan.app.ui.common.incomeColor
import com.divan.app.ui.common.toPersianDigits
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

@Composable
fun BudgetsScreen(nav: NavHostController) {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val budgets by ServiceLocator.budgets.forPeriod(year, month).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    val base by ServiceLocator.currencies.base.collectAsState(initial = null)

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "بودجه ماه $month") { nav.popBackStack() }
        TextButton(onClick = { showAdd = true }) { Text("+ بودجه جدید") }
        if (budgets.isEmpty()) EmptyView("برای این ماه بودجه‌ای تعیین نشده است")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(budgets, key = { it.id }) { b ->
                var spent by remember(b.id) { mutableStateOf(0L) }
                androidx.compose.runtime.LaunchedEffect(b.id) { spent = ServiceLocator.budgets.spent(b) }
                val pct = if (b.limitMinor > 0) (spent * 100 / b.limitMinor).toInt() else 0
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (b.scope == BudgetScope.TOTAL) "بودجه کل ماه" else "دسته: ${categoryName(b.categoryId)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "$pct٪",
                            color = if (pct >= 100) expenseColor else if (pct >= b.warnPercent) com.divan.app.ui.theme.Gold else incomeColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (pct >= 100) expenseColor else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("مصرف: ${toPersianDigits(Money.format(spent, base?.decimals ?: 0, false))}", fontSize = 12.sp)
                        Text("سقف: ${toPersianDigits(Money.format(b.limitMinor, base?.decimals ?: 0, false))}", fontSize = 12.sp)
                    }
                    // کف هشدار (پارمیس) — از این درصد، رنگ تغییر و هشدار فعال می‌شود
                    val floorVal = b.limitMinor * b.warnPercent / 100
                    val toFloor = floorVal - spent
                    Text(
                        when {
                            pct >= 100 -> "از سقف گذشته — هشدار"
                            pct >= b.warnPercent -> "از کف هشدار (٪${toPersianDigits(b.warnPercent.toString())}) گذشته است"
                            else -> "کف هشدار: ٪${toPersianDigits(b.warnPercent.toString())} · ${toPersianDigits(Money.format(toFloor, base?.decimals ?: 0, false))} تا کف",
                        },
                        fontSize = 11.sp,
                        color = if (pct >= b.warnPercent) expenseColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { scope.launch { ServiceLocator.budgets.delete(b.id) } }) { Text("حذف") }
                }
            }
        }
    }

    if (showAdd) {
        val cats by ServiceLocator.categories.allActive().collectAsState(initial = emptyList())
        var limit by remember { mutableStateOf("") }
        var catId by remember { mutableStateOf<String?>(null) }
        var isTotal by remember { mutableStateOf(false) }
        var warnPct by remember { mutableStateOf(80) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("بودجه جدید (ماه جاری)") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = isTotal, onClick = { isTotal = true }, label = { Text("کل ماه") })
                        FilterChip(selected = !isTotal, onClick = { isTotal = false }, label = { Text("یک دسته") })
                    }
                    Spacer(Modifier.height(6.dp))
                    if (!isTotal) {
                        com.divan.app.ui.common.FilterChipRow(
                            options = cats.filter { it.parentId != null }.map { it.id to it.name },
                            selected = catId, onSelect = { catId = it },
                        )
                        if (catId == null) {
                            com.divan.app.ui.common.FilterChipRow(
                                options = cats.filter { it.parentId == null }.map { it.id to it.name },
                                selected = catId, onSelect = { catId = it },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    com.divan.app.ui.common.Field("سقف بودجه", limit) { limit = it }
                    Spacer(Modifier.height(6.dp))
                    Text("کف هشدار (درصد سقف)", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(50, 70, 80, 90).forEach { p ->
                            FilterChip(selected = warnPct == p, onClick = { warnPct = p }, label = { Text("٪$p", fontSize = 11.sp) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (Money.parseToMinor(limit, base?.decimals ?: 0) ?: 0L) > 0 && (isTotal || catId != null),
                    onClick = {
                        val ts = System.currentTimeMillis()
                        scope.launch {
                            ServiceLocator.budgets.save(
                                Budget(
                                    id = UUID.randomUUID().toString(),
                                    scope = if (isTotal) BudgetScope.TOTAL else BudgetScope.CATEGORY,
                                    categoryId = if (isTotal) null else catId,
                                    year = year, month = month,
                                    limitMinor = Money.parseToMinor(limit, base?.decimals ?: 0) ?: 0L,
                                    warnPercent = warnPct,
                                    createdAt = ts, updatedAt = ts,
                                ), isNew = true,
                            )
                            showAdd = false
                        }
                    },
                ) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun categoryName(id: String?): String {
    if (id == null) return "—"
    var name by remember(id) { mutableStateOf("…") }
    androidx.compose.runtime.LaunchedEffect(id) {
        name = ServiceLocator.categories.byId(id)?.name ?: "—"
    }
    return name
}
