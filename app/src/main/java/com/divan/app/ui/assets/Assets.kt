package com.divan.app.ui.assets

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
import com.divan.app.core.db.entity.AssetItem
import com.divan.app.core.db.entity.AssetStatus
import com.divan.app.core.money.Money
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.EmptyView
import com.divan.app.ui.common.MoneyText
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.StatusChip
import com.divan.app.ui.common.toPersianDigits
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AssetsScreen(nav: NavHostController) {
    val assets by ServiceLocator.assets.all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var sellTarget by remember { mutableStateOf<AssetItem?>(null) }
    var sellPrice by remember { mutableStateOf("") }

    val base by ServiceLocator.currencies.base.collectAsState(initial = null)

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "اموال و دارایی‌ها") { nav.popBackStack() }
        TextButton(onClick = { showAdd = true }) { Text("+ دارایی جدید") }
        if (assets.isEmpty()) EmptyView("دارایی‌ای ثبت نشده است – ملک، خودرو، طلا و تجهیزات را اینجا مدیریت کنید")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(assets, key = { it.id }) { a ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    a.category.ifBlank { null },
                                    "خرید: ${toPersianDigits(Money.format(a.buyPriceMinor, 0, false))}",
                                    a.estValueMinor?.let { "ارزش فعلی: ${toPersianDigits(Money.format(it, 0, false))}" },
                                    a.location,
                                ).joinToString(" · "),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusChip(
                            when (a.status) {
                                AssetStatus.OWNED -> "مالکیت"
                                AssetStatus.SOLD -> "فروخته‌شده"
                                AssetStatus.DISCARDED -> "منصرف/اسقاط"
                            },
                            if (a.status == AssetStatus.OWNED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (a.status == AssetStatus.OWNED) {
                        Row {
                            TextButton(onClick = {
                                sellTarget = a
                                sellPrice = Money.format(a.estValueMinor ?: a.buyPriceMinor, 0, false)
                            }) { Text("فروش") }
                            TextButton(onClick = { scope.launch { ServiceLocator.assets.delete(a.id) } }) {
                                Text("حذف", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddAssetDialog(
            onDismiss = { showAdd = false },
            onSave = { asset, accId ->
                scope.launch {
                    ServiceLocator.assets.save(asset, isNew = true, buyAccountId = accId)
                    showAdd = false
                }
            },
        )
    }

    sellTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { sellTarget = null },
            title = { Text("فروش ${target.name}") },
            text = {
                Column {
                    com.divan.app.ui.common.Field("قیمت فروش", sellPrice) { sellPrice = it }
                    Text("سود/زیان بر اساس بهای تمام‌شده محاسبه و در گزارش نمایش داده می‌شود.", fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val price = Money.parseToMinor(sellPrice, 0) ?: 0L
                        val acc = ServiceLocator.accounts.all().first().firstOrNull()?.id
                        if (acc != null) ServiceLocator.assets.sell(target.id, price, acc, System.currentTimeMillis())
                        sellTarget = null
                    }
                }) { Text("ثبت فروش") }
            },
            dismissButton = { TextButton(onClick = { sellTarget = null }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun AddAssetDialog(onDismiss: () -> Unit, onSave: (AssetItem, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var buyPrice by remember { mutableStateOf("") }
    var estValue by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var insurance by remember { mutableStateOf("") }
    var buyAccount by remember { mutableStateOf<String?>(null) }
    val accounts by ServiceLocator.accounts.allActive().collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دارایی جدید") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                com.divan.app.ui.common.Field("نام دارایی", name) { name = it }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("دسته (ملک، خودرو، طلا…)", category) { category = it }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("قیمت خرید", buyPrice) { buyPrice = it }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("ارزش تخمینی فعلی", estValue) { estValue = it }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("محل نگهداری", location) { location = it }
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field("بیمه و تاریخ تمدید", insurance) { insurance = it }
                Spacer(Modifier.height(6.dp))
                EntityDropdown(
                    label = "حساب خرید (اختیاری)", items = accounts,
                    selectedId = buyAccount, getId = { it.id }, getText = { it.name },
                    onSelect = { buyAccount = it.id },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val ts = System.currentTimeMillis()
                    onSave(
                        AssetItem(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(), category = category,
                            buyDate = ts,
                            buyPriceMinor = Money.parseToMinor(buyPrice, 0) ?: 0L,
                            estValueMinor = Money.parseToMinor(estValue, 0),
                            estValueDate = if (estValue.isNotBlank()) ts else null,
                            location = location.ifBlank { null },
                            insuranceCompany = insurance.ifBlank { null },
                            currencyCode = "IRR",
                            createdAt = ts, updatedAt = ts,
                        ),
                        buyAccount,
                    )
                },
            ) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
