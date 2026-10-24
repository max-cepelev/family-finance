package com.familyfinance.data.repository

import androidx.room.withTransaction
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.toDomain
import com.familyfinance.data.db.toEntity
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.AccountWithBalance
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomAccountRepository @Inject constructor(
    private val db: FinanceDatabase,
    private val device: DeviceIdentity,
    private val changes: LocalChanges,
) : AccountRepository {

    override fun observeGroups(): Flow<List<AccountGroup>> =
        db.accountGroupDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeAccounts(): Flow<List<Account>> =
        db.accountDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>> =
        combine(db.accountDao().observeAll(), db.transactionDao().observeBalances()) { accounts, balances ->
            val byUid = balances.associate { it.uid to it.balance }
            accounts.map { AccountWithBalance(it.toDomain(), byUid[it.uid] ?: 0L) }
        }

    override fun observeCurrencies(): Flow<List<Currency>> =
        db.currencyDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun saveAccount(account: Account) = db.withTransaction {
        val stamped = account.copy(
            updatedAt = nextVersion(db.accountDao().get(account.uid)?.updatedAt),
            deviceId = device.deviceId,
        )
        db.accountDao().upsert(stamped.toEntity())
        changes.record(stamped)
    }

    override suspend fun saveGroup(group: AccountGroup) = db.withTransaction {
        val stamped = group.copy(
            updatedAt = nextVersion(db.accountGroupDao().get(group.uid)?.updatedAt),
            deviceId = device.deviceId,
        )
        db.accountGroupDao().upsert(stamped.toEntity())
        changes.record(stamped)
    }

    override suspend fun saveCurrency(currency: Currency) = db.withTransaction {
        val stamped = currency.copy(
            updatedAt = nextVersion(db.currencyDao().get(currency.uid)?.updatedAt),
            deviceId = device.deviceId,
        )
        db.currencyDao().upsert(stamped.toEntity())
        changes.record(stamped)
    }

    override suspend fun countTransactions(accountUid: String): Int = db.transactionDao().countForAccount(accountUid)

    override suspend fun deleteAccount(uid: String): Boolean = db.withTransaction {
        val stored = db.accountDao().get(uid) ?: return@withTransaction false
        if (db.transactionDao().countForAccount(uid) > 0) return@withTransaction false
        // A tombstone, so the other phone deletes it too.
        val deleted = stored.copy(updatedAt = nextVersion(stored.updatedAt), deleted = true, deviceId = device.deviceId)
        db.accountDao().upsert(deleted)
        changes.record(deleted.toDomain())
        true
    }
}
