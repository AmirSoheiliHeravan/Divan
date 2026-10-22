package com.divan.app.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Money utilities – all amounts stored as Long minor units (never floating point). */
object Money {

    /** Normalize Persian/Arabic digits to Latin and strip separators. */
    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        input.forEach { c ->
            when (c) {
                in '۰'..'۹' -> sb.append(c - '۰')
                in '٠'..'٩' -> sb.append(c - '٠')
                '٬', '،', ',', '\u200f', '\u200e', ' ' -> {} // separators & marks removed
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Parse a user-typed amount string into minor units using currency decimals. */
    fun parseToMinor(input: String, decimals: Int): Long? {
        val clean = normalizeDigits(input).replace(Regex("[^0-9.]"), "")
        if (clean.isEmpty() || clean == ".") return null
        val bd = runCatching { BigDecimal(clean) }.getOrNull() ?: return null
        return bd.movePointRight(decimals).setScale(0, RoundingMode.HALF_UP).toLongOrNull()
    }

    /** Minor units → display string with grouping and optional Persian digits. */
    fun format(minor: Long, decimals: Int, persianDigits: Boolean = false, withSymbol: String? = null): String {
        val bd = BigDecimal(minor).movePointLeft(decimals).setScale(decimals, RoundingMode.HALF_UP)
        val nf = NumberFormat.getNumberInstance(if (persianDigits) Locale("fa") else Locale.US)
        nf.minimumFractionDigits = 0
        nf.maximumFractionDigits = decimals
        val s = nf.format(bd)
        return if (withSymbol != null) "$s $withSymbol" else s
    }

    /** Convert base-amount using a rate with banker-safe rounding. */
    fun convert(amountMinor: Long, fromDecimals: Int, toDecimals: Int, rate: Double): Long {
        if (rate <= 0.0) return amountMinor
        val major = BigDecimal(amountMinor).movePointLeft(fromDecimals)
        return major.multiply(BigDecimal(rate))
            .movePointRight(toDecimals)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    fun signedDelta(type: com.divan.app.core.db.entity.TxnType, amountMinor: Long): Long = when (type) {
        com.divan.app.core.db.entity.TxnType.INCOME,
        com.divan.app.core.db.entity.TxnType.LOAN_DISBURSE,
        com.divan.app.core.db.entity.TxnType.DEBT_COLLECT_IN,
        com.divan.app.core.db.entity.TxnType.ASSET_SELL,
        com.divan.app.core.db.entity.TxnType.SALE,
        com.divan.app.core.db.entity.TxnType.SAVINGS_OUT,
        com.divan.app.core.db.entity.TxnType.INVEST_OUT -> amountMinor

        com.divan.app.core.db.entity.TxnType.EXPENSE,
        com.divan.app.core.db.entity.TxnType.LOAN_REPAY,
        com.divan.app.core.db.entity.TxnType.DEBT_REPAY_OUT,
        com.divan.app.core.db.entity.TxnType.ASSET_BUY,
        com.divan.app.core.db.entity.TxnType.PURCHASE,
        com.divan.app.core.db.entity.TxnType.SAVINGS_IN,
        com.divan.app.core.db.entity.TxnType.INVEST_IN,
        com.divan.app.core.db.entity.TxnType.FEE -> -amountMinor

        com.divan.app.core.db.entity.TxnType.BALANCE_ADJUST -> amountMinor
        com.divan.app.core.db.entity.TxnType.TRANSFER -> -amountMinor // source side; destination handled separately
    }
}
