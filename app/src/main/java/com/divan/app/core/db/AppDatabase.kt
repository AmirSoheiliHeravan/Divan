package com.divan.app.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.divan.app.core.db.dao.AccountDao
import com.divan.app.core.db.dao.AssetDao
import com.divan.app.core.db.dao.AuditDao
import com.divan.app.core.db.dao.BudgetDao
import com.divan.app.core.db.dao.CategoryDao
import com.divan.app.core.db.dao.CheckDao
import com.divan.app.core.db.dao.CounterpartyDao
import com.divan.app.core.db.dao.CurrencyDao
import com.divan.app.core.db.dao.DebtDao
import com.divan.app.core.db.dao.FamilyDao
import com.divan.app.core.db.dao.ItemsDao
import com.divan.app.core.db.dao.LoanDao
import com.divan.app.core.db.dao.MemberDao
import com.divan.app.core.db.dao.ProductDao
import com.divan.app.core.db.dao.RateDao
import com.divan.app.core.db.dao.RecurringDao
import com.divan.app.core.db.dao.SavingsDao
import com.divan.app.core.db.dao.SmsDao
import com.divan.app.core.db.dao.SyncDao
import com.divan.app.core.db.dao.TxnDao
import com.divan.app.core.db.entity.Account
import com.divan.app.core.db.entity.AssetItem
import com.divan.app.core.db.entity.AuditLog
import com.divan.app.core.db.entity.Budget
import com.divan.app.core.db.entity.Category
import com.divan.app.core.db.entity.CheckItem
import com.divan.app.core.db.entity.Counterparty
import com.divan.app.core.db.entity.CurrencyUnit
import com.divan.app.core.db.entity.DebtCredit
import com.divan.app.core.db.entity.DebtPayment
import com.divan.app.core.db.entity.ExchangeRate
import com.divan.app.core.db.entity.FamilySpace
import com.divan.app.core.db.entity.Installment
import com.divan.app.core.db.entity.InventoryMove
import com.divan.app.core.db.entity.Invoice
import com.divan.app.core.db.entity.InvoiceItem
import com.divan.app.core.db.entity.ItemEntry
import com.divan.app.core.db.entity.ItemGroup
import com.divan.app.core.db.entity.Loan
import com.divan.app.core.db.entity.Member
import com.divan.app.core.db.entity.Product
import com.divan.app.core.db.entity.Recurring
import com.divan.app.core.db.entity.SavingsGoal
import com.divan.app.core.db.entity.SmsInboxItem
import com.divan.app.core.db.entity.SmsRule
import com.divan.app.core.db.entity.SyncOp
import com.divan.app.core.db.entity.Txn
import com.divan.app.core.db.entity.TxnSplit

@Database(
    entities = [
        FamilySpace::class, Member::class, Account::class, Category::class,
        CurrencyUnit::class, ExchangeRate::class, Counterparty::class,
        Txn::class, TxnSplit::class, Loan::class, Installment::class,
        DebtCredit::class, DebtPayment::class, CheckItem::class,
        Budget::class, SavingsGoal::class, Recurring::class,
        Product::class, Invoice::class, InvoiceItem::class, InventoryMove::class,
        ItemGroup::class, ItemEntry::class,
        AssetItem::class, SmsRule::class, SmsInboxItem::class,
        AuditLog::class, SyncOp::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun familyDao(): FamilyDao
    abstract fun memberDao(): MemberDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun rateDao(): RateDao
    abstract fun counterpartyDao(): CounterpartyDao
    abstract fun txnDao(): TxnDao
    abstract fun loanDao(): LoanDao
    abstract fun debtDao(): DebtDao
    abstract fun checkDao(): CheckDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsDao(): SavingsDao
    abstract fun recurringDao(): RecurringDao
    abstract fun productDao(): ProductDao
    abstract fun itemsDao(): ItemsDao
    abstract fun assetDao(): AssetDao
    abstract fun smsDao(): SmsDao
    abstract fun auditDao(): AuditDao
    abstract fun syncDao(): SyncDao

    companion object {
        /** v1 → v2: add transactions.feeMinor (کارمزد) */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN feeMinor INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3: اقلام تراکنش (itemsJson) + کاتالوگ اقلام (مجموعه‌ها/آیتم‌ها) با داده اولیه */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN itemsJson TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS item_groups (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, color TEXT NOT NULL, sort INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS item_entries (id TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, name TEXT NOT NULL, priceMinor INTEGER NOT NULL, isDeleted INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_item_entries_groupId ON item_entries(groupId)")
                // مجموعه‌های اولیه
                db.execSQL("INSERT INTO item_groups VALUES ('g1','خواربار','#245C49',0)")
                db.execSQL("INSERT INTO item_groups VALUES ('g2','قبوض','#D9B25F',1)")
                db.execSQL("INSERT INTO item_groups VALUES ('g3','حمل‌ونقل','#4A7FD4',2)")
                db.execSQL("INSERT INTO item_groups VALUES ('g4','خانه','#2E7D5B',3)")
                db.execSQL("INSERT INTO item_groups VALUES ('g5','سلامت','#E2574C',4)")
                db.execSQL("INSERT INTO item_groups VALUES ('g6','تفریح','#C97B4A',5)")
                // آیتم‌های اولیه (قیمت به ریال)
                db.execSQL("INSERT INTO item_entries VALUES ('i1','g1','برنج ۱۰ کیلویی',850000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i2','g1','مرغ',320000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i3','g1','روغن سرخ‌کردنی',280000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i4','g1','میوه فصل',450000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i5','g1','نان',60000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i6','g2','قبض برق',920000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i7','g2','قبض گاز',650000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i8','g2','قبض آب',210000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i9','g2','اینترنت',380000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i10','g3','بنزین',200000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i11','g3','اسنپ',85000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i12','g3','مترو',30000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i13','g4','اجاره',8000000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i14','g4','شارژ ساختمان',450000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i15','g5','دارو',350000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i16','g5','ویزیت پزشک',800000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i17','g6','رستوران',1200000,0)")
                db.execSQL("INSERT INTO item_entries VALUES ('i18','g6','سینما',250000,0)")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "divan.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
