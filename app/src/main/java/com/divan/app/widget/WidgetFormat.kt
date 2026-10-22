package com.divan.app.widget

import com.divan.app.di.ServiceLocator
import com.divan.app.core.money.Money

/** Shared formatting helper (currency-aware) for widgets & notifications. */
object WidgetFormat {

    suspend fun fmt(minor: Long, currencyCode: String): String {
        val c = runCatching { ServiceLocator.currencies.byCode(currencyCode) }.getOrNull()
        val persian = runCatching { ServiceLocator.settings.persianDigitsNow() }.getOrDefault(true)
        return Money.format(minor, c?.decimals ?: 0, persian, c?.symbol)
    }

    fun fmtStatic(minor: Long, decimals: Int): String =
        Money.format(minor, decimals, persianDigits = true)
}
