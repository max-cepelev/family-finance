package com.familyfinance.data.repository

import androidx.room.withTransaction
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.toDomain
import com.familyfinance.data.db.toEntity
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.domain.model.Budget
import com.familyfinance.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomBudgetRepository @Inject constructor(
    private val db: FinanceDatabase,
    private val device: DeviceIdentity,
    private val changes: LocalChanges,
) : BudgetRepository {
    private val dao get() = db.budgetDao()

    override fun observeAll(): Flow<List<Budget>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(budget: Budget) = db.withTransaction {
        val stamped = budget.copy(updatedAt = nextVersion(dao.get(budget.uid)?.updatedAt), deleted = false, deviceId = device.deviceId)
        dao.upsert(stamped.toEntity())
        changes.record(stamped)
    }

    override suspend fun delete(uid: String) = db.withTransaction {
        val stored = dao.get(uid) ?: return@withTransaction
        val deleted = stored.copy(updatedAt = nextVersion(stored.updatedAt), deleted = true, deviceId = device.deviceId)
        dao.upsert(deleted)
        changes.record(deleted.toDomain())
    }
}
