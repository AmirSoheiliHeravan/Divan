package com.divan.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxnType {
    INCOME, EXPENSE, TRANSFER,
    LOAN_DISBURSE, LOAN_REPAY,
    DEBT_COLLECT_IN, DEBT_REPAY_OUT,
    SAVINGS_IN, SAVINGS_OUT, INVEST_IN, INVEST_OUT,
    ASSET_BUY, ASSET_SELL,
    PURCHASE, SALE,
    BALANCE_ADJUST, FEE
}

enum class TxnStatus { DRAFT, CONFIRMED, PENDING, BALANCE_ONLY, RETURNED, ARCHIVED }

enum class PayMethod { CASH, CARD, TRANSFER, CREDIT, CHEQUE, OTHER }

enum class TxnSource { MANUAL, SMS, WEAR, RECURRING, INVOICE, WIZARD }

@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index("categoryId"), Index("date"), Index("type"), Index("status")]
)
data class Txn(
    @PrimaryKey val id: String,
    val familyId: String = "family",
    val type: TxnType = TxnType.EXPENSE,
    val amountMinor: Long = 0,
    val feeMinor: Long = 0,                // کارمزد (expense/transfer) — separate from amount
    val currencyCode: String = "IRR",
    val rateUsed: Double = 1.0,
    val baseAmountMinor: Long = 0,
    val accountId: String? = null,
    val toAccountId: String? = null,      // for transfers / savings
    val categoryId: String? = null,
    val counterpartyId: String? = null,
    val memberIds: String? = null,        // CSV
    val splitMode: String? = null,        // EQUAL | PERCENT | FIXED
    val date: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val method: PayMethod = PayMethod.CASH,
    val status: TxnStatus = TxnStatus.CONFIRMED,
    val title: String = "",
    val note: String? = null,
    val tagsCsv: String? = null,
    val receiptPath: String? = null,
    val itemsJson: String? = null,        // اقلام انتخاب‌شده (لیست خرید/فاکتور) — JSON
    val source: TxnSource = TxnSource.MANUAL,
    val refId: String? = null,            // loan/installment/check/debt/invoice id
    val recurringKey: String? = null,     // template+period dedup key
    val createdAt: Long,
    val createdBy: String = "local",
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

@Entity(tableName = "txn_splits", indices = [Index("txnId")])
data class TxnSplit(
    @PrimaryKey val id: String,
    val txnId: String,
    val memberId: String,
    val sharePercent: Double = 0.0,
    val amountMinor: Long = 0,
)

enum class LoanDirection { TAKEN, GIVEN }
enum class LoanRateMode { NONE, FLAT, SIMPLE }
enum class LoanPeriodUnit { DAY, WEEK, MONTH, YEAR }
enum class LoanStatus { ACTIVE, SETTLED, ARCHIVED }

@Entity(tableName = "loans", indices = [Index("status")])
data class Loan(
    @PrimaryKey val id: String,
    val direction: LoanDirection = LoanDirection.TAKEN,
    val title: String,
    val counterpartyId: String? = null,
    val personName: String? = null,
    val principalMinor: Long,
    val currencyCode: String = "IRR",
    val ratePercent: Double = 0.0,
    val rateMode: LoanRateMode = LoanRateMode.NONE,
    val installmentsCount: Int = 0,
    val periodUnit: LoanPeriodUnit = LoanPeriodUnit.MONTH,
    val startDate: Long,
    val dueDay: Int = 1,
    val feeMinor: Long = 0,
    val collateral: String? = null,
    val accountId: String? = null,
    val note: String? = null,
    val status: LoanStatus = LoanStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

enum class InstallmentStatus { PENDING, PARTIAL, PAID, LATE }

@Entity(tableName = "installments", indices = [Index("loanId"), Index("dueDate")])
data class Installment(
    @PrimaryKey val id: String,
    val loanId: String,
    val seq: Int,
    val dueDate: Long,
    val principalMinor: Long,
    val interestMinor: Long,
    val paidMinor: Long = 0,
    val paidDate: Long? = null,
    val status: InstallmentStatus = InstallmentStatus.PENDING,
    val note: String? = null,
    val calendarEventId: Long? = null,
)

enum class DebtDirection { I_OWE, THEY_OWE }
enum class DebtStatus { OPEN, SETTLED }

@Entity(tableName = "debts", indices = [Index("status")])
data class DebtCredit(
    @PrimaryKey val id: String,
    val direction: DebtDirection = DebtDirection.I_OWE,
    val counterpartyId: String? = null,
    val personName: String,
    val amountMinor: Long,
    val currencyCode: String = "IRR",
    val accountId: String? = null,
    val startDate: Long,
    val dueDate: Long? = null,
    val note: String? = null,
    val status: DebtStatus = DebtStatus.OPEN,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

@Entity(tableName = "debt_payments", indices = [Index("debtId")])
data class DebtPayment(
    @PrimaryKey val id: String,
    val debtId: String,
    val amountMinor: Long,
    val date: Long,
    val accountId: String? = null,
    val note: String? = null,
    val txnId: String? = null,
)

enum class CheckDirection { RECEIVED, ISSUED }
enum class CheckStatus { REGISTERED, TRANSFERRED, CASHED, BOUNCED, CANCELED, SETTLED }

@Entity(tableName = "checks", indices = [Index("dueDate"), Index("status")])
data class CheckItem(
    @PrimaryKey val id: String,
    val direction: CheckDirection = CheckDirection.ISSUED,
    val amountMinor: Long,
    val currencyCode: String = "IRR",
    val bankName: String? = null,
    val chequeNumber: String? = null,
    val issueDate: Long,
    val dueDate: Long,
    val counterpartyId: String? = null,
    val personName: String? = null,
    val accountId: String? = null,
    val status: CheckStatus = CheckStatus.REGISTERED,
    val note: String? = null,
    val calendarEventId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

enum class BudgetScope { CATEGORY, TOTAL }
enum class BudgetPeriod { MONTH }

@Entity(tableName = "budgets", indices = [Index("year", "month")])
data class Budget(
    @PrimaryKey val id: String,
    val scope: BudgetScope = BudgetScope.CATEGORY,
    val categoryId: String? = null,
    val memberId: String? = null,
    val year: Int,
    val month: Int,                       // 1..12
    val limitMinor: Long,
    val currencyCode: String = "IRR",
    val rollover: Boolean = false,
    val warnPercent: Int = 80,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey val id: String,
    val title: String,
    val targetMinor: Long,
    val currencyCode: String = "IRR",
    val savedMinor: Long = 0,
    val deadline: Long? = null,
    val accountId: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

enum class RecurUnit { DAY, WEEK, MONTH, YEAR }
enum class RecurStatus { ACTIVE, PAUSED, FINISHED }

@Entity(tableName = "recurring", indices = [Index("nextRun")])
data class Recurring(
    @PrimaryKey val id: String,
    val title: String,
    val type: TxnType = TxnType.EXPENSE,
    val amountMinor: Long,
    val currencyCode: String = "IRR",
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val counterpartyId: String? = null,
    val method: PayMethod = PayMethod.CASH,
    val intervalUnit: RecurUnit = RecurUnit.MONTH,
    val intervalCount: Int = 1,
    val startDate: Long,
    val endDate: Long? = null,
    val lastRun: Long? = null,
    val nextRun: Long,
    val autoCreate: Boolean = true,       // false = only remind
    val notifyBeforeDays: Int = 1,
    val note: String? = null,
    val status: RecurStatus = RecurStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)
