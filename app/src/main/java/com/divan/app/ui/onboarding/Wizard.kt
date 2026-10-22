package com.divan.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import com.divan.app.R
import com.divan.app.core.db.entity.Account
import com.divan.app.core.db.entity.AccountType
import com.divan.app.core.db.entity.MemberRole
import com.divan.app.core.security.PinLock
import com.divan.app.data.AccountRepo
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.common.fmtMoney
import kotlinx.coroutines.launch
import java.util.UUID

/** Step-by-step setup wizard. Every choice is re-editable later in Settings. */
@Composable
fun WizardScreen(nav: NavHostController) {
    var step by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val titles = listOf(
        R.string.wiz_welcome, R.string.wiz_lang, R.string.wiz_currency, R.string.wiz_family,
        R.string.wiz_account, R.string.wiz_categories, R.string.wiz_permissions,
        R.string.wiz_security, R.string.wiz_done,
    )
    val total = titles.size

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        LinearProgressIndicator(
            progress = { (step + 1f) / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(stringRes(titles[step]), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                0 -> WelcomeStep()
                1 -> LanguageStep()
                2 -> CurrencyStep()
                3 -> FamilyStep()
                4 -> AccountStep()
                5 -> CategoriesStep()
                6 -> PermissionStep()
                7 -> SecurityStep()
                else -> DoneStep()
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step > 0) OutlinedButton(onClick = { step-- }) { Text(stringRes(R.string.back)) } else Spacer(Modifier.width(8.dp))
            Row {
                if (step in 2..7) TextButton(onClick = { step++ }) { Text(stringRes(R.string.skip)) }
                Button(onClick = {
                    if (step == total - 1) finishWizard(nav, scope) else step++
                }) { Text(if (step == total - 1) stringRes(R.string.finish) else stringRes(R.string.next)) }
            }
        }
    }
}

@Composable
private fun stringRes(id: Int) = androidx.compose.ui.res.stringResource(id)

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("🏠💰", fontSize = 56.sp)
        Spacer(Modifier.height(10.dp))
        Text(stringRes(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(stringRes(R.string.wiz_welcome_text), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LanguageStep() {
    val langs = listOf("fa" to "فارسی", "en" to "English", "de" to "Deutsch", "tr" to "Türkçe")
    val scope = rememberCoroutineScope()
    Column {
        Text(stringRes(R.string.wiz_lang_text))
        Spacer(Modifier.height(12.dp))
        langs.forEach { (code, label) ->
            Card(
                onClick = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                    scope.launch { ServiceLocator.settings.setLanguage(code) }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) { Text(label, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun CurrencyStep() {
    val scope = rememberCoroutineScope()
    val currencies by ServiceLocator.currencies.all().collectAsState(initial = emptyList())
    val base by ServiceLocator.currencies.base.collectAsState(initial = null)
    val fiscalStart by ServiceLocator.settings.fiscalStartMonth.collectAsState(initial = 1)
    Column {
        Text(stringRes(R.string.wiz_currency_text))
        Spacer(Modifier.height(8.dp))
        currencies.forEach { c ->
            FilterChip(
                selected = base?.code == c.code,
                onClick = { scope.launch { ServiceLocator.currencies.setBase(c.code) } },
                label = { Text("${c.nameFa} (${c.code})") },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        Text(
            stringRes(R.string.wiz_currency_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        // اصول حسابداری — سال مالی زیربنای گزارش‌ها، بودجه و سود/زیان
        Text("سال مالی (شروع دوره)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "طبق اصول حسابداری، همه گزارش‌ها و بودجه‌ها روی یک دوره مالی محاسبه می‌شوند — پیش‌فرض سال شمسی از فروردین است.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val months = listOf("فرو", "ارد", "خرد", "تیر", "مرد", "شهر", "مهر", "آبا", "آذر", "دی", "بهم", "اسف")
            months.forEachIndexed { i, m ->
                FilterChip(
                    selected = fiscalStart == i + 1,
                    onClick = { scope.launch { ServiceLocator.settings.setFiscalStartMonth(i + 1) } },
                    label = { Text(m, fontSize = 10.sp) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FamilyStep() {
    val scope = rememberCoroutineScope()
    var familyName by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        familyName = ServiceLocator.settings.familyNameNow()
    }
    var memberName by remember { mutableStateOf("") }
    val members by ServiceLocator.family.members().collectAsState(initial = emptyList())
    Column {
        com.divan.app.ui.common.Field(stringRes(R.string.family_name), familyName) {
            familyName = it
            scope.launch { ServiceLocator.settings.setFamilyName(it); ServiceLocator.family.ensure(it) }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(memberName, { memberName = it }, Modifier.weight(1f), label = { Text(stringRes(R.string.add_member)) })
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (memberName.isNotBlank()) {
                    scope.launch {
                        ServiceLocator.family.addMember(memberName.trim(), MemberRole.MEMBER, "#2E7D5B")
                        memberName = ""
                    }
                }
            }) { Text("+") }
        }
        Spacer(Modifier.height(8.dp))
        members.forEach { m ->
            Text("• ${m.name} – ${roleLabel(m.role.name)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun roleLabel(name: String): String = when (name) {
    "OWNER" -> stringRes(R.string.role_owner)
    "ADMIN" -> stringRes(R.string.role_admin)
    "FINANCE" -> stringRes(R.string.role_finance)
    "READ_ONLY" -> stringRes(R.string.role_readonly)
    else -> stringRes(R.string.role_member)
}

@Composable
private fun AccountStep() {
    val scope = rememberCoroutineScope()
    val accounts by ServiceLocator.accounts.all().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.CASH) }
    var card4 by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    val base by ServiceLocator.currencies.base.collectAsState(initial = null)

    Column {
        if (accounts.isEmpty()) {
            com.divan.app.ui.common.Field(stringRes(R.string.acc_name), name) { name = it }
            Spacer(Modifier.height(8.dp))
            Text(stringRes(R.string.acc_type))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(AccountType.CASH to R.string.acc_cash, AccountType.CHECKING to R.string.acc_bank, AccountType.CARD to R.string.acc_card).forEach { (t, label) ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(stringRes(label)) })
                }
            }
            if (type == AccountType.CHECKING || type == AccountType.CARD) {
                Spacer(Modifier.height(6.dp))
                com.divan.app.ui.common.Field(stringRes(R.string.acc_card_last4), card4) { card4 = it.take(4) }
            }
            Spacer(Modifier.height(6.dp))
            com.divan.app.ui.common.Field(stringRes(R.string.opening_balance), opening) { opening = it }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        val ts = System.currentTimeMillis()
                        val openingMinor = com.divan.app.core.money.Money.parseToMinor(opening, base?.decimals ?: 0) ?: 0L
                        ServiceLocator.accounts.save(
                            Account(
                                id = UUID.randomUUID().toString(), name = name.trim(),
                                type = type, cardLast4 = card4.ifBlank { null },
                                currencyCode = base?.code ?: "IRR",
                                openingBalanceMinor = openingMinor, openingDate = ts,
                                createdAt = ts, updatedAt = ts,
                            ),
                            isNew = true,
                        )
                        name = ""; card4 = ""; opening = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringRes(R.string.add)) }
        }
        Spacer(Modifier.height(8.dp))
        accounts.forEach { a ->
            SectionCard {
                Text("✔ " + a.name, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun CategoriesStep() {
    val scope = rememberCoroutineScope()
    val cats by ServiceLocator.categories.all().collectAsState(initial = emptyList())
    val roots = cats.filter { it.parentId == null }
    Column {
        Text(stringRes(R.string.wiz_categories_text))
        Spacer(Modifier.height(8.dp))
        roots.forEach { g ->
            val subs = cats.filter { it.parentId == g.id }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = g.active,
                    onCheckedChange = { on -> scope.launch { ServiceLocator.categories.setActive(g.id, on) } },
                )
                Text("${g.icon} ${g.name} (${subs.size})", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PermissionStep() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var smsGranted by remember { mutableStateOf(false) }
    var calGranted by remember { mutableStateOf(false) }
    var contactsGranted by remember { mutableStateOf(false) }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        smsGranted = r[Manifest.permission.RECEIVE_SMS] == true && r[Manifest.permission.READ_SMS] == true
        scope.launch { ServiceLocator.settings.setSmsEnabled(smsGranted) }
    }
    val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        calGranted = r.values.all { it }
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        contactsGranted = it
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PermissionCard(
            "📩", stringRes(R.string.perm_sms_title), stringRes(R.string.perm_sms_desc),
            granted = smsGranted,
            onAllow = { smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) },
            onOpenSettings = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))) },
        )
        PermissionCard(
            "📅", stringRes(R.string.perm_cal_title), stringRes(R.string.perm_cal_desc),
            granted = calGranted,
            onAllow = { calLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
            onOpenSettings = {},
        )
        PermissionCard(
            "👥", stringRes(R.string.perm_contacts_title), stringRes(R.string.perm_contacts_desc),
            granted = contactsGranted,
            onAllow = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
            onOpenSettings = {},
        )
    }
}

@Composable
private fun PermissionCard(icon: String, title: String, desc: String, granted: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row {
                if (granted) {
                    Text("✔ ${stringRes(R.string.granted)}", color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(onClick = onAllow) { Text(stringRes(R.string.allow)) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onOpenSettings) { Text(stringRes(R.string.open_settings)) }
                }
            }
        }
    }
}

@Composable
private fun SecurityStep() {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    Column {
        Text(stringRes(R.string.wiz_security_text))
        Spacer(Modifier.height(10.dp))
        com.divan.app.ui.common.Field(stringRes(R.string.pin_new), pin) { pin = it.take(8) }
        Spacer(Modifier.height(6.dp))
        com.divan.app.ui.common.Field(stringRes(R.string.pin_repeat), pin2) { pin2 = it.take(8) }
        Spacer(Modifier.height(8.dp))
        Button(enabled = pin.length >= 4, onClick = {
            scope.launch {
                if (pin == pin2) {
                    PinLock.setPin(ServiceLocator.settings, pin)
                    msg = stringRes(R.string.pin_saved)
                } else msg = stringRes(R.string.pin_mismatch)
            }
        }) { Text(stringRes(R.string.pin_save)) }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DoneStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("✅", fontSize = 52.sp)
        Spacer(Modifier.height(10.dp))
        Text(stringRes(R.string.wiz_done_text), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Text(stringRes(R.string.wiz_done_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun finishWizard(nav: NavHostController, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        ServiceLocator.settings.setWizardDone(true)
    }
}
