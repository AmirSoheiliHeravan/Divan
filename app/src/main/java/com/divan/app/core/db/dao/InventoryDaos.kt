package com.divan.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.divan.app.core.db.entity.AssetItem
import com.divan.app.core.db.entity.AuditAction
import com.divan.app.core.db.entity.AuditLog
import com.divan.app.core.db.entity.Invoice
import com.divan.app.core.db.entity.InvoiceItem
import com.divan.app.core.db.entity.InventoryMove
import com.divan.app.core.db.entity.Product
import com.divan.app.core.db.entity.SmsInboxItem
import com.divan.app.core.db.entity.SmsRule
import com.divan.app.core.db.entity.SyncOp
import com.divan.app.core.db.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(p: Product)

    @Update
    suspend fun update(p: Product)

    @Query("SELECT * FROM products WHERE isDeleted = 0 ORDER BY name")
    fun all(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isDeleted = 0 ORDER BY name")
    suspend fun allOnce(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun byId(id: String): Product?

    @Query("SELECT * FROM products WHERE id = :id")
    fun byIdFlow(id: String): Flow<Product?>

    @Query("SELECT * FROM products WHERE barcode = :barcode AND isDeleted = 0 LIMIT 1")
    suspend fun byBarcode(barcode: String): Product?

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND stockQty <= minQty")
    fun lowStock(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isDeleted = 0 AND expiryDate IS NOT NULL AND expiryDate <= :until ORDER BY expiryDate")
    fun expiringBefore(until: Long): Flow<List<Product>>

    @Query("UPDATE products SET isDeleted = 1, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoves(list: List<InventoryMove>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMove(move: InventoryMove)

    @Query("SELECT * FROM inventory_moves WHERE productId = :productId ORDER BY date DESC")
    fun moves(productId: String): Flow<List<InventoryMove>>

    @Query("SELECT * FROM invoices WHERE isDeleted = 0 ORDER BY date DESC")
    fun invoices(): Flow<List<Invoice>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: Invoice)

    @Update
    suspend fun updateInvoice(invoice: Invoice)

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun invoiceById(id: String): Invoice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(list: List<InvoiceItem>)

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    suspend fun itemsOnce(invoiceId: String): List<InvoiceItem>

    @Query("SELECT * FROM invoice_items WHERE invoiceId = :invoiceId")
    fun items(invoiceId: String): Flow<List<InvoiceItem>>

    @Query("UPDATE invoices SET isDeleted = 1, updatedAt = :ts WHERE id = :id")
    suspend fun softDeleteInvoice(id: String, ts: Long)
}

@Dao
interface AssetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(a: AssetItem)

    @Update
    suspend fun update(a: AssetItem)

    @Query("SELECT * FROM assets WHERE isDeleted = 0 ORDER BY buyDate DESC")
    fun all(): Flow<List<AssetItem>>

    @Query("SELECT * FROM assets ORDER BY buyDate DESC")
    suspend fun allOnce(): List<AssetItem>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun byId(id: String): AssetItem?

    @Query("UPDATE assets SET isDeleted = 1, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)
}

@Dao
interface SmsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<SmsRule>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: SmsRule)

    @Update
    suspend fun updateRule(rule: SmsRule)

    @Query("SELECT * FROM sms_rules ORDER BY bankName")
    fun rules(): Flow<List<SmsRule>>

    @Query("SELECT * FROM sms_rules WHERE enabled = 1")
    suspend fun enabledRulesOnce(): List<SmsRule>

    @Query("SELECT * FROM sms_rules WHERE sender = :sender LIMIT 1")
    suspend fun ruleBySender(sender: String): SmsRule?

    @Query("SELECT * FROM sms_rules WHERE id = :id")
    suspend fun ruleById(id: String): SmsRule?

    @Query("UPDATE sms_rules SET enabled = :enabled, updatedAt = :ts WHERE id = :id")
    suspend fun setRuleEnabled(id: String, enabled: Boolean, ts: Long)

    @Query("DELETE FROM sms_rules WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomRule(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInbox(item: SmsInboxItem)

    @Update
    suspend fun updateInbox(item: SmsInboxItem)

    @Query("SELECT * FROM sms_inbox ORDER BY date DESC LIMIT 200")
    fun inbox(): Flow<List<SmsInboxItem>>

    @Query("SELECT * FROM sms_inbox WHERE id = :id")
    suspend fun inboxById(id: String): SmsInboxItem?

    @Query("UPDATE sms_inbox SET status = :status, txnId = :txnId, ruleId = :ruleId WHERE id = :id")
    suspend fun setInboxResult(id: String, status: String, txnId: String?, ruleId: String?)
}

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun log(entry: AuditLog)

    @Query("SELECT * FROM audit_log ORDER BY ts DESC LIMIT 500")
    fun recent(): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_log WHERE entityType = :type AND entityId = :id ORDER BY ts DESC")
    fun forEntity(type: String, id: String): Flow<List<AuditLog>>
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(op: SyncOp)

    @Query("SELECT * FROM sync_ops WHERE status = 'PENDING' OR status = 'FAILED' ORDER BY createdAt LIMIT :batch")
    suspend fun pendingBatch(batch: Int): List<SyncOp>

    @Query("SELECT * FROM sync_ops ORDER BY createdAt DESC LIMIT 100")
    fun recent(): Flow<List<SyncOp>>

    @Query("SELECT COUNT(*) FROM sync_ops WHERE status IN ('PENDING','FAILED')")
    fun pendingCount(): Flow<Int>

    @Update
    suspend fun update(op: SyncOp)

    @Query("DELETE FROM sync_ops WHERE status = 'DONE'")
    suspend fun clearDone()

    @Query("SELECT * FROM sync_ops WHERE id = :id")
    suspend fun byId(id: String): SyncOp?
}

/** Helper: audit trail writer */
class AuditWriter(private val dao: AuditDao) {
    suspend fun write(entityType: String, entityId: String, action: AuditAction, summary: String) {
        dao.log(
            AuditLog(
                id = java.util.UUID.randomUUID().toString(),
                ts = System.currentTimeMillis(),
                entityType = entityType,
                entityId = entityId,
                action = action,
                summary = summary,
            )
        )
    }
}
