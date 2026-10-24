package com.familyfinance.data.repository

import androidx.room.withTransaction
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.toDomain
import com.familyfinance.data.db.toEntity
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomTransactionRepository @Inject constructor(
    private val db: FinanceDatabase,
    private val device: DeviceIdentity,
    private val changes: LocalChanges,
) : TransactionRepository {

    private val dao get() = db.transactionDao()

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override fun observeBetween(from: Long, to: Long): Flow<List<Transaction>> =
        dao.observeBetween(from, to).map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(uid: String): Transaction? = dao.get(uid)?.toDomain()

    override suspend fun save(transaction: Transaction) = db.withTransaction {
        val previous = dao.get(transaction.uid)
        val stamped = transaction.copy(
            updatedAt = nextVersion(previous?.updatedAt),
            deleted = false,
            deviceId = device.deviceId,
        )
        dao.upsert(stamped.toEntity())
        changes.record(stamped)
    }

    override suspend fun delete(uid: String) = db.withTransaction {
        val previous = dao.get(uid) ?: return@withTransaction
        val deleted = previous.copy(updatedAt = nextVersion(previous.updatedAt), deleted = true, deviceId = device.deviceId)
        dao.upsert(deleted)
        changes.record(deleted.toDomain())
    }

    override suspend fun lastUsedAccountUid(): String? = dao.lastUsedAccountUid()

    override suspend fun firstDate(): Long? = dao.firstDate()

    override suspend fun frequentCategoryUids(type: TransactionType, limit: Int): List<String> =
        dao.frequentCategoryUids(type, limit, window = FREQUENT_WINDOW)


    private companion object {
        /** Recent operations considered for "frequent categories". */
        const val FREQUENT_WINDOW = 300
    }
}
