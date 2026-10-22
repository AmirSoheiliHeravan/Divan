package com.divan.app.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.divan.app.MainActivity
import com.divan.app.R
import com.divan.app.core.db.entity.Txn

object Notifications {
    const val CH_REMINDERS = "reminders"
    const val CH_SMS = "bank_sms"
    const val CH_BUDGET = "budget"
    const val CH_SYNC = "sync"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            NotificationChannel(CH_REMINDERS, "یادآوری‌های مالی", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_SMS, "پیامک‌های بانکی", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_BUDGET, "هشدار بودجه", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_SYNC, "همگام‌سازی", NotificationManager.IMPORTANCE_MIN),
        ).forEach { nm.createNotificationChannel(it) }
    }

    private fun pi(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun notifySmsParsed(txn: Txn?, text: String) {
        val c = ServiceLocatorCtx.get() ?: return
        val b = NotificationCompat.Builder(c, CH_SMS)
            .setSmallIcon(R.drawable.ic_stat_divan)
            .setContentTitle(c.getString(R.string.notif_sms_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi(c))
        if (txn != null) {
            b.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        runCatching { notify(c, 2001, b) }
    }

    fun notifyReminder(id: Int, title: String, text: String) {
        val c = ServiceLocatorCtx.get() ?: return
        val b = NotificationCompat.Builder(c, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_divan)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi(c))
        runCatching { notify(c, id, b) }
    }

    fun notifyBudget(title: String, text: String) {
        val c = ServiceLocatorCtx.get() ?: return
        val b = NotificationCompat.Builder(c, CH_BUDGET)
            .setSmallIcon(R.drawable.ic_stat_divan)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi(c))
        runCatching { notify(c, 3001, b) }
    }

    private fun notify(c: Context, id: Int, b: NotificationCompat.Builder) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, b.build())
    }
}

/** Lazily available application context for notifications from workers. */
object ServiceLocatorCtx {
    @Volatile private var appCtx: Context? = null
    fun init(c: Context) { appCtx = c.applicationContext }
    fun get(): Context? = appCtx
}
