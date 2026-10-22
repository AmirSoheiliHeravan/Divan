package com.divan.app.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import com.divan.app.core.calendar.CalendarSyncManager
import com.divan.app.core.security.PinLock
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.SectionCard
import com.divan.app.ui.nav.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val settings = ServiceLocator.settings

    val lang by settings.languageCode.collectAsState(initial = "system")
    val themeMode by settings.themeMode.collectAsState(initial = "system")
    val persianDigits by settings.persianDigits.collectAsState(initial = true)
    val smsEnabled by settings.smsEnabled.collectAsState(initial = false)
    val smsAuto by settings.smsAutoConfirm.collectAsState(initial = false)
    val calEnabled by settings.calendarEnabled.collectAsState(initial = false)
    val biometric by settings.biometric.collectAsState(initial = false)
    val fontScale by settings.fontScale.collectAsState(initial = 1f)
    val fiscalStart by settings.fiscalStartMonth.collectAsState(initial = 1)
    val serverUrl by settings.serverUrl.collectAsState(initial = "")
    val pending by ServiceLocator.system.syncPendingCount().collectAsState(initial = 0)

    var calPicker by remember { mutableStateOf(false) }
    val calManager = remember { CalendarSyncManager(ctx, ServiceLocator.db, settings) }

    val calPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) {
            scope.launch {
                settings.setCalendarEnabled(true)
                calPicker = true
            }
        }
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        scope.launch { settings.setSmsEnabled(r.values.all { it }) }
    }
    val biometricPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { settings.setBiometric(it) }
    }

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState())) {
        CurrencyTopBar(title = "تنظیمات") { nav.popBackStack() }

        SectionCard(title = "🌐 زبان و نمایش") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system" to "سیستم", "fa" to "فارسی", "en" to "English", "de" to "Deutsch", "tr" to "Türkçe").forEach { (code, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = lang == code,
                        onClick = {
                            scope.launch {
                                settings.setLanguage(code)
                                AppCompatDelegate.setApplicationLocales(
                                    if (code == "system") LocaleListCompat.getEmptyLocaleList()
                                    else LocaleListCompat.forLanguageTags(code)
                                )
                            }
                        },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("تم برنامه (تاریک / روشن)", fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("system" to "سیستم", "light" to "روشن ☀", "dark" to "تاریک 🌙").forEach { (code, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = themeMode == code,
                        onClick = { scope.launch { settings.setThemeMode(code) } },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("اندازه فونت برنامه (پارمیس)", fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.9f to "کوچک", 1f to "معمولی", 1.15f to "بزرگ", 1.3f to "خیلی بزرگ").forEach { (v, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = kotlin.math.abs(fontScale - v) < 0.01f,
                        onClick = { scope.launch { settings.setFontScale(v) } },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            ToggleRow("نمایش ارقام فارسی (۱۲۳)", persianDigits) { scope.launch { settings.setPersianDigits(it) } }
            Spacer(Modifier.height(4.dp))
            // اصول حسابداری — سال مالی زیربنای گزارش‌ها و بودجه
            Text("سال مالی (شروع دوره) — زیربنای گزارش‌ها", fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val months = listOf("فرو", "ارد", "خرد", "تیر", "مرد", "شهر", "مهر", "آبا", "آذر", "دی", "بهم", "اسف")
                months.forEachIndexed { i, m ->
                    FilterChip(
                        selected = fiscalStart == i + 1,
                        onClick = { scope.launch { settings.setFiscalStartMonth(i + 1) } },
                        label = { Text(m, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            TextButton(onClick = { nav.navigate(Routes.CURRENCIES) }) { Text("💱 منابع نرخ زنده: tgju.org (ارز) · estjt.ir (طلا) ›") }
            TextButton(onClick = { nav.navigate(Routes.CURRENCIES) }) { Text("💱 ارزها و نرخ تبدیل ›") }
        }

        SectionCard(title = "📩 پیامک بانکی") {
            ToggleRow("خواندن پیامک بانک‌ها", smsEnabled) { on ->
                if (on) smsPermission.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                else scope.launch { settings.setSmsEnabled(false) }
            }
            ToggleRow("ثبت خودکار بدون تأیید (غیرفعال = تأیید دستی)", smsAuto) { scope.launch { settings.setSmsAutoConfirm(it) } }
            TextButton(onClick = { nav.navigate(Routes.SMS_RULES) }) { Text("قواعد بانک‌ها (افزودن بانک ایران/خارج) ›") }
        }

        SectionCard(title = "📅 تقویم سیستم‌عامل") {
            ToggleRow("سینک سررسیدها با تقویم گوشی", calEnabled) { on ->
                if (on) calPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                else scope.launch { settings.setCalendarEnabled(false) }
            }
            val cals = remember(calEnabled) { if (calEnabled) calManager.listCalendars() else emptyList() }
            cals.forEach { c ->
                TextButton(onClick = { scope.launch { settings.setCalendarId(c.id) } }) {
                    Text("🗓 ${c.name} (${c.account})")
                }
            }
            if (cals.isEmpty() && calEnabled) Text("تقویمی یافت نشد – یک تقویم در گوشی تعریف کنید", fontSize = 12.sp)
            TextButton(onClick = { scope.launch { calManager.syncAll() } }) { Text("همگام‌سازی الان") }
        }

        SectionCard(title = "👥 مخاطبین") {
            Text(
                "برای اتصال طرف‌حساب‌ها به مخاطبین گوشی، هنگام افزودن طرف‌حساب دکمه «انتخاب از مخاطبین» را بزنید.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { nav.navigate(Routes.COUNTERPARTIES) }) { Text("مدیریت طرف‌حساب‌ها ›") }
        }

        SectionCard(title = "🔐 امنیت") {
            var pinState by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { pinState = PinLock.isSet(settings) }
            ToggleRow("قفل با زیست‌سنجی (اثر انگشت/چهره)", biometric) {
                biometricPermission.launch(Manifest.permission.USE_BIOMETRIC)
            }
            Row {
                TextButton(onClick = {
                    scope.launch {
                        PinLock.setPin(settings, "1234")
                        pinState = true
                    }
                }) { Text("تعیین PIN (پیش‌فرض ۱۲۳۴ – عوض کنید)") }
                if (pinState) {
                    TextButton(onClick = { scope.launch { PinLock.clear(settings); pinState = false } }) {
                        Text("حذف PIN")
                    }
                }
            }
        }

        SectionCard(title = "☁️ همگام‌سازی و سرور") {
            var url by remember { mutableStateOf(serverUrl) }
            LaunchedEffect(serverUrl) { url = serverUrl }
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("آدرس سرور (اختیاری – API آماده است)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Button(onClick = { scope.launch { settings.setServerUrl(url.trim()) } }) { Text("ذخیره") }
                Spacer(Modifier.padding(6.dp))
                TextButton(onClick = { WorkScheduler.triggerSyncNow(ctx) }) { Text("همگام‌سازی الان") }
            }
            Text(
                if (pending > 0) "در صف انتظار: $pending عملیات" else "صف همگام‌سازی خالی است",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { nav.navigate(Routes.BACKUP) }) { Text("پشتیبان‌گیری و خروجی اکسل (CSV) ›") }
        }

        SectionCard(title = "🧩 ماژول‌ها") {
            listOf(
                "حساب‌ها" to Routes.ACCOUNTS,
                "دسته‌بندی‌ها" to Routes.CATEGORIES,
                "وام و اقساط" to Routes.LOANS,
                "بدهی و طلب" to Routes.DEBTS,
                "چک‌ها" to Routes.CHECKS,
                "بودجه" to Routes.BUDGETS,
                "پرداخت دوره‌ای" to Routes.RECURRING,
                "کالا و انبار" to Routes.PRODUCTS,
                "اموال و دارایی" to Routes.ASSETS,
                "خانواده" to Routes.FAMILY,
            ).forEach { (label, route) ->
                TextButton(onClick = { nav.navigate(route) }) { Text("$label ›") }
            }
        }

        SectionCard(title = "📜 تاریخچه تغییرات") {
            val audit by ServiceLocator.system.auditRecent().collectAsState(initial = emptyList())
            audit.take(10).forEach { a ->
                Text(
                    "${java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.US).format(java.util.Date(a.ts))} · ${a.entityType} · ${a.action}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "درباره") {
            Text("دیوان (Divan) v1.0.0", style = MaterialTheme.typography.titleMedium)
            Text(
                "حسابدار شخصی و خانوادگی – آفلاین‌محور با صف همگام‌سازی امن",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
