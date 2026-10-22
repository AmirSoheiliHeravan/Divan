package com.divan.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.divan.app.MainActivity
import com.divan.app.di.ServiceLocator
import kotlinx.coroutines.flow.first

/** Quick expense widget: one tap → add expense/income/transfer. */
class QuickExpenseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val month = monthSummary()
        provideContent {
            Box(
                modifier = GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFF1B4D3E))).cornerRadius(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(modifier = GlanceModifier.fillMaxWidth().padding(10.dp)) {
                    Text(
                        "دیوان",
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        "هزینه ماه: ${month.second}",
                        style = TextStyle(color = ColorProvider(Color(0xFFBFD8CC)), fontSize = 10.sp),
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        InlineBtn("− هزینه", Color(0xFFE2574C), QuickExpenseAction.TYPE_EXPENSE)
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        InlineBtn("+ درآمد", Color(0xFF2E7D5B), QuickExpenseAction.TYPE_INCOME)
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        InlineBtn("⇄ انتقال", Color(0xFF4A7FD4), QuickExpenseAction.TYPE_TRANSFER)
                    }
                }
            }
        }
    }

    private suspend fun monthSummary(): Pair<Long, String> {
        return runCatching {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0)
            val t = ServiceLocator.txns.totalsBetween(cal.timeInMillis, System.currentTimeMillis())
            val code = ServiceLocator.settings.baseCurrencyNow()
            t.expense to WidgetFormat.fmt(t.expense, code)
        }.getOrDefault(0L to "—")
    }

    @Composable
    private fun androidx.glance.layout.RowScope.InlineBtn(label: String, color: Color, type: String) {
        Box(
            modifier = GlanceModifier.defaultWeight()
                .background(ColorProvider(color))
                .cornerRadius(12.dp)
                .padding(vertical = 8.dp)
                .clickable(actionRunCallback<QuickExpenseAction>(
                    actionParametersOf(QuickExpenseAction.keyType to type)
                )),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
    }

    companion object {
        const val KEY_TYPE = "type"
    }
}

class QuickExpenseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickExpenseWidget()
}

class QuickExpenseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val type = parameters[keyType] ?: TYPE_EXPENSE
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("divan://quick-add?type=$type"), context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { context.startActivity(intent) }
    }

    companion object {
        val keyType = ActionParameters.Key<String>(QuickExpenseWidget.KEY_TYPE)
        const val TYPE_EXPENSE = "expense"
        const val TYPE_INCOME = "income"
        const val TYPE_TRANSFER = "transfer"
    }
}
