package com.familyfinance.data.repository

import androidx.room.withTransaction
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.toDomain
import com.familyfinance.data.db.toEntity
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.domain.model.BankCard
import com.familyfinance.domain.repository.BankCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomBankCardRepository @Inject constructor(
    private val db: FinanceDatabase,
    private val device: DeviceIdentity,
    private val changes: LocalChanges,
) : BankCardRepository {
    private val dao get() = db.bankDao()

    override fun observeAll(): Flow<List<BankCard>> = dao.observeCards().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(card: BankCard) = db.withTransaction {
        val stamped = card.copy(updatedAt = nextVersion(dao.card(card.uid)?.updatedAt), deviceId = device.deviceId)
        dao.upsertCard(stamped.toEntity())
        // A linked card no longer needs the "Привязать" hint.
        stamped.last4?.let { dao.forgetUnknown(stamped.bank, it) }
        changes.record(stamped)
    }

    override suspend fun delete(uid: String) = db.withTransaction {
        val stored = dao.card(uid) ?: return@withTransaction
        val deleted = stored.copy(updatedAt = nextVersion(stored.updatedAt), deleted = true, deviceId = device.deviceId)
        dao.upsertCard(deleted)
        changes.record(deleted.toDomain())
    }
}
