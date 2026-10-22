package com.divan.app.ui.counterparties

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.divan.app.core.contacts.ContactsHelper
import com.divan.app.core.db.entity.Counterparty
import com.divan.app.core.db.entity.CounterpartyKind
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.SectionCard
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CounterpartiesScreen(nav: NavHostController) {
    val cps by ServiceLocator.counterparties.all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(CounterpartyKind.PERSON) }
    var linkedName by remember { mutableStateOf<String?>(null) }
    var linkedPhone by remember { mutableStateOf<String?>(null) }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
        if (uri != null) {
            val info = ContactsHelper.resolve(ctx, uri)
            if (info != null) {
                linkedName = info.name
                linkedPhone = info.phone
                if (name.isBlank()) name = info.name
            }
        }
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) pickContact.launch(null)
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        CurrencyTopBar(title = "طرف‌حساب‌ها") { nav.popBackStack() }
        SectionCard(title = "افزودن (از مخاطبین یا دستی)") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.FilterChip(
                    selected = true, onClick = { contactsPermission.launch(android.Manifest.permission.READ_CONTACTS) },
                    label = { Text("👥 انتخاب از مخاطبین") },
                )
            }
            Spacer(Modifier.height(6.dp))
            com.divan.app.ui.common.Field("نام", name) { name = it }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(CounterpartyKind.PERSON to "شخص", CounterpartyKind.SHOP to "فروشگاه", CounterpartyKind.BANK to "بانک", CounterpartyKind.COMPANY to "شرکت").forEach { (k, lbl) ->
                    androidx.compose.material3.FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(lbl, fontSize = 11.sp) })
                }
            }
            if (linkedPhone != null) {
                Text("موبایل: $linkedPhone", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        val ts = System.currentTimeMillis()
                        ServiceLocator.counterparties.save(
                            Counterparty(
                                id = UUID.randomUUID().toString(), name = name.trim(),
                                kind = kind, phone = linkedPhone,
                                createdAt = ts, updatedAt = ts,
                            ), isNew = true,
                        )
                        name = ""; linkedName = null; linkedPhone = null
                    }
                },
            ) { Text("افزودن") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(cps, key = { it.id }) { cp ->
                SectionCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(cp.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(kindLabel(cp.kind), cp.phone).joinToString(" · "),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { scope.launch { ServiceLocator.counterparties.delete(cp.id) } }) {
                            Text("حذف", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

private fun kindLabel(k: CounterpartyKind): String = when (k) {
    CounterpartyKind.PERSON -> "شخص"
    CounterpartyKind.SHOP -> "فروشگاه"
    CounterpartyKind.BANK -> "بانک"
    CounterpartyKind.COMPANY -> "شرکت"
    CounterpartyKind.OTHER -> "سایر"
}
