package com.divan.app.data

import com.divan.app.core.db.AppDatabase
import com.divan.app.core.db.dao.AuditWriter
import com.divan.app.core.db.entity.Account
import com.divan.app.core.db.entity.AccountStatus
import com.divan.app.core.db.entity.Category
import com.divan.app.core.db.entity.CategoryKind
import com.divan.app.core.db.entity.Counterparty
import com.divan.app.core.db.entity.CurrencyUnit
import com.divan.app.core.db.entity.ExchangeRate
import com.divan.app.core.db.entity.FamilySpace
import com.divan.app.core.db.entity.Member
import com.divan.app.core.db.entity.MemberRole
import com.divan.app.core.db.entity.TxnType
import com.divan.app.core.money.Money
import com.divan.app.core.sync.SyncQueue
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class FamilyRepo(private val db: AppDatabase) {
    fun space(): Flow<FamilySpace?> = db.familyDao().first()
    suspend fun ensure(name: String) {
        if (db.familyDao().countAny() == 0)
            db.familyDao().insert(FamilySpace("family", name, System.currentTimeMillis()))
    }
    fun members(): Flow<List<Member>> = db.memberDao().all()
    suspend fun addMember(name: String, role: MemberRole, color: String): Member {
        val m = Member(UUID.randomUUID().toString(), name, role, color,
            createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        db.memberDao().insert(m)
        return m
    }
    suspend fun updateMember(m: Member) = db.memberDao().update(m.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteMember(id: String) = db.memberDao().softDelete(id, System.currentTimeMillis())
    suspend fun memberById(id: String): Member? = db.memberDao().byId(id)
}

class AccountRepo(
    private val db: AppDatabase,
    private val sync: SyncQueue,
    private val settings: SettingsRepo,
) {
    private val audit = AuditWriter(db.auditDao())

    fun all(): Flow<List<Account>> = db.accountDao().all()
    fun allActive(): Flow<List<Account>> = db.accountDao().allActive()
    suspend fun byId(id: String): Account? = db.accountDao().byId(id)
    fun byIdFlow(id: String): Flow<Account?> = db.accountDao().byIdFlow(id)

    suspend fun save(a: Account, isNew: Boolean) {
        val ts = System.currentTimeMillis()
        val rec = a.copy(updatedAt = ts, version = a.version + if (isNew) 0 else 1)
        db.accountDao().insert(rec)
        audit.write("account", rec.id, if (isNew) com.divan.app.core.db.entity.AuditAction.INSERT else com.divan.app.core.db.entity.AuditAction.UPDATE,
            "${rec.name} | ${rec.currencyCode}")
        sync.enqueue("account", rec.id, if (isNew) com.divan.app.core.db.entity.AuditAction.INSERT else com.divan.app.core.db.entity.AuditAction.UPDATE, rec)
    }

    suspend fun delete(id: String) {
        val ts = System.currentTimeMillis()
        db.accountDao().softDelete(id, ts)
        audit.write("account", id, com.divan.app.core.db.entity.AuditAction.DELETE, "")
        sync.enqueue("account", id, com.divan.app.core.db.entity.AuditAction.DELETE, null)
    }

    /** Re-calculated balance = opening balance + confirmed movements.
     *  Transfers in (+) and out (-) are handled without double counting. */
    suspend fun balanceOf(accountId: String): Long {
        val acc = db.accountDao().byId(accountId) ?: return 0
        val txns = db.txnDao().betweenOnce(Long.MIN_VALUE, Long.MAX_VALUE)
            .filter { it.status != com.divan.app.core.db.entity.TxnStatus.DRAFT &&
                      it.status != com.divan.app.core.db.entity.TxnStatus.RETURNED }
        var bal = acc.openingBalanceMinor
        txns.forEach { t ->
            if (t.accountId == accountId) bal += Money.signedDelta(t.type, t.amountMinor)
            else if (t.toAccountId == accountId && t.type == TxnType.TRANSFER) bal += t.amountMinor
        }
        return bal
    }

    suspend fun count(): Int = db.accountDao().count()
}

class CategoryRepo(private val db: AppDatabase) {
    fun allActive(): Flow<List<Category>> = db.categoryDao().allActive()
    fun all(): Flow<List<Category>> = db.categoryDao().all()
    suspend fun byId(id: String): Category? = db.categoryDao().byId(id)
    suspend fun roots(): List<Category> = db.categoryDao().rootsOnce()
    suspend fun children(parentId: String): List<Category> = db.categoryDao().childrenOnce(parentId)

    suspend fun save(c: Category, isNew: Boolean) {
        val ts = System.currentTimeMillis()
        db.categoryDao().insert(c.copy(updatedAt = ts))
    }

    suspend fun setActive(id: String, active: Boolean) =
        db.categoryDao().setActive(id, active, System.currentTimeMillis())

    suspend fun delete(id: String) = db.categoryDao().softDelete(id, System.currentTimeMillis())

    /** Flattened tree [root, child...] for pickers */
    suspend fun flatTree(kind: CategoryKind? = null): List<Pair<Category, Int>> {
        val out = mutableListOf<Pair<Category, Int>>()
        roots().filter { kind == null || it.kind == kind || it.kind == CategoryKind.BOTH }.forEach { root ->
            out += root to 0
            children(root.id).forEach { out += it to 1 }
        }
        return out
    }

    suspend fun nameOf(id: String?, localeFa: Boolean): String? {
        if (id == null) return null
        val c = db.categoryDao().byId(id) ?: return null
        return if (localeFa) c.name else c.nameEn
    }
}

class CurrencyRepo(
    private val db: AppDatabase,
    private val settings: SettingsRepo,
) {
    fun all(): Flow<List<CurrencyUnit>> = db.currencyDao().all()
    val base: Flow<CurrencyUnit?> = db.currencyDao().base()
    suspend fun allOnce(): List<CurrencyUnit> = db.currencyDao().allOnce()
    suspend fun byCode(code: String): CurrencyUnit? = db.currencyDao().byCode(code)
    suspend fun baseOnce(): CurrencyUnit = db.currencyDao().baseOnce()
        ?: CurrencyUnit("IRR", "Iranian Rial", "ریال", "﷼", 0, isBase = true)

    suspend fun save(c: CurrencyUnit, isNew: Boolean) = db.currencyDao().insert(c)

    suspend fun setBase(code: String) {
        db.currencyDao().clearBase()
        db.currencyDao().setBase(code)
        settings.setBaseCurrency(code)
    }

    fun rates(): Flow<List<ExchangeRate>> = db.rateDao().all()
    fun ratesFor(code: String): Flow<List<ExchangeRate>> = db.rateDao().forCurrency(code)

    suspend fun addRate(code: String, rate: Double, date: Long, source: String = "manual") {
        db.rateDao().insert(
            ExchangeRate(
                id = UUID.randomUUID().toString(), currencyCode = code,
                rateToBase = rate, date = date, source = source,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteRate(id: String) = db.rateDao().delete(id)

    suspend fun latestRate(code: String): Double {
        val base = baseOnce()
        if (code == base.code) return 1.0
        return db.rateDao().latest(code)?.rateToBase ?: 0.0
    }
}

class CounterpartyRepo(
    private val db: AppDatabase,
    private val sync: SyncQueue,
) {
    fun all(): Flow<List<Counterparty>> = db.counterpartyDao().all()
    suspend fun byId(id: String): Counterparty? = db.counterpartyDao().byId(id)
    suspend fun allOnce(): List<Counterparty> = db.counterpartyDao().allOnce()
    suspend fun count(): Int = db.counterpartyDao().count()

    suspend fun save(c: Counterparty, isNew: Boolean) {
        val ts = System.currentTimeMillis()
        val rec = c.copy(updatedAt = ts)
        db.counterpartyDao().insert(rec)
        sync.enqueue("counterparty", rec.id,
            if (isNew) com.divan.app.core.db.entity.AuditAction.INSERT else com.divan.app.core.db.entity.AuditAction.UPDATE, rec)
    }

    suspend fun delete(id: String) {
        db.counterpartyDao().softDelete(id, System.currentTimeMillis())
        sync.enqueue("counterparty", id, com.divan.app.core.db.entity.AuditAction.DELETE, null)
    }
}
