package com.divan.app.data

import com.divan.app.core.db.AppDatabase
import com.divan.app.core.db.dao.AuditWriter
import com.divan.app.core.db.entity.AuditAction
import com.divan.app.core.db.entity.Budget
import com.divan.app.core.db.entity.CheckItem
import com.divan.app.core.db.entity.CheckStatus
import com.divan.app.core.db.entity.DebtCredit
import com.divan.app.core.db.entity.DebtDirection
import com.divan.app.core.db.entity.DebtPayment
import com.divan.app.core.db.entity.DebtStatus
import com.divan.app.core.db.entity.Installment
import com.divan.app.core.db.entity.InstallmentStatus
import com.divan.app.core.db.entity.Loan
import com.divan.app.core.db.entity.LoanPeriodUnit
import com.divan.app.core.db.entity.LoanRateMode
import com.divan.app.core.db.entity.PayMethod
import com.divan.app.core.db.entity.Recurring
import com.divan.app.core.db.entity.RecurUnit
import com.divan.app.core.db.entity.RecurStatus
import com.divan.app.core.db.entity.SavingsGoal
import com.divan.app.core.db.entity.Txn
import com.divan.app.core.db.entity.TxnStatus
import com.divan.app.core.db.entity.TxnType
import com.divan.app.core.sync.SyncQueue
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID

class LoanRepo(
    private val db: AppDatabase,
    private val sync: SyncQueue,
    private val settings: SettingsRepo,
) {
    private val audit = AuditWriter(db.auditDao())

    fun all(): Flow<List<Loan>> = db.loanDao().all()
    fun byIdFlow(id: String) = db.loanDao().byIdFlow(id)
    suspend fun byId(id: String): Loan? = db.loanDao().byId(id)
    fun installments(loanId: String) = db.loanDao().installments(loanId)
    suspend fun installmentsOnce(loanId: String) = db.loanDao().installmentsOnce(loanId)
    fun upcoming(limit: Int = 10) = db.loanDao().upcoming(limit)
    suspend fun unpaidOnce() = db.loanDao().unpaidOnce()

    /** Generate the installment plan according to rate mode. */
    fun buildSchedule(loan: Loan): List<Installment> {
        val n = loan.installmentsCount
        if (n <= 0) return emptyList()
        val p = loan.principalMinor
        val r = loan.ratePercent / 100.0
        val out = mutableListOf<Installment>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = loan.startDate

        when (loan.rateMode) {
            LoanRateMode.NONE -> {
                val each = p / n
                var rem = p
                for (i in 1..n) {
                    val principal = if (i == n) rem else each
                    rem -= each
                    out += mk(loan, i, addPeriod(loan, cal, i), principal, 0)
                }
            }
            LoanRateMode.FLAT -> {
                val totalInterest = (p * r).toLong()
                val eachPrincipal = p / n
                val eachInterest = totalInterest / n
                var remP = p; var remI = totalInterest
                for (i in 1..n) {
                    val prin = if (i == n) remP else eachPrincipal
                    val int_ = if (i == n) remI else eachInterest
                    remP -= eachPrincipal; remI -= eachInterest
                    out += mk(loan, i, addPeriod(loan, cal, i), prin, int_)
                }
            }
            LoanRateMode.SIMPLE -> { // amortized (annuity)
                val rate = r
                val pay = if (rate == 0.0) p.toDouble() / n
                else p * rate * Math.pow(1 + rate, n.toDouble()) / (Math.pow(1 + rate, n.toDouble()) - 1)
                var remaining = p.toDouble()
                for (i in 1..n) {
                    val interest = (remaining * rate).toLong()
                    var principal = (pay - interest).toLong()
                    if (i == n || principal > remaining.toLong()) principal = remaining.toLong()
                    if (principal < 0) principal = 0
                    remaining -= principal
                    out += mk(loan, i, addPeriod(loan, cal, i), principal, interest)
                }
            }
        }
        return out
    }

    private fun mk(loan: Loan, seq: Int, due: Long, principal: Long, interest: Long) = Installment(
        id = UUID.randomUUID().toString(), loanId = loan.id, seq = seq,
        dueDate = due, principalMinor = principal, interestMinor = interest,
    )

    private fun addPeriod(loan: Loan, cal: Calendar, k: Int): Long {
        val c = cal.clone() as Calendar
        when (loan.periodUnit) {
            LoanPeriodUnit.DAY -> c.add(Calendar.DAY_OF_MONTH, k)
            LoanPeriodUnit.WEEK -> c.add(Calendar.WEEK_OF_YEAR, k)
            LoanPeriodUnit.MONTH -> {
                c.set(Calendar.DAY_OF_MONTH, minOf(loan.dueDay, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
                c.add(Calendar.MONTH, k)
                c.set(Calendar.DAY_OF_MONTH, minOf(loan.dueDay, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
            LoanPeriodUnit.YEAR -> {
                c.set(Calendar.DAY_OF_MONTH, minOf(loan.dueDay, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
                c.add(Calendar.YEAR, k)
                c.set(Calendar.DAY_OF_MONTH, minOf(loan.dueDay, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
        }
        return c.timeInMillis
    }

    suspend fun create(loan: Loan, alsoDisburse: Boolean, accountId: String?) {
        val ts = System.currentTimeMillis()
        val rec = loan.copy(id = loan.id.ifEmpty { UUID.randomUUID().toString() }, createdAt = ts, updatedAt = ts)
        db.loanDao().insert(rec)
        val schedule = buildSchedule(rec)
        if (schedule.isNotEmpty()) db.loanDao().insertInstallments(schedule)
        audit.write("loan", rec.id, AuditAction.INSERT, "${rec.title} ${rec.principalMinor}")
        sync.enqueue("loan", rec.id, AuditAction.INSERT, rec)
        if (alsoDisburse && accountId != null && rec.direction == com.divan.app.core.db.entity.LoanDirection.TAKEN) {
            com.divan.app.data.TxnCreator(db, sync, settings).create(
                Txn(
                    type = TxnType.LOAN_DISBURSE, amountMinor = rec.principalMinor,
                    currencyCode = rec.currencyCode, accountId = accountId,
                    title = rec.title, note = "وام: ${rec.title}", date = rec.startDate,
                    refId = rec.id, status = TxnStatus.CONFIRMED,
                    createdAt = ts, updatedAt = ts,
                )
            )
        }
    }

    suspend fun delete(id: String) {
        db.loanDao().softDelete(id, System.currentTimeMillis())
        audit.write("loan", id, AuditAction.DELETE, "")
        sync.enqueue("loan", id, AuditAction.DELETE, null)
    }

    /** Pay part or all of an installment; prevents double full payment. */
    suspend fun payInstallment(installmentId: String, amountMinor: Long, accountId: String?, date: Long, method: PayMethod) {
        val inst = db.loanDao().installmentById(installmentId) ?: return
        require(inst.status != InstallmentStatus.PAID) { "قسط قبلا پرداخت شده است" }
        val total = inst.principalMinor + inst.interestMinor
        val newPaid = (inst.paidMinor + amountMinor).coerceAtMost(total)
        val status = if (newPaid >= total) InstallmentStatus.PAID else InstallmentStatus.PARTIAL
        db.loanDao().updateInstallment(inst.copy(paidMinor = newPaid, paidDate = date, status = status))
        val creator = TxnCreator(db, sync, settings)
        creator.create(
            Txn(
                type = TxnType.LOAN_REPAY, amountMinor = amountMinor,
                currencyCode = "", accountId = accountId, method = method,
                title = "قسط ${inst.seq}", date = date, refId = inst.id,
                status = TxnStatus.CONFIRMED, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
            )
        )
        audit.write("installment", inst.id, AuditAction.UPDATE, "paid=$newPaid/$total")
    }

    suspend fun loanRemaining(loanId: String): Long {
        val loan = db.loanDao().byId(loanId) ?: return 0
        val insts = db.loanDao().installmentsOnce(loanId)
        return insts.sumOf { (it.principalMinor + it.interestMinor - it.paidMinor) }
    }

    suspend fun paidStats(loanId: String): Pair<Long, Long> {
        val insts = db.loanDao().installmentsOnce(loanId)
        val paid = insts.sumOf { it.paidMinor }
        val total = insts.sumOf { it.principalMinor + it.interestMinor }
        return paid to total
    }
}

/** Small helper shared by repos that need to create linked transactions. */
class TxnCreator(private val db: AppDatabase, private val sync: SyncQueue, private val settings: SettingsRepo) {
    val repo = TxnRepo(db, sync, settings)
    suspend fun create(t: Txn) = repo.create(t)
}

class DebtRepo(
    private val db: AppDatabase,
    private val sync: SyncQueue,
    private val settings: SettingsRepo,
) {
    private val audit = AuditWriter(db.auditDao())
    fun all(): Flow<List<DebtCredit>> = db.debtDao().all()
    suspend fun byId(id: String): DebtCredit? = db.debtDao().byId(id)
    fun payments(debtId: String) = db.debtDao().payments(debtId)
    suspend fun paidSum(debtId: String): Long = db.debtDao().paidSum(debtId)

    suspend fun create(d: DebtCredit) {
        val ts = System.currentTimeMillis()
        val rec = d.copy(id = d.id.ifEmpty { UUID.randomUUID().toString() }, createdAt = ts, updatedAt = ts)
        db.debtDao().insert(rec)
        audit.write("debt", rec.id, AuditAction.INSERT, "${rec.personName} ${rec.amountMinor}")
        sync.enqueue("debt", rec.id, AuditAction.INSERT, rec)
    }

    suspend fun update(d: DebtCredit) {
        db.debtDao().update(d.copy(updatedAt = System.currentTimeMillis()))
        sync.enqueue("debt", d.id, AuditAction.UPDATE, d)
    }

    suspend fun delete(id: String) {
        db.debtDao().softDelete(id, System.currentTimeMillis())
        sync.enqueue("debt", id, AuditAction.DELETE, null)
    }

    suspend fun addPayment(debtId: String, amountMinor: Long, accountId: String?, date: Long, note: String?) {
        val debt = db.debtDao().byId(debtId) ?: return
        val creator = TxnCreator(db, sync, settings)
        val txn = creator.create(
            Txn(
                type = if (debt.direction == DebtDirection.I_OWE) TxnType.DEBT_REPAY_OUT else TxnType.DEBT_COLLECT_IN,
                amountMinor = amountMinor, currencyCode = debt.currencyCode,
                accountId = accountId, title = debt.personName,
                note = note ?: (if (debt.direction == DebtDirection.I_OWE) "پرداخت بدهی" else "دریافت طلب"),
                date = date, refId = debtId, status = TxnStatus.CONFIRMED,
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
            )
        )
        db.debtDao().insertPayment(
            DebtPayment(UUID.randomUUID().toString(), debtId, amountMinor, date, accountId, note, txn.id)
        )
        val paid = db.debtDao().paidSum(debtId)
        if (paid >= debt.amountMinor && debt.status == DebtStatus.OPEN) {
            db.debtDao().update(debt.copy(status = DebtStatus.SETTLED, updatedAt = System.currentTimeMillis()))
        }
        audit.write("debt", debtId, AuditAction.UPDATE, "payment=$amountMinor")
    }
}

class CheckRepo(
    private val db: AppDatabase,
    private val sync: SyncQueue,
    private val settings: SettingsRepo,
) {
    private val audit = AuditWriter(db.auditDao())
    fun all(): Flow<List<CheckItem>> = db.checkDao().all()
    suspend fun byId(id: String): CheckItem? = db.checkDao().byId(id)

    suspend fun save(c: CheckItem, isNew: Boolean) {
        val ts = System.currentTimeMillis()
        val rec = c.copy(id = c.id.ifEmpty { UUID.randomUUID().toString() }, createdAt = if (isNew) ts else c.createdAt, updatedAt = ts)
        db.checkDao().insert(rec)
        audit.write("check", rec.id, if (isNew) AuditAction.INSERT else AuditAction.UPDATE, "${rec.status}")
        sync.enqueue("check", rec.id, if (isNew) AuditAction.INSERT else AuditAction.UPDATE, rec)
    }

    suspend fun delete(id: String) {
        db.checkDao().softDelete(id, System.currentTimeMillis())
        sync.enqueue("check", id, AuditAction.DELETE, null)
    }

    /** Cashing a cheque creates the real money movement. */
    suspend fun cash(id: String, date: Long) {
        val c = db.checkDao().byId(id) ?: return
        if (c.status == CheckStatus.CASHED) return
        db.checkDao().insert(c.copy(status = CheckStatus.CASHED, updatedAt = System.currentTimeMillis()))
        val creator = TxnCreator(db, sync, settings)
        creator.create(
            Txn(
                type = if (c.direction == com.divan.app.core.db.entity.CheckDirection.RECEIVED) TxnType.INCOME else TxnType.EXPENSE,
                amountMinor = c.amountMinor, currencyCode = c.currencyCode,
                accountId = c.accountId, title = "چک ${c.chequeNumber ?: ""}",
                note = c.personName, date = date, refId = c.id, method = PayMethod.CHEQUE,
                status = TxnStatus.CONFIRMED, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun markBounced(id: String) {
        val c = db.checkDao().byId(id) ?: return
        db.checkDao().insert(c.copy(status = CheckStatus.BOUNCED, updatedAt = System.currentTimeMillis()))
        audit.write("check", id, AuditAction.UPDATE, "BOUNCED")
    }

    suspend fun mark(id: String, status: CheckStatus) {
        val c = db.checkDao().byId(id) ?: return
        db.checkDao().insert(c.copy(status = status, updatedAt = System.currentTimeMillis()))
    }
}

class BudgetRepo(
    private val db: AppDatabase,
    private val sync: SyncQueue,
    private val settings: SettingsRepo,
) {
    private val audit = AuditWriter(db.auditDao())
    fun all(): Flow<List<Budget>> = db.budgetDao().all()
    fun forPeriod(year: Int, month: Int) = db.budgetDao().forPeriod(year, month)

    suspend fun save(b: Budget, isNew: Boolean) {
        val ts = System.currentTimeMillis()
        val rec = b.copy(id = b.id.ifEmpty { UUID.randomUUID().toString() }, createdAt = if (isNew) ts else b.createdAt, updatedAt = ts)
        db.budgetDao().insert(rec)
        audit.write("budget", rec.id, AuditAction.UPDATE, "limit=${rec.limitMinor}")
        sync.enqueue("budget", rec.id, if (isNew) AuditAction.INSERT else AuditAction.UPDATE, rec)
    }

    suspend fun delete(id: String) {
        db.budgetDao().softDelete(id, System.currentTimeMillis())
        sync.enqueue("budget", id, AuditAction.DELETE, null)
    }

    suspend fun spent(b: Budget): Long {
        val cal = Calendar.getInstance()
        cal.set(b.year, b.month - 1, 1, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val to = cal.timeInMillis
        val txns = db.txnDao().betweenOnce(from, to).filter {
            !it.isDeleted && it.status != TxnStatus.DRAFT && it.type == TxnType.EXPENSE
        }
        val base = settings.baseCurrencyNow()
        val conv = com.divan.app.core.money.CurrencyConverter(db)
        return txns.filter { t -> b.categoryId == null || t.categoryId == b.categoryId }
            .sumOf { t ->
                if (t.currencyCode == base) t.amountMinor
                else conv.toBase(t.amountMinor, t.currencyCode, base, t.date)
            }
    }
}

class RecurringRepo(
    private val db: AppDatabase,
    private val sync: SyncQueue,
    private val settings: SettingsRepo,
) {
    private val audit = AuditWriter(db.auditDao())
    fun all(): Flow<List<Recurring>> = db.recurringDao().all()
    suspend fun byId(id: String): Recurring? = db.recurringDao().byId(id)

    suspend fun save(r: Recurring, isNew: Boolean) {
        val ts = System.currentTimeMillis()
        val rec = r.copy(id = r.id.ifEmpty { UUID.randomUUID().toString() }, createdAt = if (isNew) ts else r.createdAt, updatedAt = ts)
        db.recurringDao().insert(rec)
        audit.write("recurring", rec.id, AuditAction.INSERT, rec.title)
        sync.enqueue("recurring", rec.id, AuditAction.INSERT, rec)
    }

    suspend fun delete(id: String) {
        db.recurringDao().softDelete(id, System.currentTimeMillis())
        sync.enqueue("recurring", id, AuditAction.DELETE, null)
    }

    fun nextRunAfter(r: Recurring, fromTs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fromTs
        val c = cal.clone() as Calendar
        when (r.intervalUnit) {
            RecurUnit.DAY -> c.add(Calendar.DAY_OF_MONTH, r.intervalCount)
            RecurUnit.WEEK -> c.add(Calendar.WEEK_OF_YEAR, r.intervalCount)
            RecurUnit.MONTH -> c.add(Calendar.MONTH, r.intervalCount)
            RecurUnit.YEAR -> c.add(Calendar.YEAR, r.intervalCount)
        }
        return c.timeInMillis
    }

    /** Materialize due templates into real transactions (idempotent via key). */
    suspend fun materializeDue(now: Long, notify: (String) -> Unit = {}): Int {
        val due = db.recurringDao().dueOnce(now)
        var created = 0
        val creator = TxnCreator(db, sync, settings)
        for (r in due) {
            if (r.endDate != null && r.nextRun > r.endDate!!) {
                db.recurringDao().insert(r.copy(status = RecurStatus.FINISHED, updatedAt = now))
                continue
            }
            var run = r.nextRun
            var guard = 0
            while (run <= now && guard < 60) {
                val key = "${r.id}:${run / 86400000L}"
                if (db.txnDao().byRecurringKey(key) == null) {
                    if (r.autoCreate) {
                        creator.create(
                            Txn(
                                type = r.type, amountMinor = r.amountMinor,
                                currencyCode = r.currencyCode, accountId = r.accountId,
                                toAccountId = r.toAccountId, categoryId = r.categoryId,
                                counterpartyId = r.counterpartyId, method = r.method,
                                title = r.title, note = r.note, date = run,
                                source = com.divan.app.core.db.entity.TxnSource.RECURRING,
                                recurringKey = key, status = TxnStatus.CONFIRMED,
                                createdAt = now, updatedAt = now,
                            )
                        )
                        created++
                    } else {
                        notify(r.title)
                    }
                }
                run = nextRunAfter(r, run)
                guard++
            }
            db.recurringDao().insert(r.copy(lastRun = r.nextRun, nextRun = run, updatedAt = now))
        }
        return created
    }

    suspend fun activeOnce(): List<Recurring> = db.recurringDao().activeOnce()
}

class SavingsRepo(private val db: AppDatabase) {
    fun all(): Flow<List<SavingsGoal>> = db.savingsDao().all()
    suspend fun save(g: SavingsGoal) = db.savingsDao().insert(g.copy(updatedAt = System.currentTimeMillis()))
    suspend fun delete(id: String) = db.savingsDao().softDelete(id, System.currentTimeMillis())
}
