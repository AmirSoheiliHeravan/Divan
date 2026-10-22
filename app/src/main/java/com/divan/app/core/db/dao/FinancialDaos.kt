package com.divan.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.divan.app.core.db.entity.Budget
import com.divan.app.core.db.entity.CheckItem
import com.divan.app.core.db.entity.DebtCredit
import com.divan.app.core.db.entity.DebtPayment
import com.divan.app.core.db.entity.Installment
import com.divan.app.core.db.entity.InstallmentStatus
import com.divan.app.core.db.entity.Loan
import com.divan.app.core.db.entity.Recurring
import com.divan.app.core.db.entity.SavingsGoal
import com.divan.app.core.db.entity.Txn
import com.divan.app.core.db.entity.TxnSplit
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(txn: Txn)

    @Update
    suspend fun update(txn: Txn)

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY date DESC LIMIT :limit OFFSET :offset")
    fun paged(limit: Int, offset: Int): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY date DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY date DESC")
    fun all(): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY date DESC")
    suspend fun allOnce(): List<Txn>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND categoryId IS NOT NULL ORDER BY date DESC LIMIT :limit")
    suspend fun recentWithCategoryOnce(limit: Int): List<Txn>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: String): Txn?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun byIdFlow(id: String): Flow<Txn?>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND date BETWEEN :from AND :to ORDER BY date DESC")
    fun between(from: Long, to: Long): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND date BETWEEN :from AND :to ORDER BY date DESC")
    suspend fun betweenOnce(from: Long, to: Long): List<Txn>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND (title LIKE '%'||:q||'%' OR note LIKE '%'||:q||'%' OR amountMinor LIKE :q||'%') ORDER BY date DESC")
    fun search(q: String): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE refId = :refId AND isDeleted = 0")
    suspend fun byRef(refId: String): List<Txn>

    @Query("SELECT * FROM transactions WHERE recurringKey = :key LIMIT 1")
    suspend fun byRecurringKey(key: String): Txn?

    @Query("UPDATE transactions SET isDeleted = 1, updatedAt = :ts, version = version + 1 WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("UPDATE transactions SET status = :status, updatedAt = :ts WHERE id = :id")
    suspend fun setStatus(id: String, status: String, ts: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE isDeleted = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM transactions WHERE source = 'SMS' AND status = 'PENDING' AND isDeleted = 0 ORDER BY date DESC")
    fun pendingSms(): Flow<List<Txn>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplits(list: List<TxnSplit>)

    @Query("SELECT * FROM txn_splits WHERE txnId = :txnId")
    suspend fun splits(txnId: String): List<TxnSplit>

    @Query("DELETE FROM txn_splits WHERE txnId = :txnId")
    suspend fun clearSplits(txnId: String)
}

@Dao
interface LoanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loan: Loan)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallments(list: List<Installment>)

    @Update
    suspend fun update(loan: Loan)

    @Update
    suspend fun updateInstallment(i: Installment)

    @Query("SELECT * FROM loans WHERE isDeleted = 0 ORDER BY startDate DESC")
    fun all(): Flow<List<Loan>>

    @Query("SELECT * FROM loans ORDER BY startDate DESC")
    suspend fun allOnce(): List<Loan>

    @Query("SELECT * FROM installments ORDER BY dueDate")
    suspend fun allInstallmentsOnce(): List<Installment>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun byId(id: String): Loan?

    @Query("SELECT * FROM loans WHERE id = :id")
    fun byIdFlow(id: String): Flow<Loan?>

    @Query("SELECT * FROM installments WHERE loanId = :loanId ORDER BY seq")
    fun installments(loanId: String): Flow<List<Installment>>

    @Query("SELECT * FROM installments WHERE loanId = :loanId ORDER BY seq")
    suspend fun installmentsOnce(loanId: String): List<Installment>

    @Query("SELECT * FROM installments WHERE status != 'PAID' ORDER BY dueDate LIMIT :limit")
    fun upcoming(limit: Int): Flow<List<Installment>>

    @Query("SELECT * FROM installments WHERE status != 'PAID' ORDER BY dueDate")
    suspend fun unpaidOnce(): List<Installment>

    @Query("SELECT * FROM installments WHERE id = :id")
    suspend fun installmentById(id: String): Installment?

    @Query("UPDATE loans SET isDeleted = 1, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("UPDATE installments SET calendarEventId = :eventId WHERE id = :id")
    suspend fun setInstallmentEvent(id: String, eventId: Long?)
}

@Dao
interface DebtDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(d: DebtCredit)

    @Update
    suspend fun update(d: DebtCredit)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(p: DebtPayment)

    @Query("SELECT * FROM debts WHERE isDeleted = 0 ORDER BY startDate DESC")
    fun all(): Flow<List<DebtCredit>>

    @Query("SELECT * FROM debts ORDER BY startDate DESC")
    suspend fun allOnce(): List<DebtCredit>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun byId(id: String): DebtCredit?

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY date")
    fun payments(debtId: String): Flow<List<DebtPayment>>

    @Query("SELECT COALESCE(SUM(amountMinor),0) FROM debt_payments WHERE debtId = :debtId")
    suspend fun paidSum(debtId: String): Long

    @Query("UPDATE debts SET isDeleted = 1, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)
}

@Dao
interface CheckDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: CheckItem)

    @Update
    suspend fun update(c: CheckItem)

    @Query("SELECT * FROM checks WHERE isDeleted = 0 ORDER BY dueDate")
    fun all(): Flow<List<CheckItem>>

    @Query("SELECT * FROM checks ORDER BY dueDate")
    suspend fun allOnce(): List<CheckItem>

    @Query("SELECT * FROM checks WHERE id = :id")
    suspend fun byId(id: String): CheckItem?

    @Query("SELECT * FROM checks WHERE isDeleted = 0 AND status IN ('REGISTERED','TRANSFERRED','BOUNCED') AND dueDate BETWEEN :from AND :to ORDER BY dueDate")
    suspend fun dueBetween(from: Long, to: Long): List<CheckItem>

    @Query("UPDATE checks SET isDeleted = 1, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("UPDATE checks SET calendarEventId = :eventId WHERE id = :id")
    suspend fun setEvent(id: String, eventId: Long?)
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(b: Budget)

    @Update
    suspend fun update(b: Budget)

    @Query("SELECT * FROM budgets WHERE isDeleted = 0 ORDER BY year DESC, month DESC")
    fun all(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets ORDER BY year DESC, month DESC")
    suspend fun allOnce(): List<Budget>

    @Query("SELECT * FROM budgets WHERE isDeleted = 0 AND year = :year AND month = :month")
    fun forPeriod(year: Int, month: Int): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE isDeleted = 0 AND year = :year AND month = :month")
    suspend fun forPeriodOnce(year: Int, month: Int): List<Budget>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun byId(id: String): Budget?

    @Query("UPDATE budgets SET isDeleted = 1, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)
}

@Dao
interface SavingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(g: SavingsGoal)

    @Update
    suspend fun update(g: SavingsGoal)

    @Query("SELECT * FROM savings_goals WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun all(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun byId(id: String): SavingsGoal?

    @Query("UPDATE savings_goals SET isDeleted = 1, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)
}

@Dao
interface RecurringDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: Recurring)

    @Update
    suspend fun update(r: Recurring)

    @Query("SELECT * FROM recurring WHERE isDeleted = 0 ORDER BY nextRun")
    fun all(): Flow<List<Recurring>>

    @Query("SELECT * FROM recurring ORDER BY nextRun")
    suspend fun allOnce(): List<Recurring>

    @Query("SELECT * FROM recurring WHERE isDeleted = 0 AND status = 'ACTIVE' AND nextRun <= :now")
    suspend fun dueOnce(now: Long): List<Recurring>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun byId(id: String): Recurring?

    @Query("UPDATE recurring SET isDeleted = 1, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("SELECT * FROM recurring WHERE isDeleted = 0 AND status = 'ACTIVE'")
    suspend fun activeOnce(): List<Recurring>
}
