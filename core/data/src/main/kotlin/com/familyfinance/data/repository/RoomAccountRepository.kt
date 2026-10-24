package com.familyfinance.data.repository

import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.toDomain
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.AccountWithBalance
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomAccountRepository @Inject constructor(
    private val db: FinanceDatabase,
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
}

class RoomCategoryRepository @Inject constructor(
    private val db: FinanceDatabase,
) : CategoryRepository {
    override fun observeAll(): Flow<List<Category>> =
        db.categoryDao().observeAll().map { rows -> rows.map { it.toDomain() } }
}
