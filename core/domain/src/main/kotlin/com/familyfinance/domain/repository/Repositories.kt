package com.familyfinance.domain.repository

import com.familyfinance.domain.importer.ImportBundle
import com.familyfinance.domain.importer.ImportResult
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.AccountWithBalance
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeGroups(): Flow<List<AccountGroup>>
    fun observeAccounts(): Flow<List<Account>>
    fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>>
    fun observeCurrencies(): Flow<List<Currency>>

    // Writes stamp updatedAt/deviceId themselves and queue the change for sync.
    suspend fun saveAccount(account: Account)
    suspend fun saveGroup(group: AccountGroup)
    suspend fun saveCurrency(currency: Currency)

    /** Operations on the account, either side of a transfer included. */
    suspend fun countTransactions(accountUid: String): Int

    /** Deletes an account without operations; false (and nothing changed) when it has some. */
    suspend fun deleteAccount(uid: String): Boolean
}

interface TransactionRepository {
    fun observeCount(): Flow<Int>

    /** Not deleted transactions with `from <= date < to`, newest first. */
    fun observeBetween(from: Long, to: Long): Flow<List<Transaction>>

    suspend fun get(uid: String): Transaction?

    /**
     * Inserts or updates. The repository stamps [Transaction.updatedAt] and [Transaction.deviceId],
     * so whatever the caller put there is ignored.
     */
    suspend fun save(transaction: Transaction)

    /** Soft delete, so the deletion can be synced. */
    suspend fun delete(uid: String)

    /** Date of the oldest transaction, epoch millis; null when there are none. */
    suspend fun firstDate(): Long?

    /** Account of the newest transaction, a sensible default for a new one. */
    suspend fun lastUsedAccountUid(): String?

    /** Categories used most often recently for [type], most frequent first. */
    suspend fun frequentCategoryUids(type: TransactionType, limit: Int): List<String>
}

interface CategoryRepository {
    /** Including archived categories: old transactions still show them. */
    fun observeAll(): Flow<List<Category>>

    /** Category uid -> number of operations booked on it (subcategories counted separately). */
    fun observeUsage(): Flow<Map<String, Int>>

    /** Stamps updatedAt/deviceId and queues the change for sync. */
    suspend fun save(category: Category)

    /** Deletes a category without operations and subcategories; false when it is not empty. */
    suspend fun delete(uid: String): Boolean

    /**
     * Moves every operation of [fromUid] to [toUid] and hides [fromUid]; returns how many moved.
     * Only for a category without subcategories.
     */
    suspend fun merge(fromUid: String, toUid: String): Int
}

/** Writes an imported snapshot into local storage, merging by last-writer-wins. */
interface ImportRepository {
    suspend fun apply(bundle: ImportBundle): ImportResult
}
