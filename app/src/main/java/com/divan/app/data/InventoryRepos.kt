package com.divan.app.data

import com.divan.app.core.db.AppDatabase
import com.divan.app.core.db.dao.AuditWriter
import com.divan.app.core.db.entity.AuditAction
import com.divan.app.core.db.entity.AssetItem
import com.divan.app.core.db.entity.AssetStatus
import com.divan.app.core.db.entity.DebtCredit
import com.divan.app.core.db.entity.DebtDirection
import com.divan.app.core.db.entity.DebtStatus
import com.divan.app.core.db.entity.InventoryMove
import com.divan.app.core.db.entity.Invoice
import com.divan.app.core.db.entity.InvoiceItem
import com.divan.app.core.db.entity.InvoiceKind
import com.divan.app.core.db.entity.InvoicePayMode
import com.divan.app.core.db.entity.MoveKind
import com.divan.app.core.db.entity.PayMethod
import com.divan.app.core.db.entity.Product
import com.divan.app.core.db.entity.Txn
import com.divan.app.core.db.entity.TxnStatus
import com.divan.app.core.db.entity.TxnType
import com.divan.app.core.sync.SyncQueue
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ProductRepo(
    private val db: AppDatabase,
    private val sync: SyncQueue,
    private val settings: SettingsRepo,
) {
    private val audit = AuditWriter(db.auditDao())

    fun all(): Flow<List<Product>> = db.productDao().all()
    fun invoices(): Flow<List<Invoice>> = db.productDao().invoices()
    fun byIdFlow(id: String) = db.productDao().byIdFlow(id)
    suspend fun byId(id: String): Product? = db.productDao().byId(id)
    suspend fun byBarcode(b: String): Product? = db.productDao().byBarcode(b)
    fun lowStock(): Flow<List<Product>> = db.productDao().lowStock()
    fun expiringBefore(until: Long): Flow<List<Product>> = db.productDao().expiringBefore(until)
    fun moves(productId: String): Flow<List<InventoryMove>> = db.productDao().moves(productId)

    suspend fun save(p: Product, isNew: Boolean) {
        val ts = System.currentTimeMillis()
        val rec = p.copy(id = p.id.ifEmpty { UUID.randomUUID().toString() }, createdAt = if (isNew) ts else p.createdAt, updatedAt = ts)
        db.productDao().insert(rec)
        audit.write("product", rec.id, if (isNew) AuditAction.INSERT else AuditAction.UPDATE, rec.name)
        sync.enqueue("product", rec.id, if (isNew) AuditAction.INSERT else AuditAction.UPDATE, rec)
    }

    suspend fun delete(id: String) {
        db.productDao().softDelete(id, System.currentTimeMillis())
        sync.enqueue("product", id, AuditAction.DELETE, null)
    }

    /** Manual stock correction with reason (always leaves a trail). */
    suspend fun adjustStock(productId: String, kind: MoveKind, qty: Double, reason: String?, unitCost: Long? = null) {
        val p = db.productDao().byId(productId) ?: return
        val delta = when (kind) {
            MoveKind.RECEIVE, MoveKind.RETURN_PURCHASE, MoveKind.RETURN_SALE -> qty
            MoveKind.ISSUE, MoveKind.DAMAGED, MoveKind.EXPIRED -> -qty
            MoveKind.ADJUST -> qty
        }
        val newStock = p.stockQty + delta
        var newCost = p.avgCostMinor
        if (unitCost != null && delta > 0) {
            val totalValue = p.avgCostMinor * p.stockQty + unitCost * delta
            newStock.takeIf { it > 0 }?.let { newCost = (totalValue / it).toLong() }
        }
        db.productDao().insert(
            p.copy(stockQty = newStock, avgCostMinor = newCost, updatedAt = System.currentTimeMillis())
        )
        db.productDao().insertMove(
            InventoryMove(
                id = UUID.randomUUID().toString(), productId = productId, kind = kind,
                qty = delta, date = System.currentTimeMillis(), reason = reason,
                unitCostMinor = unitCost,
            )
        )
        audit.write("product", productId, AuditAction.UPDATE, "$kind $delta")
    }
}

class InvoiceRepo(
    private val db: AppDatabase,
    private val sync: SyncQueue,
    private val settings: SettingsRepo,
) {
    private val audit = AuditWriter(db.auditDao())

    fun all(): Flow<List<Invoice>> = db.productDao().invoices()
    fun items(invoiceId: String) = db.productDao().items(invoiceId)
    suspend fun byId(id: String): Invoice? = db.productDao().invoiceById(id)
    suspend fun itemsOnce(id: String): List<InvoiceItem> = db.productDao().itemsOnce(id)

    /** Create invoice: totals, stock moves, linked transaction (paid amount),
     *  and automatic debt for the unpaid remainder. */
    suspend fun create(invoice: Invoice, items: List<InvoiceItem>): Invoice {
        val ts = System.currentTimeMillis()
        val invId = invoice.id.ifEmpty { UUID.randomUUID().toString() }
        val subtotal = items.sumOf { it.qty * it.unitPriceMinor }.toLong()
        val itemsDiscount = items.sumOf { it.discountMinor }
        val total = subtotal - invoice.discountMinor - itemsDiscount + invoice.taxMinor + invoice.shippingMinor
        val inv = invoice.copy(
            id = invId, createdAt = if (invoice.createdAt == 0L) ts else invoice.createdAt,
            updatedAt = ts, subtotalMinor = subtotal,
            discountMinor = invoice.discountMinor + itemsDiscount,
            totalMinor = total, status = InvoiceStatusOf(invoice),
        )
        db.productDao().insertInvoice(inv)
        db.productDao().insertItems(items.map { it.copy(invoiceId = invId) })

        val productRepo = ProductRepo(db, sync, settings)
        if (inv.asInventory) {
            items.forEach { item ->
                if (item.productId != null) {
                    val kind = if (inv.kind == InvoiceKind.PURCHASE) MoveKind.RECEIVE else MoveKind.ISSUE
                    productRepo.adjustStock(
                        item.productId!!, kind, item.qty,
                        reason = "فاکتور ${inv.kind}", unitCost = if (inv.kind == InvoiceKind.PURCHASE) item.unitPriceMinor else null
                    )
                }
            }
        }

        // Cash movement = paid amount only; remainder becomes a debt record.
        val creator = TxnCreator(db, sync, settings)
        if (inv.paidMinor > 0 && inv.accountId != null) {
            val txn = creator.create(
                Txn(
                    type = if (inv.kind == InvoiceKind.PURCHASE) TxnType.PURCHASE else TxnType.SALE,
                    amountMinor = inv.paidMinor, currencyCode = inv.currencyCode,
                    accountId = inv.accountId, method = if (inv.payMode == InvoicePayMode.CREDIT) PayMethod.CREDIT else PayMethod.CASH,
                    title = if (inv.kind == InvoiceKind.PURCHASE) "خرید" else "فروش",
                    note = inv.personName, date = inv.date, refId = invId,
                    status = TxnStatus.CONFIRMED, createdAt = ts, updatedAt = ts,
                )
            )
            db.productDao().updateInvoice(inv.copy(txnId = txn.id))
        }
        val remaining = total - inv.paidMinor
        if (remaining > 0) {
            val debtRepo = DebtRepo(db, sync, settings)
            debtRepo.create(
                DebtCredit(
                    direction = if (inv.kind == InvoiceKind.PURCHASE) DebtDirection.I_OWE else DebtDirection.THEY_OWE,
                    counterpartyId = inv.counterpartyId,
                    personName = inv.personName ?: (if (inv.kind == InvoiceKind.PURCHASE) "فروشنده" else "خریدار"),
                    amountMinor = remaining, currencyCode = inv.currencyCode,
                    startDate = inv.date, note = "بدهی فاکتور", status = DebtStatus.OPEN,
                    createdAt = ts, updatedAt = ts,
                )
            )
        }
        audit.write("invoice", invId, AuditAction.INSERT, "total=$total")
        sync.enqueue("invoice", invId, AuditAction.INSERT, inv)
        return inv
    }

    private fun InvoiceStatusOf(i: Invoice) = i.status

    suspend fun delete(id: String) {
        db.productDao().softDeleteInvoice(id, System.currentTimeMillis())
        sync.enqueue("invoice", id, AuditAction.DELETE, null)
    }
}

class AssetRepo(
    private val db: AppDatabase,
    private val sync: SyncQueue,
    private val settings: SettingsRepo,
) {
    private val audit = AuditWriter(db.auditDao())

    fun all(): Flow<List<AssetItem>> = db.assetDao().all()
    suspend fun byId(id: String): AssetItem? = db.assetDao().byId(id)

    suspend fun save(a: AssetItem, isNew: Boolean, buyAccountId: String?) {
        val ts = System.currentTimeMillis()
        val rec = a.copy(id = a.id.ifEmpty { UUID.randomUUID().toString() }, createdAt = if (isNew) ts else a.createdAt, updatedAt = ts)
        db.assetDao().insert(rec)
        audit.write("asset", rec.id, if (isNew) AuditAction.INSERT else AuditAction.UPDATE, rec.name)
        sync.enqueue("asset", rec.id, if (isNew) AuditAction.INSERT else AuditAction.UPDATE, rec)
        if (isNew && buyAccountId != null && rec.buyPriceMinor > 0) {
            TxnCreator(db, sync, settings).create(
                Txn(
                    type = TxnType.ASSET_BUY, amountMinor = rec.buyPriceMinor + rec.extraCostMinor,
                    currencyCode = rec.currencyCode, accountId = buyAccountId,
                    title = "خرید دارایی: ${rec.name}", date = rec.buyDate,
                    refId = rec.id, status = TxnStatus.CONFIRMED, createdAt = ts, updatedAt = ts,
                )
            )
        }
    }

    suspend fun delete(id: String) {
        db.assetDao().softDelete(id, System.currentTimeMillis())
        sync.enqueue("asset", id, AuditAction.DELETE, null)
    }

    /** Sell an asset: marks sold + income transaction + profit figure. */
    suspend fun sell(id: String, priceMinor: Long, accountId: String, date: Long) {
        val a = db.assetDao().byId(id) ?: return
        db.assetDao().insert(
            a.copy(status = AssetStatus.SOLD, soldDate = date, soldPriceMinor = priceMinor, updatedAt = System.currentTimeMillis())
        )
        TxnCreator(db, sync, settings).create(
            Txn(
                type = TxnType.ASSET_SELL, amountMinor = priceMinor, currencyCode = a.currencyCode,
                accountId = accountId, title = "فروش دارایی: ${a.name}", date = date,
                refId = id, status = TxnStatus.CONFIRMED, createdAt = date, updatedAt = date,
            )
        )
        audit.write("asset", id, AuditAction.UPDATE, "sold=$priceMinor")
    }

    suspend fun portfolioValue(): Long {
        val base = settings.baseCurrencyNow()
        val conv = com.divan.app.core.money.CurrencyConverter(db)
        return db.assetDao().all().let { 0L } // computed in VM from flows
    }
}

class SmsRepo(private val db: AppDatabase) {
    fun rules() = db.smsDao().rules()
    fun inbox() = db.smsDao().inbox()
    suspend fun ruleBySender(sender: String) = db.smsDao().ruleBySender(sender)
    suspend fun ruleById(id: String) = db.smsDao().ruleById(id)
    suspend fun saveRule(rule: com.divan.app.core.db.entity.SmsRule) = db.smsDao().insertRule(rule)
    suspend fun updateRule(rule: com.divan.app.core.db.entity.SmsRule) = db.smsDao().updateRule(rule)
    suspend fun setEnabled(id: String, enabled: Boolean) =
        db.smsDao().setRuleEnabled(id, enabled, System.currentTimeMillis())
    suspend fun deleteCustom(id: String) = db.smsDao().deleteCustomRule(id)
    suspend fun setInboxResult(id: String, status: String, txnId: String?, ruleId: String?) =
        db.smsDao().setInboxResult(id, status, txnId, ruleId)
}

class SystemRepo(private val db: AppDatabase, private val sync: SyncQueue) {
    fun auditRecent() = db.auditDao().recent()
    fun auditFor(type: String, id: String) = db.auditDao().forEntity(type, id)
    fun syncRecent() = db.syncDao().recent()
    fun syncPendingCount() = db.syncDao().pendingCount()
    suspend fun clearDoneOps() = db.syncDao().clearDone()
}
