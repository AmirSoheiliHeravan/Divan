package com.divan.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ProductStatus { ACTIVE, CONSUMED, SOLD, DAMAGED, EXPIRED, ARCHIVED }

@Entity(tableName = "products", indices = [Index("barcode"), Index("categoryId")])
data class Product(
    @PrimaryKey val id: String,
    val name: String,
    val altName: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val categoryId: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val unit: String = "عدد",             // base unit label
    val packFactor: Double = 1.0,          // e.g. 1 pack = 6 base units
    val packLabel: String? = null,         // e.g. "بسته"
    val sizeInfo: String? = null,
    val colorInfo: String? = null,
    val producedDate: Long? = null,
    val expiryDate: Long? = null,
    val serialOrBatch: String? = null,
    val imagePath: String? = null,
    val location: String? = null,
    val supplierId: String? = null,
    val buyPriceMinor: Long = 0,
    val buyPriceDate: Long? = null,
    val salePriceMinor: Long = 0,
    val salePriceDate: Long? = null,
    val avgCostMinor: Long = 0,
    val stockQty: Double = 0.0,
    val minQty: Double = 0.0,
    val status: ProductStatus = ProductStatus.ACTIVE,
    val warrantyUntil: Long? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

enum class InvoiceKind { PURCHASE, SALE }
enum class InvoicePayMode { CASH, PARTIAL, CREDIT }
enum class InvoiceStatus { DRAFT, CONFIRMED, RETURNED }

@Entity(tableName = "invoices", indices = [Index("kind"), Index("date")])
data class Invoice(
    @PrimaryKey val id: String,
    val kind: InvoiceKind = InvoiceKind.PURCHASE,
    val counterpartyId: String? = null,
    val personName: String? = null,
    val date: Long,
    val subtotalMinor: Long = 0,
    val discountMinor: Long = 0,
    val taxMinor: Long = 0,
    val shippingMinor: Long = 0,
    val totalMinor: Long = 0,
    val currencyCode: String = "IRR",
    val accountId: String? = null,
    val payMode: InvoicePayMode = InvoicePayMode.CASH,
    val paidMinor: Long = 0,
    val asInventory: Boolean = true,      // false = treat as immediate expense/income
    val status: InvoiceStatus = InvoiceStatus.CONFIRMED,
    val txnId: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

@Entity(tableName = "invoice_items", indices = [Index("invoiceId"), Index("productId")])
data class InvoiceItem(
    @PrimaryKey val id: String,
    val invoiceId: String,
    val productId: String? = null,
    val itemName: String,
    val qty: Double,
    val unit: String,
    val unitPriceMinor: Long,
    val discountMinor: Long = 0,
    val totalMinor: Long,
    val avgCostMinor: Long = 0,
)

enum class MoveKind { RECEIVE, ISSUE, ADJUST, DAMAGED, EXPIRED, RETURN_SALE, RETURN_PURCHASE }

@Entity(tableName = "inventory_moves", indices = [Index("productId"), Index("date")])
data class InventoryMove(
    @PrimaryKey val id: String,
    val productId: String,
    val kind: MoveKind = MoveKind.RECEIVE,
    val qty: Double,                       // signed quantity in base units
    val date: Long,
    val reason: String? = null,
    val invoiceId: String? = null,
    val unitCostMinor: Long? = null,
    val createdBy: String = "local",
)

enum class AssetStatus { OWNED, SOLD, DISCARDED }

@Entity(tableName = "assets")
data class AssetItem(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "",
    val ownerMemberId: String? = null,
    val buyDate: Long,
    val buyPriceMinor: Long = 0,
    val extraCostMinor: Long = 0,
    val currencyCode: String = "IRR",
    val estValueMinor: Long? = null,
    val estValueDate: Long? = null,
    val location: String? = null,
    val serialOrDeed: String? = null,
    val imagePath: String? = null,
    val ownershipSharePercent: Double = 100.0,
    val loanId: String? = null,
    val insuranceCompany: String? = null,
    val insuranceUntil: Long? = null,
    val status: AssetStatus = AssetStatus.OWNED,
    val soldDate: Long? = null,
    val soldPriceMinor: Long? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

@Entity(tableName = "sms_rules")
data class SmsRule(
    @PrimaryKey val id: String,
    val bankName: String,
    val sender: String,                    // e.g. 9870005151 or +49170..
    val pattern: String,                   // regex with named groups: amount, card, account, balance
    val depositKeywords: String,           // "|" separated
    val withdrawKeywords: String,
    val balanceKeywords: String = "",
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val sample: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class SmsItemStatus { UNPARSED, PARSED, IGNORED, DUPLICATE }

@Entity(tableName = "sms_inbox", indices = [Index("date")])
data class SmsInboxItem(
    @PrimaryKey val id: String,
    val sender: String,
    val body: String,
    val date: Long,
    val ruleId: String? = null,
    val status: SmsItemStatus = SmsItemStatus.UNPARSED,
    val txnId: String? = null,
    val createdAt: Long,
)

enum class AuditAction { INSERT, UPDATE, DELETE, CONFIRM, ARCHIVE, RESTORE, SETTINGS }

@Entity(tableName = "audit_log", indices = [Index("ts")])
data class AuditLog(
    @PrimaryKey val id: String,
    val ts: Long,
    val actor: String = "local",
    val entityType: String,
    val entityId: String,
    val action: AuditAction = AuditAction.UPDATE,
    val summary: String,
)

enum class SyncStatus { PENDING, SENT, FAILED, DONE, SERVER_UNSET }

@Entity(tableName = "sync_ops", indices = [Index("status")])
data class SyncOp(
    @PrimaryKey val id: String,            // idempotency key
    val entityType: String,
    val entityId: String,
    val op: String,                        // INSERT | UPDATE | DELETE
    val payload: String,                   // JSON snapshot
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
    val status: SyncStatus = SyncStatus.PENDING,
)
