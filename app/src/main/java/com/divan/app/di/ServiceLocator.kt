package com.divan.app.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.divan.app.core.backup.BackupManager
import com.divan.app.core.calendar.CalendarSyncManager
import com.divan.app.core.db.AppDatabase
import com.divan.app.core.sms.SmsParser
import com.divan.app.core.sync.SyncQueue
import com.divan.app.data.AccountRepo
import com.divan.app.data.AssetRepo
import com.divan.app.data.CheckRepo
import com.divan.app.data.CounterpartyRepo
import com.divan.app.data.CurrencyRepo
import com.divan.app.data.DebtRepo
import com.divan.app.data.FamilyRepo
import com.divan.app.data.LoanRepo
import com.divan.app.data.ProductRepo
import com.divan.app.data.RecurringRepo
import com.divan.app.data.SettingsRepo
import com.divan.app.data.SmsRepo
import com.divan.app.data.SystemRepo
import com.divan.app.data.TxnRepo
import com.divan.app.data.CategoryRepo
import com.divan.app.data.RateRepo

val Context.dataStore by preferencesDataStore(name = "divan_settings")

/** Simple service locator – keeps the project dependency-free and easy to follow. */
object ServiceLocator {
    private lateinit var appContext: Context

    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsRepo
        private set
    lateinit var syncQueue: SyncQueue
        private set
    lateinit var backup: BackupManager
        private set
    lateinit var calendarSync: CalendarSyncManager
        private set

    lateinit var family: FamilyRepo
        private set
    lateinit var accounts: AccountRepo
        private set
    lateinit var categories: CategoryRepo
        private set
    lateinit var currencies: CurrencyRepo
        private set
    lateinit var counterparties: CounterpartyRepo
        private set
    val itemsDao get() = db.itemsDao()
    lateinit var txns: TxnRepo
        private set
    lateinit var loans: LoanRepo
        private set
    lateinit var debts: DebtRepo
        private set
    lateinit var checks: CheckRepo
        private set
    lateinit var recurring: RecurringRepo
        private set
    lateinit var products: ProductRepo
        private set
    lateinit var assets: AssetRepo
        private set
    lateinit var sms: SmsRepo
        private set
    lateinit var system: SystemRepo
        private set
    lateinit var smsParser: SmsParser
        private set
    lateinit var rates: RateRepo
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        db = AppDatabase.build(appContext)
        settings = SettingsRepo(appContext.dataStore)
        syncQueue = SyncQueue(db.syncDao())
        backup = BackupManager(appContext, db, settings)
        calendarSync = CalendarSyncManager(appContext, db, settings)

        family = FamilyRepo(db)
        accounts = AccountRepo(db, syncQueue, settings)
        categories = CategoryRepo(db)
        currencies = CurrencyRepo(db, settings)
        counterparties = CounterpartyRepo(db, syncQueue)
        txns = TxnRepo(db, syncQueue, settings)
        loans = LoanRepo(db, syncQueue, settings)
        debts = DebtRepo(db, syncQueue, settings)
        checks = CheckRepo(db, syncQueue, settings)
        recurring = RecurringRepo(db, syncQueue, settings)
        products = ProductRepo(db, syncQueue, settings)
        assets = AssetRepo(db, syncQueue, settings)
        sms = SmsRepo(db)
        system = SystemRepo(db, syncQueue)
        smsParser = SmsParser(db, txns, accounts, sms, settings, counterparties)
        rates = RateRepo(appContext)
    }

    /** First-run seeding: family space, members, categories, currencies, bank SMS rules. */
    suspend fun seedIfNeeded() {
        com.divan.app.core.db.SeedData.seed(db)
    }

    fun context(): Context = appContext

    /** Bridge for invoice creation (repo kept inside data layer). */
    suspend fun createInvoice(inv: com.divan.app.core.db.entity.Invoice, items: List<com.divan.app.core.db.entity.InvoiceItem>) {
        com.divan.app.data.InvoiceRepo(db, syncQueue, settings).create(inv, items)
    }
}
