package com.divan.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.divan.app.MainActivity
import com.divan.app.di.ServiceLocator
import kotlinx.coroutines.flow.first
import java.util.Calendar

/** Balance widget: total balance + month income/expense, tap opens app. */
class BalanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = runCatching {
            val accounts = ServiceLocator.accounts.allActive().first()
            var total = 0L
            val base = ServiceLocator.currencies.baseOnce()
            accounts.forEach { a ->
                val bal = ServiceLocator.accounts.balanceOf(a.id)
                total += if (a.currencyCode == base.code) bal
                else ServiceLocator.txns.converter.toBase(bal, a.currencyCode, base.code)
            }
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            val t = ServiceLocator.txns.totalsBetween(cal.timeInMillis, System.currentTimeMillis())
            Triple(
                WidgetFormat.fmt(total, base.code),
                WidgetFormat.fmt(t.income, base.code),
                WidgetFormat.fmt(t.expense, base.code),
            )
        }.getOrDefault(Triple("—", "—", "—"))

        provideContent {
            Box(
                modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF0F3D2E))).cornerRadius(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(14.dp).clickableOpen(),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text("موجودی کل", style = TextStyle(color = ColorProvider(Color(0xFFBFD8CC)), fontSize = 11.sp))
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(data.first, style = TextStyle(color = ColorProvider(Color.White), fontSize = 17.sp, fontWeight = FontWeight.Bold))
                    }
                    Column {
                        Text("درآمد: ${data.second}", style = TextStyle(color = ColorProvider(Color(0xFF7FD8A8)), fontSize = 11.sp))
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text("هزینه: ${data.third}", style = TextStyle(color = ColorProvider(Color(0xFFF3A69B)), fontSize = 11.sp))
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun androidx.glance.GlanceModifier.clickableOpen(): androidx.glance.GlanceModifier =
    actionStartActivity<MainActivity>()

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
