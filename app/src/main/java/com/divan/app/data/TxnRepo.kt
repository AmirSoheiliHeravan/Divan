package com.divan.app.data

import com.divan.app.core.db.AppDatabase
import com.divan.app.core.db.dao.AuditWriter
import com.divan.app.core.db.entity.AuditAction
import com.divan.app.core.db.entity.Txn
import com.divan.app.core.db.entity.TxnSplit
import com.divan.app.core.db.entity.TxnStatus
import com.divan.app.core.money.CurrencyConverter
import com.divan.app.core.sync.SyncQueue
import com.divan.app.widget.WidgetUpdater
import java.util.UUID

/** Central financial ledger repository. Every write goes through here so that
 *  audit trail + sync queue + widgets stay consistent. */
class TxnRepo(
    private val db: AppDatabase,
    private val sync: SyncQueue,
    private val settings: SettingsRepo,
) {
    private val audit = AuditWriter(db.auditDao())
    val converter = CurrencyConverter(db)

    suspend fun baseCode(): String = settings.baseCurrencyNow()

    /** Fill derived money fields (rate + base equivalent) using stored history. */
    suspend fun withDerived(t: Txn): Txn {
        val base = baseCode()
        if (t.currencyCode == base || t.amountMinor == 0L)
            return t.copy(rateUsed = 1.0, baseAmountMinor = t.amountMinor)
        val rate = converter.rateAt(t.currencyCode, base, t.date)
        val from = db.currencyDao().byCode(t.currencyCode)
        val to = db.currencyDao().byCode(base)
        val converted = com.divan.app.core.money.Money.convert(
            t.amountMinor, from?.decimals ?: 0, to?.decimals ?: 0, rate
        )
        return t.copy(rateUsed = rate, baseAmountMinor = converted)
    }

    suspend fun create(t0: Txn, splits: List<TxnSplit> = emptyList()): Txn {
        val t = withDerived(t0.copy(id = t0.id.ifEmpty { UUID.randomUUID().toString() }))
        db.txnDao().insert(t)
        if (splits.isNotEmpty()) db.txnDao().insertSplits(splits.map { it.copy(txnId = t.id) })
        audit.write("txn", t.id, AuditAction.INSERT, "${t.type} ${t.amountMinor} ${t.currencyCode}")
        sync.enqueue("txn", t.id, AuditAction.INSERT, t)
        WidgetUpdater.requestUpdateAll()
        return t
    }

    suspend fun update(t: Txn, splits: List<TxnSplit>? = null) {
        val upd = withDerived(t.copy(updatedAt = System.currentTimeMillis(), version = t.version + 1))
        db.txnDao().update(upd)
        if (splits != null) {
            db.txnDao().clearSplits(upd.id)
            if (splits.isNotEmpty()) db.txnDao().insertSplits(splits.map { it.copy(txnId = upd.id) })
        }
        audit.write("txn", upd.id, AuditAction.UPDATE, "${upd.type} ${upd.amountMinor}")
        sync.enqueue("txn", upd.id, AuditAction.UPDATE, upd)
        WidgetUpdater.requestUpdateAll()
    }

    suspend fun delete(id: String) {
        db.txnDao().softDelete(id, System.currentTimeMillis())
        audit.write("txn", id, AuditAction.DELETE, "")
        sync.enqueue("txn", id, AuditAction.DELETE, null)
        WidgetUpdater.requestUpdateAll()
    }

    suspend fun confirm(id: String) {
        db.txnDao().setStatus(id, TxnStatus.CONFIRMED.name, System.currentTimeMillis())
        audit.write("txn", id, AuditAction.CONFIRM, "")
        sync.enqueue("txn", id, AuditAction.CONFIRM, null)
        WidgetUpdater.requestUpdateAll()
    }

    fun recent(limit: Int = 50) = db.txnDao().recent(limit)
    suspend fun recentWithCategory(limit: Int = 8): List<Txn> = db.txnDao().recentWithCategoryOnce(limit)
    fun all() = db.txnDao().all()
    fun between(from: Long, to: Long) = db.txnDao().between(from, to)
    fun search(q: String) = db.txnDao().search(q)
    fun byIdFlow(id: String) = db.txnDao().byIdFlow(id)
    suspend fun byId(id: String): Txn? = db.txnDao().byId(id)
    suspend fun splitsOf(txnId: String): List<TxnSplit> = db.txnDao().splits(txnId)
    suspend fun count(): Int = db.txnDao().count()
    fun pendingSms() = db.txnDao().pendingSms()

    /** Totals per period converted into base currency (for reports/dashboard). */
    suspend fun totalsBetween(from: Long, to: Long, memberId: String? = null): Totals {
        val txns = db.txnDao().betweenOnce(from, to)
            .filter { it.status != TxnStatus.DRAFT && it.status != TxnStatus.RETURNED && !it.isDeleted }
            .let { list -> if (memberId == null) list else list.filter { t -> t.memberIds?.contains(memberId) == true } }
        var income = 0L; var expense = 0L
        val base = baseCode()
        txns.forEach { t ->
            val baseMinor = if (t.currencyCode == base) t.amountMinor
            else converter.toBase(t.amountMinor, t.currencyCode, base, t.date)
            when (t.type) {
                com.divan.app.core.db.entity.TxnType.INCOME,
                com.divan.app.core.db.entity.TxnType.LOAN_DISBURSE,
                com.divan.app.core.db.entity.TxnType.DEBT_COLLECT_IN,
                com.divan.app.core.db.entity.TxnType.ASSET_SELL,
                com.divan.app.core.db.entity.TxnType.SALE -> income += baseMinor
                com.divan.app.core.db.entity.TxnType.EXPENSE,
                com.divan.app.core.db.entity.TxnType.PURCHASE,
                com.divan.app.core.db.entity.TxnType.FEE -> expense += baseMinor
                else -> {}
            }
        }
        return Totals(income, expense, income - expense, txns.size)
    }

    data class Totals(val income: Long, val expense: Long, val net: Long, val count: Int)

    /** Per-category expense sums (base currency) inside period. */
    suspend fun categorySums(from: Long, to: Long, kind: com.divan.app.core.db.entity.TxnType = com.divan.app.core.db.entity.TxnType.EXPENSE): Map<String, Long> {
        val base = baseCode()
        val map = HashMap<String, Long>()
        db.txnDao().betweenOnce(from, to)
            .filter { it.status != TxnStatus.DRAFT && !it.isDeleted && it.type == kind && it.categoryId != null }
            .forEach { t ->
                val baseMinor = if (t.currencyCode == base) t.amountMinor
                else converter.toBase(t.amountMinor, t.currencyCode, base, t.date)
                map[t.categoryId!!] = (map[t.categoryId!!] ?: 0L) + baseMinor
            }
        return map
    }
}
