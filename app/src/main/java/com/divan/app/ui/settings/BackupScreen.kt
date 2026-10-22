package com.divan.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.common.CurrencyTopBar
import com.divan.app.ui.common.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

@Composable
fun BackupScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bm = ServiceLocator.backup
                val plain = bm.serializeBackup()
                val data = if (password.isNotBlank()) bm.encrypt(plain, password) else plain
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray(Charsets.UTF_8)) }
                }
                status = "پشتیبان ذخیره شد ✔"
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bm = ServiceLocator.backup
                val cal = Calendar.getInstance()
                cal.add(Calendar.YEAR, -5)
                val csv = bm.transactionsCsv(cal.timeInMillis, System.currentTimeMillis())
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                }
                status = "CSV ذخیره شد ✔"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bm = ServiceLocator.backup
                val raw = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                }
                val plain = if (raw.startsWith("KM1:")) {
                    if (password.isBlank()) { status = "این فایل رمزگذاری شده – رمز را وارد کنید"; return@launch }
                    bm.decrypt(raw, password) ?: run { status = "رمز اشتباه است یا فایل خراب است"; return@launch }
                } else raw
                status = if (bm.restore(plain)) "بازیابی کامل شد ✔" else "بازیابی ناموفق بود"
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState())) {
        CurrencyTopBar(title = "پشتیبان‌گیری و خروجی") { nav.popBackStack() }
        SectionCard(title = "رمز اختیاری فایل پشتیبان") {
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("رمز AES (خالی = بدون رمز)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
        }
        Spacer(Modifier.height(10.dp))
        SectionCard(title = "📦 پشتیبان کامل") {
            Text(
                "شامل حساب‌ها، تراکنش‌ها، دسته‌بندی‌ها، وام‌ها، بدهی‌ها، چک‌ها، بودجه، دوره‌ای، کالاها و دارایی‌ها",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Button(onClick = { exportLauncher.launch(ServiceLocator.backup.backupFileName()) }) { Text("ایجاد پشتیبان") }
                Spacer(Modifier.padding(6.dp))
                Button(onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text("بازیابی از فایل") }
            }
        }
        Spacer(Modifier.height(10.dp))
        SectionCard(title = "📄 خروجی CSV (Excel)") {
            Text("خروجی تراکنش‌های ۵ سال اخیر با سازگاری کامل فارسی در اکسل", fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Button(onClick = { csvLauncher.launch("divan_transactions.csv") }) { Text("خروجی CSV") }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(status, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
    }
}
