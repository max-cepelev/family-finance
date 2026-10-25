package com.familyfinance.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BankDao {
    @Query("SELECT * FROM bank_cards WHERE deleted = 0 ORDER BY bank, last4 IS NULL, last4")
    fun observeCards(): Flow<List<BankCardEntity>>

    @Query("SELECT * FROM bank_cards WHERE deleted = 0")
    suspend fun cards(): List<BankCardEntity>

    @Query("SELECT * FROM bank_cards WHERE uid = :uid") suspend fun card(uid: String): BankCardEntity?
    @Upsert suspend fun upsertCard(item: BankCardEntity)
    @Upsert suspend fun upsertCards(items: List<BankCardEntity>)
    @Query("SELECT uid, updatedAt, deviceId FROM bank_cards") suspend fun versions(): List<VersionRow>
    @Query("SELECT * FROM bank_cards WHERE uid IN (:uids)") suspend fun cardsByUid(uids: List<String>): List<BankCardEntity>

    /** False when the key was already there: the notification was handled before. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun markSeen(item: PushSeenEntity): Long

    @Query("DELETE FROM push_seen WHERE seenAt < :before") suspend fun forgetSeenBefore(before: Long)

    @Upsert suspend fun upsertUnknown(item: UnknownCardEntity)
    @Query("DELETE FROM push_unknown_cards WHERE bank = :bank AND last4 = :last4") suspend fun forgetUnknown(bank: String, last4: String)
    @Query("SELECT * FROM push_unknown_cards ORDER BY lastSeenAt DESC") fun observeUnknown(): Flow<List<UnknownCardEntity>>
}
