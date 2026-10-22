package com.divan.app.core.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-schedules periodic jobs after reboot / app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            WorkScheduler.scheduleDaily(context)
            WorkScheduler.scheduleSync(context)
        }
    }
}
