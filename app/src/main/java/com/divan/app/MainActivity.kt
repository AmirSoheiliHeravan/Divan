package com.divan.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.lifecycleScope
import com.divan.app.core.security.PinLock
import com.divan.app.ui.common.UiCtx
import com.divan.app.ui.nav.AppRoot
import com.divan.app.ui.theme.DivanTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val locked = MutableStateFlow(true)
    private var pendingQuickAdd: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingQuickAdd = parseDeepLink(intent)
        lifecycleScope.launch {
            UiCtx.refresh()
            locked.value = PinLock.isSet(ServiceLocator.settings)
        }

        setContent {
            val themeMode by ServiceLocator.settings.themeMode.collectAsState(initial = "system")
            val fontScale by ServiceLocator.settings.fontScale.collectAsState(initial = 1f)
            val density = androidx.compose.ui.platform.LocalDensity.current
            DivanTheme(
                darkTheme = when (themeMode) {
                    "light" -> false
                    "dark" -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                },
            ) {
                // اندازه فونت برنامه (پارمیس) — ضرب در fontScale سیستم
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                        density.density, density.fontScale * fontScale,
                    ),
                ) {
                    val isLocked by locked.collectAsState()
                    if (isLocked) {
                        LockScreen(
                            onSubmit = { pin ->
                                val ok = PinLock.verify(ServiceLocator.settings, pin)
                                if (ok) locked.value = false
                                ok
                            },
                            onForgot = { /* security policy: only full reset via device settings */ },
                        )
                    } else {
                        AppRoot(quickAddParam = pendingQuickAdd)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        pendingQuickAdd = parseDeepLink(intent)
    }

    private fun parseDeepLink(intent: android.content.Intent?): String? {
        val data = intent?.data ?: return null
        return if (data.scheme == "divan" && data.host == "quick-add") {
            data.getQueryParameter("type") ?: "expense"
        } else null
    }
}

@Composable
private fun LockScreen(onSubmit: suspend (String) -> Boolean, onForgot: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🔐", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("دیوان قفل است", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8) { pin = it; error = false } },
            isError = error,
            placeholder = { Text("رمز ۴ تا ۸ رقمی") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch { error = !onSubmit(pin) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("باز کردن قفل") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onForgot) { Text("راهنما", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)) }
    }
}
