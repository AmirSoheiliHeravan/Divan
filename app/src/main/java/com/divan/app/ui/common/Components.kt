package com.divan.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divan.app.core.money.Money
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.theme.Gold
import com.divan.app.ui.theme.Green600
import com.divan.app.ui.theme.Red400
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiCtx {
    var persianDigits: Boolean = true
    var baseCurrencyCode: String = "IRR"
    suspend fun refresh() {
        persianDigits = ServiceLocator.settings.persianDigitsNow()
        baseCurrencyCode = ServiceLocator.settings.baseCurrencyNow()
    }
}

suspend fun currencyDecimals(code: String): Int =
    ServiceLocator.currencies.byCode(code)?.decimals ?: 0

suspend fun currencySymbol(code: String): String =
    ServiceLocator.currencies.byCode(code)?.symbol ?: code

suspend fun fmtMoney(minor: Long, code: String): String {
    val d = currencyDecimals(code)
    val s = currencySymbol(code)
    return Money.format(minor, d, UiCtx.persianDigits, s)
}

fun fmtDate(ts: Long): String {
    val f = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    return if (UiCtx.persianDigits) toPersianDigits(f.format(Date(ts))) else f.format(Date(ts))
}

fun fmtDateTime(ts: Long): String {
    val f = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
    return if (UiCtx.persianDigits) toPersianDigits(f.format(Date(ts))) else f.format(Date(ts))
}

fun toPersianDigits(s: String): String {
    val fa = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    return buildString {
        s.forEach { c -> append(if (c in '0'..'9') fa[c - '0'] else c) }
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    trailing?.invoke()
                }
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun MoneyText(minor: Long, code: String, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false, fontSize: Int = 16) {
    var text by remember { mutableStateOf("…") }
    androidx.compose.runtime.LaunchedEffect(minor, code) {
        text = fmtMoney(minor, code)
    }
    Text(text, color = color, fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null, fontSize = fontSize.sp)
}

@Composable
fun EmptyView(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🗂", fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

/** Simple labeled field */
@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = value, onValueChange = onChange, singleLine = singleLine, modifier = Modifier.fillMaxWidth())
    }
}

/** Donut/pie chart via Canvas */
@Composable
fun PieChart(data: List<Pair<Color, Double>>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.second }
    if (total <= 0.0) return
    Canvas(modifier.size(160.dp)) {
        var start = -90f
        data.forEach { (color, value) ->
            val sweep = (value / total * 360.0).toFloat()
            drawArc(
                color = color, startAngle = start, sweepAngle = sweep, useCenter = true,
                topLeft = Offset.Zero, size = Size(size.width, size.height),
            )
            start += sweep
        }
    }
}

/** Horizontal bars for month comparison */
@Composable
fun MiniBarChart(values: List<Pair<String, Double>>, modifier: Modifier = Modifier, barColor: Color = Green600) {
    val max = values.maxOfOrNull { it.second } ?: 0.0
    if (max <= 0.0) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { (label, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(64.dp), fontSize = 12.sp)
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((v / max).toFloat().coerceIn(0.02f, 1f))
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
fun ColorDot(colorHex: String) {
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(parseColorSafe(colorHex))
    )
}

fun parseColorSafe(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Green600)

val incomeColor = Green600
val expenseColor = Red400
val transferColor = Color(0xFF4A7FD4)
val accentGold = Gold
