package com.divan.app.core.sms

import com.divan.app.core.db.AppDatabase
import com.divan.app.core.db.entity.SmsInboxItem
import com.divan.app.core.db.entity.SmsItemStatus
import com.divan.app.core.db.entity.SmsRule
import com.divan.app.core.db.entity.Txn
import com.divan.app.core.db.entity.TxnSource
import com.divan.app.core.db.entity.TxnStatus
import com.divan.app.core.db.entity.TxnType
import com.divan.app.core.money.Money
import com.divan.app.core.notify.Notifications
import com.divan.app.data.AccountRepo
import com.divan.app.data.CounterpartyRepo
import com.divan.app.data.SmsRepo
import com.divan.app.data.SettingsRepo
import com.divan.app.data.TxnRepo
import java.util.UUID

/** Bank-SMS parsing engine.
 *  – Rules are data (DB table) so any bank of any country can be added.
 *  – Named regex groups: amount / card / account / balance.
 *  – Result: CONFIRMED or PENDING transaction + inbox record + notification. */
class SmsParser(
    private val db: AppDatabase,
    private val txns: TxnRepo,
    private val accounts: AccountRepo,
    private val smsRepo: SmsRepo,
    private val settings: SettingsRepo,
    private val counterparties: CounterpartyRepo,
) {

    data class ParseResult(
        val direction: TxnType?,
        val amountMinor: Long?,
        val cardLast4: String?,
        val accountLast4: String?,
        val balanceMinor: Long?,
    )

    fun parseWithRule(rule: SmsRule, body: String): ParseResult {
        val norm = Money.normalizeDigits(body)
        val regex = runCatching { Regex(rule.pattern) }.getOrNull()
            ?: return ParseResult(null, null, null, null, null)
        val m = regex.find(norm) ?: return ParseResult(null, null, null, null, null)
        val amount = m.groups["amount"]?.value?.let {
            Money.parseToMinor(it, 0)
        }
        val balance = m.groups["balance"]?.value?.let { Money.parseToMinor(it, 0) }
        val card = m.groups["card"]?.value
        val account = m.groups["account"]?.value

        val depositKw = rule.depositKeywords.split("|").filter { it.isNotBlank() }
        val withdrawKw = rule.withdrawKeywords.split("|").filter { it.isNotBlank() }

        val direction = when {
            depositKw.any { norm.contains(it) } -> TxnType.INCOME
            withdrawKw.any { norm.contains(it) } -> TxnType.EXPENSE
            else -> null
        }
        return ParseResult(direction, amount, card, account, balance)
    }

    /** Main entry called by SmsReceiver. Never throws – SMS handling must be safe. */
    suspend fun handleIncoming(sender: String, body: String, date: Long) {
        if (!runCatching { settings.smsEnabledNow() }.getOrDefault(false)) return
        val rules = db.smsDao().enabledRulesOnce()
        val rule = rules.firstOrNull { sender.replace("+", "").endsWith(it.sender.replace("+", "")) }

        val inboxId = UUID.randomUUID().toString()
        db.smsDao().insertInbox(
            SmsInboxItem(
                id = inboxId, sender = sender, body = body, date = date,
                status = SmsItemStatus.UNPARSED, createdAt = System.currentTimeMillis(),
            )
        )

        val parsed = rule?.let { parseWithRule(it, body) }
        val amount = parsed?.amountMinor
        val direction = parsed?.direction

        if (rule == null || amount == null || amount <= 0 || direction == null) {
            db.smsDao().setInboxResult(inboxId, SmsItemStatus.UNPARSED.name, null, rule?.id)
            if (rule != null) Notifications.notifySmsParsed(null, "پیامک ${rule.bankName} قابل تشخیص نبود – از تنظیمات بررسی کنید")
            return
        }

        // duplicate guard: same source+amount within ±2 min
        val dup = db.txnDao().betweenOnce(date - 120_000, date + 120_000).any {
            it.source == TxnSource.SMS && it.amountMinor == amount && it.type == direction
        }
        if (dup) {
            db.smsDao().setInboxResult(inboxId, SmsItemStatus.DUPLICATE.name, null, rule.id)
            return
        }

        // account matching: card last-4 → account last-4 digits → user default
        var account = parsed.cardLast4?.let { db.accountDao().byCardLast4(it) }
        if (account == null) account = parsed.accountLast4?.let { db.accountDao().byCardLast4(it) }
        if (account == null) {
            val def = settings.smsDefaultAccountNow()
            if (def.isNotBlank()) account = db.accountDao().byId(def)
        }

        val autoConfirm = settings.smsAutoConfirmNow()
        val txn = txns.create(
            Txn(
                type = direction, amountMinor = amount,
                currencyCode = account?.currencyCode ?: "IRR",
                accountId = account?.id,
                title = rule.bankName,
                note = body.take(180),
                date = date, source = TxnSource.SMS,
                status = if (autoConfirm) TxnStatus.CONFIRMED else TxnStatus.PENDING,
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
            )
        )
        db.smsDao().setInboxResult(inboxId, SmsItemStatus.PARSED.name, txn.id, rule.id)
        Notifications.notifySmsParsed(txn, "${rule.bankName}: ${if (direction == TxnType.INCOME) "واریز" else "برداشت"} ${com.divan.app.widget.WidgetFormat.fmt(amount, account?.currencyCode ?: "IRR")}")
    }

    /** Live test from the rule editor screen. */
    suspend fun test(rule: SmsRule, sample: String): ParseResult = parseWithRule(rule, sample)
}
