package com.familyfinance.data.repository

import androidx.room.withTransaction
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.toDomain
import com.familyfinance.data.db.toEntity
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.domain.model.CashRegister
import com.familyfinance.domain.repository.CashRegisterRepository
import javax.inject.Inject

class RoomCashRegisterRepository @Inject constructor(
    private val db: FinanceDatabase,
    private val device: DeviceIdentity,
    private val changes: LocalChanges,
) : CashRegisterRepository {
    private val dao get() = db.cashRegisterDao()

    override suspend fun get(fn: String): CashRegister? = dao.get(fn)?.toDomain()?.takeIf { !it.deleted }

    override suspend fun save(register: CashRegister) = db.withTransaction {
        val stamped = register.copy(updatedAt = nextVersion(dao.get(register.uid)?.updatedAt), deleted = false, deviceId = device.deviceId)
        dao.upsert(stamped.toEntity())
        changes.record(stamped)
    }
}
