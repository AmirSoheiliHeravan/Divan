package com.divan.app.core.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.divan.app.core.notify.Notifications
import com.divan.app.di.ServiceLocator
import com.divan.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.first
import java.util.Calendar

/** Daily background jobs: recurring materialization, installment/check reminders,
 *  budget warnings, calendar sync and widget refresh. */
class DailyWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val sl = ServiceLocator
            val ctx = applicationContext

            // 1) recurring transactions
            val created = sl.recurring.materializeDue(System.currentTimeMillis())

            // 2) installment reminders (due in 3 days or late)
            val soon = System.currentTimeMillis() + 3L * 86400000
            sl.loans.unpaidOnce().filter { it.dueDate <= soon }.take(10).forEach { inst ->
                Notifications.notifyReminder(
                    inst.seq + 4000,
                    "یادآوری قسط",
                    "قسط ${inst.seq} تا تاریخ ${fmtDate(inst.dueDate)} سررسید می‌شود"
                )
            }

            // 3) check reminders
            val checks = sl.db.checkDao().dueBetween(System.currentTimeMillis() - 86400000L, soon)
            checks.take(5).forEach { c ->
                Notifications.notifyReminder(
                    (c.id.hashCode() and 0xFFFF) + 5000,
                    "یادآوری چک",
                    "چک ${c.chequeNumber ?: ""} در ${fmtDate(c.dueDate)} سررسید است"
                )
            }

            // 4) budget warnings
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val budgets = sl.db.budgetDao().forPeriodOnce(year, month)
            val warnPct = sl.settings.warnThreshold.first()
            val budgetCalc = com.divan.app.data.BudgetRepo(sl.db, sl.syncQueue, sl.settings)
            budgets.forEach { b ->
                val spent = budgetCalc.spent(b)
                val pct = if (b.limitMinor > 0) (spent * 100 / b.limitMinor) else 0
                if (pct >= warnPct) {
                    Notifications.notifyBudget(
                        "هشدار بودجه",
                        "مصرف بودجه به $pct٪ سقف رسیده است"
                    )
                }
            }

            // 5) calendar sync (if enabled + permitted)
            runCatching { sl.calendarSync.syncAll() }

            // 6) به‌روزرسانی روزانه نرخ‌های زنده (tgju.org + estjt.ir)
            runCatching { sl.rates.refresh() }

            // 7) پشتیبان ابری خودکار — اگر پوشه Drive/SAF انتخاب شده باشد (Poolaki)
            runCatching {
                val treeStr = sl.settings.cloudTreeUriNow()
                if (treeStr.isNotBlank()) {
                    val tree = android.net.Uri.parse(treeStr)
                    val root = android.provider.DocumentsContract
                        .buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
                    val data = sl.backup.serializeBackup()
                    val cr = ctx.contentResolver
                    val newDoc = android.provider.DocumentsContract
                        .createDocument(cr, root, "application/json", sl.backup.backupFileName())
                    newDoc?.let { cr.openOutputStream(it)?.use { os -> os.write(data.toByteArray(Charsets.UTF_8)) } }
                }
            }

            WidgetUpdater.requestUpdateAll()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    private fun fmtDate(ts: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ts
        return "${c.get(Calendar.YEAR)}/${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}"
    }
}
