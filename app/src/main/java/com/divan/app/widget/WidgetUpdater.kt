package com.divan.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Request refresh of all widgets after data changes. */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun requestUpdateAll() {
        scope.launch {
            runCatching { QuickExpenseWidget().updateAll(appContext()) }
            runCatching { BalanceWidget().updateAll(appContext()) }
        }
    }

    @Volatile
    private var ctx: Context? = null

    fun init(context: Context) { ctx = context.applicationContext }

    private fun appContext(): Context =
        ctx ?: throw IllegalStateException("WidgetUpdater.init not called")
}
