package com.divan.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.work.Configuration
import com.divan.app.core.notify.Notifications
import com.divan.app.core.work.WorkScheduler
import com.divan.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class DivanApp : Application(), Configuration.Provider {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        ServiceLocator.init(this)
        com.divan.app.widget.WidgetUpdater.init(this)
        com.divan.app.core.notify.ServiceLocatorCtx.init(this)
        // Apply persisted language (per-app locales also auto-stored by AppCompat service)
        appScope.launch {
            val lang = ServiceLocator.settings.languageCode.first()
            if (lang != "system") {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(lang)
                )
            }
            // Seed database on first run
            ServiceLocator.seedIfNeeded()
            // Schedule background work
            WorkScheduler.scheduleDaily(this@Divan)
            WorkScheduler.scheduleSync(this@Divan)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
