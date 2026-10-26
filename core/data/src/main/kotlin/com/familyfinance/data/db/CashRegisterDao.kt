package com.familyfinance.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CashRegisterDao {
    @Query("SELECT * FROM cash_registers WHERE uid = :uid") suspend fun get(uid: String): CashRegisterEntity?
    @Upsert suspend fun upsert(item: CashRegisterEntity)
    @Upsert suspend fun upsert(items: List<CashRegisterEntity>)
    @Query("SELECT * FROM cash_registers WHERE uid IN (:uids)") suspend fun byUid(uids: List<String>): List<CashRegisterEntity>
}
