package com.divan.app.core.money

import com.divan.app.core.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Currency conversion using the exchange rate table.
 *  Historical transactions always keep their own rate; this helper is for
 *  creating new records and live reports. */
class CurrencyConverter(private val db: AppDatabase) {

    suspend fun latestRate(code: String, baseCode: String): Double = withContext(Dispatchers.IO) {
        if (code == baseCode) return@withContext 1.0
        val r = db.rateDao().latest(code) ?: return@withContext 0.0
        r.rateToBase
    }

    /** rate for a past timestamp (rate history is immutable) */
    suspend fun rateAt(code: String, baseCode: String, ts: Long): Double = withContext(Dispatchers.IO) {
        if (code == baseCode) return@withContext 1.0
        db.rateDao().latestAt(code, ts)?.rateToBase ?: latestRate(code, baseCode)
    }

    suspend fun toBase(amountMinor: Long, fromCode: String, baseCode: String, atTs: Long = System.currentTimeMillis()): Long {
        if (fromCode == baseCode) return amountMinor
        val from = db.currencyDao().byCode(fromCode) ?: return amountMinor
        val base = db.currencyDao().byCode(baseCode) ?: return amountMinor
        val rate = rateAt(fromCode, baseCode, atTs)
        return Money.convert(amountMinor, from.decimals, base.decimals, rate)
    }
}
