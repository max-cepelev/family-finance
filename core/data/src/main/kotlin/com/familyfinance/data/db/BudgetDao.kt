package com.familyfinance.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE deleted = 0") fun observeAll(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets WHERE uid = :uid") suspend fun get(uid: String): BudgetEntity?
    @Upsert suspend fun upsert(item: BudgetEntity)
    @Upsert suspend fun upsert(items: List<BudgetEntity>)
    @Query("SELECT * FROM budgets WHERE uid IN (:uids)") suspend fun byUid(uids: List<String>): List<BudgetEntity>
}
