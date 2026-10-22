package com.divan.app.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Family data space – locally stored; server-side separation reserved for future sync. */
@Entity(tableName = "family_space")
data class FamilySpace(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

enum class MemberRole { OWNER, ADMIN, FINANCE, MEMBER, READ_ONLY, CHILD, GUEST }

@Entity(tableName = "members")
data class Member(
    @PrimaryKey val id: String,
    val name: String,
    val role: MemberRole = MemberRole.MEMBER,
    val colorHex: String = "#2E7D5B",
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

enum class AccountType {
    CHECKING, SAVINGS, FOREIGN, CARD, CASH, DIGITAL_WALLET, FAMILY_FUND, JOINT,
    GOAL_SAVINGS, INVESTMENT, DEPOSIT_HELD, DEBT_TRACK, SETTLEMENT, CUSTOM
}

enum class AccountStatus { ACTIVE, CLOSED, ARCHIVED }

@Entity(tableName = "accounts", indices = [Index("currencyCode"), Index("type")])
data class Account(
    @PrimaryKey val id: String,
    val name: String,
    val alias: String? = null,
    val type: AccountType = AccountType.CASH,
    val ownerMemberId: String? = null,
    val sharedMemberIds: String? = null,   // CSV of member ids
    val bankName: String? = null,
    val cardLast4: String? = null,          // for bank-SMS auto matching
    val accountNumber: String? = null,
    val currencyCode: String = "IRR",
    val openingBalanceMinor: Long = 0,
    val openingDate: Long = 0,
    val minBalanceMinor: Long? = null,
    val status: AccountStatus = AccountStatus.ACTIVE,
    val note: String? = null,
    val colorHex: String = "#2E7D5B",
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

enum class CategoryKind { INCOME, EXPENSE, BOTH }

@Entity(tableName = "categories", indices = [Index("parentId")])
data class Category(
    @PrimaryKey val id: String,
    val parentId: String? = null,
    val name: String,
    val nameEn: String,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val icon: String = "🏷",           // emoji
    val colorHex: String = "#2E7D5B",
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,     // seeded, cannot be deleted – can be hidden
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)

@Entity(tableName = "currencies")
data class CurrencyUnit(
    @PrimaryKey val code: String,      // ISO 4217 or custom code
    val name: String,
    val nameFa: String,
    val symbol: String,
    val decimals: Int = 0,             // minor-unit digits (USD=2, IRR=0)
    val isBase: Boolean = false,
    val isCustom: Boolean = false,
    val active: Boolean = true,
)

@Entity(tableName = "exchange_rates", indices = [Index("currencyCode")])
data class ExchangeRate(
    @PrimaryKey val id: String,
    val currencyCode: String,
    val rateToBase: Double,            // 1 unit of currency = rateToBase * base
    val date: Long,
    val source: String = "manual",     // manual | api:<name>
    val note: String? = null,
    val createdAt: Long,
)

enum class CounterpartyKind { PERSON, SHOP, BANK, COMPANY, OTHER }

@Entity(tableName = "counterparties")
data class Counterparty(
    @PrimaryKey val id: String,
    val name: String,
    val kind: CounterpartyKind = CounterpartyKind.PERSON,
    val phone: String? = null,
    val contactUri: String? = null,     // linked phone contact
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val version: Int = 1,
)
