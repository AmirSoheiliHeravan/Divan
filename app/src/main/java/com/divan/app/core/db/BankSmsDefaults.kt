package com.divan.app.core.db

import java.util.UUID

/**
 * Built-in Iranian bank SMS definitions (senders + parser patterns).
 * All rules are user-editable / removable from Settings → Bank SMS.
 * Patterns use NAMED regex groups: amount, card, account, balance.
 * Users can add any other bank (Iran or any country) with the same format.
 */
object BankSmsDefaults {

    data class Def(
        val bankName: String,
        val sender: String,
        val pattern: String,
        val depositKw: String,
        val withdrawKw: String,
        val balanceKw: String = "موجودی|مانده",
        val sample: String? = null,
    )

    private const val DEP = "واریز|وصول|شارژ|افزایش موجودی"
    private const val WD = "برداشت|خرید|پرداخت|کسر|هزینه|انتقال وجه"

    /** Generic Iranian pattern: amount + optional card/account last-4 */
    private val iranPattern =
        "(?:مبلغ|به مبلغ)\\s*(?<amount>[\\d,،]+)\\s*ریال.*?(?:کارت\\s*\\d{4}\\*{2,}(?<card>\\d{4})|حساب\\s*\\d*(?<account>\\d{4}))?"

    /** Balance-only pattern (e.g. Melli: موجودی حساب شما 100,000 ریال) */
    private val balancePattern =
        "(?:موجودی|مانده)[^\\d]*(?<balance>[\\d,،]+)\\s*ریال"

    val all: List<Def> = listOf(
        Def("بانک ملی", "9870005151", iranPattern, DEP, WD, sample = "بانک ملی: برداشت به مبلغ 500,000 ریال با کارت 6104********1234"),
        Def("بانک ملی", "9870006044", iranPattern, DEP, WD),
        Def("بانک ملی", "9870005451", balancePattern, DEP, WD, sample = "بانک ملی: موجودی حساب شما 1,250,000 ریال"),
        Def("بانک ملت", "9870004820", iranPattern, DEP, WD, sample = "بانک ملت . مورد: واریز . مبلغ: 100,000 ریال"),
        Def("بانک ملت", "9870004812", iranPattern, DEP, WD),
        Def("بانک صادرات", "9870000721", iranPattern, DEP, WD, sample = "MM10025626 صادرات: برداشت به مبلغ 500,000 ریال از حساب 0123456789"),
        Def("بانک صادرات", "9870007746", iranPattern, DEP, WD),
        Def("بانک تجارت", "9870004321", iranPattern, DEP, WD),
        Def("بانک رفاه", "9870007312", iranPattern, DEP, WD),
        Def("بانک مسکن", "9870000732", iranPattern, DEP, WD),
        Def("بانک کشاورزی", "9870007712", iranPattern, DEP, WD),
        Def("بانک پارسیان", "9870000711", iranPattern, DEP, WD),
        Def("بانک پاسارگاد", "9870007211", iranPattern, DEP, WD),
        Def("بانک سامان", "9870000751", iranPattern, DEP, WD),
        Def("بانک سپه", "9870000775", iranPattern, DEP, WD),
        Def("بانک شهر", "9870000802", iranPattern, DEP, WD),
        Def("بانک آینده", "9870000862", iranPattern, DEP, WD),
        Def("بانک کوثر", "9870000812", iranPattern, DEP, WD),
        Def("بانک انصار", "9870007012", iranPattern, DEP, WD),
        Def("بانک سرمایه", "9870000762", iranPattern, DEP, WD),
        Def("بانک کارآفرین", "9870000972", iranPattern, DEP, WD),
        Def("بانک مهر ایران", "9870005881", iranPattern, DEP, WD),
    )

    fun rules(): List<SmsRule> = all.map {
        SmsRule(
            id = UUID.randomUUID().toString(),
            bankName = it.bankName,
            sender = it.sender,
            pattern = it.pattern,
            depositKeywords = it.depositKw,
            withdrawKeywords = it.withdrawKw,
            balanceKeywords = it.balanceKw,
            enabled = true,
            isBuiltIn = true,
            sample = it.sample,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
    }
}
