package com.divan.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.divan.app.core.db.entity.Account
import com.divan.app.core.db.entity.AccountStatus
import com.divan.app.core.db.entity.Category
import com.divan.app.core.db.entity.Counterparty
import com.divan.app.core.db.entity.CurrencyUnit
import com.divan.app.core.db.entity.ExchangeRate
import com.divan.app.core.db.entity.FamilySpace
import com.divan.app.core.db.entity.Member
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(space: FamilySpace)

    @Query("SELECT COUNT(*) FROM family_space")
    suspend fun countAny(): Int

    @Query("SELECT * FROM family_space LIMIT 1")
    fun first(): Flow<FamilySpace?>

    @Query("SELECT * FROM family_space LIMIT 1")
    suspend fun firstOnce(): FamilySpace?
}

@Dao
interface MemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: Member)

    @Update
    suspend fun update(member: Member)

    @Query("SELECT * FROM members WHERE isDeleted = 0 ORDER BY name")
    fun all(): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE isDeleted = 0 ORDER BY name")
    suspend fun allOnce(): List<Member>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun byId(id: String): Member?

    @Query("UPDATE members SET isDeleted = 1, updatedAt = :ts, version = version + 1 WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)
}

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account)

    @Update
    suspend fun update(account: Account)

    @Query("SELECT * FROM accounts WHERE isDeleted = 0 ORDER BY name")
    fun all(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE isDeleted = 0 AND status = 'ACTIVE' ORDER BY name")
    fun allActive(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE isDeleted = 0 ORDER BY name")
    suspend fun allOnce(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: String): Account?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun byIdFlow(id: String): Flow<Account?>

    @Query("SELECT * FROM accounts WHERE cardLast4 = :last4 AND isDeleted = 0 LIMIT 1")
    suspend fun byCardLast4(last4: String): Account?

    @Query("UPDATE accounts SET isDeleted = 1, updatedAt = :ts, version = version + 1 WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("SELECT COUNT(*) FROM accounts WHERE isDeleted = 0")
    suspend fun count(): Int
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Category>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category)

    @Update
    suspend fun update(category: Category)

    @Query("SELECT * FROM categories WHERE isDeleted = 0 AND active = 1 ORDER BY sortOrder, name")
    fun allActive(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY sortOrder, name")
    fun all(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY sortOrder, name")
    suspend fun allOnce(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: String): Category?

    @Query("SELECT * FROM categories WHERE parentId IS NULL AND isDeleted = 0 AND active = 1 ORDER BY sortOrder")
    suspend fun rootsOnce(): List<Category>

    @Query("SELECT * FROM categories WHERE parentId = :parentId AND isDeleted = 0 AND active = 1 ORDER BY sortOrder, name")
    suspend fun childrenOnce(parentId: String): List<Category>

    @Query("UPDATE categories SET active = :active, updatedAt = :ts WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, ts: Long)

    @Query("UPDATE categories SET isDeleted = 1, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countAll(): Int
}

@Dao
interface CurrencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<CurrencyUnit>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(currency: CurrencyUnit)

    @Update
    suspend fun update(currency: CurrencyUnit)

    @Query("SELECT * FROM currencies WHERE active = 1 ORDER BY isBase DESC, code")
    fun all(): Flow<List<CurrencyUnit>>

    @Query("SELECT * FROM currencies WHERE active = 1 ORDER BY isBase DESC, code")
    suspend fun allOnce(): List<CurrencyUnit>

    @Query("SELECT * FROM currencies WHERE code = :code")
    suspend fun byCode(code: String): CurrencyUnit?

    @Query("SELECT * FROM currencies WHERE isBase = 1 LIMIT 1")
    suspend fun baseOnce(): CurrencyUnit?

    @Query("SELECT * FROM currencies WHERE isBase = 1 LIMIT 1")
    fun base(): Flow<CurrencyUnit?>

    @Query("UPDATE currencies SET isBase = 0")
    suspend fun clearBase()

    @Query("UPDATE currencies SET isBase = 1 WHERE code = :code")
    suspend fun setBase(code: String)
}

@Dao
interface RateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rate: ExchangeRate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rates: List<ExchangeRate>)

    @Query("SELECT * FROM exchange_rates WHERE currencyCode = :code ORDER BY date DESC LIMIT 1")
    suspend fun latest(code: String): ExchangeRate?

    @Query("SELECT * FROM exchange_rates WHERE currencyCode = :code AND date <= :ts ORDER BY date DESC LIMIT 1")
    suspend fun latestAt(code: String, ts: Long): ExchangeRate?

    @Query("SELECT * FROM exchange_rates ORDER BY date DESC")
    fun all(): Flow<List<ExchangeRate>>

    @Query("SELECT * FROM exchange_rates WHERE currencyCode = :code ORDER BY date DESC")
    fun forCurrency(code: String): Flow<List<ExchangeRate>>

    @Query("DELETE FROM exchange_rates WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CounterpartyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: Counterparty)

    @Update
    suspend fun update(c: Counterparty)

    @Query("SELECT * FROM counterparties WHERE isDeleted = 0 ORDER BY name")
    fun all(): Flow<List<Counterparty>>

    @Query("SELECT * FROM counterparties WHERE isDeleted = 0 ORDER BY name")
    suspend fun allOnce(): List<Counterparty>

    @Query("SELECT * FROM counterparties WHERE id = :id")
    suspend fun byId(id: String): Counterparty?

    @Query("UPDATE counterparties SET isDeleted = 1, updatedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("SELECT COUNT(*) FROM counterparties WHERE isDeleted = 0")
    suspend fun count(): Int
}
