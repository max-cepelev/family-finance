package com.familyfinance.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Insert suspend fun enqueue(items: List<PendingChangeEntity>)

    @Query("SELECT * FROM pending_changes ORDER BY seq LIMIT :limit")
    suspend fun pending(limit: Int): List<PendingChangeEntity>

    @Query("SELECT COUNT(*) FROM pending_changes")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM pending_changes WHERE seq <= :seq")
    suspend fun deletePendingUpTo(seq: Long)

    @Query("DELETE FROM pending_changes WHERE seq IN (:seqs)")
    suspend fun deletePending(seqs: List<Long>)

    @Query("DELETE FROM pending_changes")
    suspend fun clearPending()

    // Kind strings mirror RecordKind.
    @Query("INSERT INTO pending_changes (kind, uid) SELECT 'currency', uid FROM currencies")
    suspend fun enqueueAllCurrencies()

    @Query("INSERT INTO pending_changes (kind, uid) SELECT 'group', uid FROM account_groups")
    suspend fun enqueueAllGroups()

    @Query("INSERT INTO pending_changes (kind, uid) SELECT 'account', uid FROM accounts")
    suspend fun enqueueAllAccounts()

    @Query("INSERT INTO pending_changes (kind, uid) SELECT 'category', uid FROM categories")
    suspend fun enqueueAllCategories()

    @Query("INSERT INTO pending_changes (kind, uid) SELECT 'tx', uid FROM transactions ORDER BY date")
    suspend fun enqueueAllTransactions()

    @Query("INSERT INTO pending_changes (kind, uid) SELECT 'card', uid FROM bank_cards")
    suspend fun enqueueAllCards()

    @Query("INSERT INTO pending_changes (kind, uid) SELECT 'register', uid FROM cash_registers")
    suspend fun enqueueAllRegisters()

    @Query("SELECT `offset` FROM sync_offsets WHERE fileName = :fileName")
    suspend fun offset(fileName: String): Long?

    @Upsert suspend fun setOffset(item: SyncOffsetEntity)

    @Query("DELETE FROM sync_offsets")
    suspend fun clearOffsets()

    @Query("SELECT * FROM sync_devices WHERE deviceId = :deviceId")
    suspend fun device(deviceId: String): SyncDeviceEntity?

    @Upsert suspend fun upsertDevice(item: SyncDeviceEntity)

    @Query("SELECT * FROM sync_devices ORDER BY name")
    fun observeDevices(): Flow<List<SyncDeviceEntity>>

    // Records by uid, for the outbox and for conflict checks. Callers keep lists under SQLite's variable limit.
    @Query("SELECT * FROM currencies WHERE uid IN (:uids)") suspend fun currencies(uids: List<String>): List<CurrencyEntity>
    @Query("SELECT * FROM account_groups WHERE uid IN (:uids)") suspend fun groups(uids: List<String>): List<AccountGroupEntity>
    @Query("SELECT * FROM accounts WHERE uid IN (:uids)") suspend fun accounts(uids: List<String>): List<AccountEntity>
    @Query("SELECT * FROM categories WHERE uid IN (:uids)") suspend fun categories(uids: List<String>): List<CategoryEntity>
    @Query("SELECT * FROM transactions WHERE uid IN (:uids)") suspend fun transactions(uids: List<String>): List<TransactionEntity>
}
