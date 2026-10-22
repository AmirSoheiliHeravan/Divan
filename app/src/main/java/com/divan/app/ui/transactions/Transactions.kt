package com.divan.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.divan.app.core.db.entity.Txn
import com.divan.app.core.db.entity.TxnStatus
import com.divan.app.core.db.entity.TxnType
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.EmptyView
import com.divan.app.ui.common.MoneyText
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.expenseColor
import com.divan.app.ui.common.fmtDate
import com.divan.app.ui.common.incomeColor
import com.divan.app.ui.dashboard.typeLabel
import com.divan.app.ui.nav.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(nav: NavHostController) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<TxnType?>(null) }
    val scope = rememberCoroutineScope()

    val all by (if (query.isBlank()) ServiceLocator.txns.all() else ServiceLocator.txns.search(query))
        .collectAsState(initial = emptyList())

    val filtered = all.filter { typeFilter == null || it.type == typeFilter }
        .groupBy { dayKey(it.date) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("جست‌وجو در تراکنش‌ها…") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("همه") })
            FilterChip(selected = typeFilter == TxnType.EXPENSE, onClick = { typeFilter = TxnType.EXPENSE }, label = { Text("هزینه") })
            FilterChip(selected = typeFilter == TxnType.INCOME, onClick = { typeFilter = TxnType.INCOME }, label = { Text("درآمد") })
            FilterChip(selected = typeFilter == TxnType.TRANSFER, onClick = { typeFilter = TxnType.TRANSFER }, label = { Text("انتقال") })
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            EmptyView("تراکنشی یافت نشد")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filtered.forEach { (day, list) ->
                    item(key = "h$day") {
                        Text(
                            day,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(list, key = { it.id }) { t ->
                        TxnRow(t, onDelete = { scope.launch { ServiceLocator.txns.delete(t.id) } })
                    }
                }
            }
        }
    }
}

private fun dayKey(ts: Long): String = fmtDate(ts)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TxnRow(t: Txn, onDelete: () -> Unit) {
    val dismiss = rememberDismissState { value ->
        if (value == DismissValue.DismissedToStart) { onDelete(); true } else false
    }
    SwipeToDismiss(
        state = dismiss,
        background = {
            Row(
                Modifier.fillMaxSize().padding(end = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, null, tint = expenseColor)
            }
        },
        dismissContent = {
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.title.ifBlank { typeLabel(t.type) }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            if (t.status == TxnStatus.PENDING) {
                                Spacer(Modifier.width(6.dp))
                                com.divan.app.ui.common.StatusChip("در انتظار تأیید", com.divan.app.ui.theme.Gold)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${typeLabel(t.type)} · ${fmtDate(t.date)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoneyText(
                        t.amountMinor, t.currencyCode,
                        color = when (t.type) {
                            TxnType.INCOME, TxnType.LOAN_DISBURSE, TxnType.DEBT_COLLECT_IN, TxnType.ASSET_SELL, TxnType.SALE -> incomeColor
                            TxnType.TRANSFER -> androidx.compose.ui.graphics.Color(0xFF4A7FD4)
                            else -> expenseColor
                        },
                        bold = true,
                    )
                }
            }
        },
    )
}
