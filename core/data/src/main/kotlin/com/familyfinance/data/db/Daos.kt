package com.familyfinance.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.familyfinance.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
    @Upsert suspend fun upsert(items: List<CurrencyEntity>)
    @Query("SELECT uid, updatedAt, deviceId FROM currencies") suspend fun versions(): List<VersionRow>
    @Query("SELECT * FROM currencies WHERE deleted = 0 ORDER BY isMain DESC, uid")
    fun observeAll(): Flow<List<CurrencyEntity>>
}

@Dao
interface AccountGroupDao {
    @Upsert suspend fun upsert(items: List<AccountGroupEntity>)
    @Query("SELECT uid, updatedAt, deviceId FROM account_groups") suspend fun versions(): List<VersionRow>
    @Query("SELECT * FROM account_groups WHERE deleted = 0 ORDER BY sortOrder")
    fun observeAll(): Flow<List<AccountGroupEntity>>
}

@Dao
interface AccountDao {
    @Upsert suspend fun upsert(items: List<AccountEntity>)
    @Query("SELECT uid, updatedAt, deviceId FROM accounts") suspend fun versions(): List<VersionRow>
    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY sortOrder")
    fun observeAll(): Flow<List<AccountEntity>>
}

@Dao
interface CategoryDao {
    @Upsert suspend fun upsert(items: List<CategoryEntity>)
    @Query("SELECT uid, updatedAt, deviceId FROM categories") suspend fun versions(): List<VersionRow>
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY type, sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>
}

@Dao
interface TransactionDao {
    @Upsert suspend fun upsert(items: List<TransactionEntity>)
    @Query("SELECT uid, updatedAt, deviceId FROM transactions") suspend fun versions(): List<VersionRow>
    @Upsert suspend fun upsert(item: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE uid = :uid")
    suspend fun get(uid: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND date >= :from AND date < :to ORDER BY date DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>>

    /** When [deviceId] last changed an operation; null if never. */
    @Query("SELECT MAX(updatedAt) FROM transactions WHERE deviceId = :deviceId")
    fun observeLastChangeBy(deviceId: String): Flow<Long?>

    @Query("SELECT MIN(date) FROM transactions WHERE deleted = 0")
    suspend fun firstDate(): Long?

    @Query("SELECT accountUid FROM transactions WHERE deleted = 0 ORDER BY date DESC LIMIT 1")
    suspend fun lastUsedAccountUid(): String?

    /** Most used categories among the latest [window] operations of [type]. */
    @Query(
        """
        SELECT categoryUid FROM (
            SELECT categoryUid FROM transactions
            WHERE deleted = 0 AND type = :type AND categoryUid IS NOT NULL
            ORDER BY date DESC LIMIT :window
        ) GROUP BY categoryUid ORDER BY COUNT(*) DESC LIMIT :limit
        """,
    )
    suspend fun frequentCategoryUids(type: TransactionType, limit: Int, window: Int): List<String>

    @Query("SELECT COUNT(*) FROM transactions WHERE deleted = 0")
    fun observeCount(): Flow<Int>

    /** Balance per account in its own currency: income/expense/transfer-out on the source side, transfer-in on the target. */
    @Query(
        """
        SELECT uid, SUM(delta) AS balance FROM (
            SELECT accountUid AS uid,
                   CASE type WHEN 'INCOME' THEN accountAmount ELSE -accountAmount END AS delta
            FROM transactions WHERE deleted = 0
            UNION ALL
            SELECT toAccountUid AS uid, COALESCE(toAccountAmount, accountAmount) AS delta
            FROM transactions WHERE deleted = 0 AND type = 'TRANSFER' AND toAccountUid IS NOT NULL
        ) GROUP BY uid
        """,
    )
    fun observeBalances(): Flow<List<BalanceRow>>
}
