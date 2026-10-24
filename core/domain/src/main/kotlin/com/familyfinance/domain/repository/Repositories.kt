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
}

/** Writes an imported snapshot into local storage, merging by last-writer-wins. */
interface ImportRepository {
    suspend fun apply(bundle: ImportBundle): ImportResult
}
