package com.familyfinance.web

import com.familyfinance.domain.model.*
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Synthetic, in-memory data for web tests and the optional local browser preview. */
internal class WebTestStore {
    val accountList = mutableListOf(
        Account("card", "bank", "Карта", "RUB", 0, updatedAt = 0, deviceId = "test"),
        Account("cash", null, "Наличные", "RUB", 1, updatedAt = 0, deviceId = "test"),
        Account("hidden", null, "Скрытый счёт", "RUB", 2, hidden = true, updatedAt = 0, deviceId = "test"),
    )
    val categoryList = mutableListOf(
        Category("food", null, "Питание", CategoryType.EXPENSE, 0, updatedAt = 0, deviceId = "test", icon = "🛒"),
        Category("groceries", "food", "Продукты", CategoryType.EXPENSE, 1, updatedAt = 0, deviceId = "test"),
        Category("cafe", "food", "Кафе", CategoryType.EXPENSE, 2, updatedAt = 0, deviceId = "test"),
        Category("salary", null, "Зарплата", CategoryType.INCOME, 3, updatedAt = 0, deviceId = "test"),
    )
    val items = mutableListOf(
        transaction("salary-tx", TransactionType.INCOME, 100000, "card", "salary", "Зарплата за октябрь"),
        transaction("shop-tx", TransactionType.EXPENSE, 12543, "card", "groceries", "Магазин; у дома"),
        transaction("cafe-tx", TransactionType.EXPENSE, 2500, "cash", "cafe", "Кофе"),
        transaction("transfer-tx", TransactionType.TRANSFER, 20000, "card", null, "Снятие наличных").copy(toAccountUid = "cash", toAccountAmount = 20000),
        transaction("hidden-tx", TransactionType.INCOME, 500000, "hidden", null, "Скрытые накопления"),
    )

    val accounts = object : AccountRepository {
        override fun observeAccounts() = flowOf(accountList.toList())
        override fun observeGroups() = flowOf(listOf(AccountGroup("bank", "Банки", 0, updatedAt = 0, deviceId = "test")))
        override fun observeCurrencies() = flowOf(listOf(Currency("RUB", "Рубль", "₽", 2, true, 1.0, updatedAt = 0, deviceId = "test")))
        override fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>> {
            val balances = Balances.byAccount(items)
            return flowOf(accountList.filterNot { it.deleted }.map { AccountWithBalance(it, balances[it.uid] ?: 0) })
        }
        override suspend fun saveAccount(account: Account) { accountList.removeAll { it.uid == account.uid }; accountList += account }
        override suspend fun saveGroup(group: AccountGroup) = error("Not used in web tests")
        override suspend fun saveCurrency(currency: Currency) = error("Not used in web tests")
        override suspend fun countTransactions(accountUid: String) = items.count { !it.deleted && (it.accountUid == accountUid || it.toAccountUid == accountUid) }
        override suspend fun deleteAccount(uid: String): Boolean = error("Not used in web tests")
    }
    val categories = object : CategoryRepository {
        override fun observeAll() = flowOf(categoryList.filterNot { it.deleted })
        override fun observeUsage() = flowOf(items.filterNot { it.deleted }.mapNotNull { it.categoryUid }.groupingBy { it }.eachCount())
        override suspend fun save(category: Category) { categoryList.removeAll { it.uid == category.uid }; categoryList += category }
        override suspend fun delete(uid: String): Boolean {
            if (items.any { !it.deleted && it.categoryUid == uid } || categoryList.any { !it.deleted && it.parentUid == uid }) return false
            return categoryList.removeAll { it.uid == uid }
        }
        override suspend fun merge(fromUid: String, toUid: String): Int = error("Not used in web tests")
    }
    val transactions = object : TransactionRepository {
        override fun observeCount() = flowOf(items.count { !it.deleted })
        override fun observeBetween(from: Long, to: Long) = flowOf(items.filter { !it.deleted && it.date >= from && it.date < to }.sortedByDescending { it.date })
        override suspend fun get(uid: String) = items.firstOrNull { it.uid == uid && !it.deleted }
        override suspend fun save(transaction: Transaction) { items.removeAll { it.uid == transaction.uid }; items += transaction }
        override suspend fun saveAll(transactions: List<Transaction>, deleteUids: List<String>) { deleteUids.forEach { delete(it) }; transactions.forEach { save(it) } }
        override suspend fun delete(uid: String) { items.removeAll { it.uid == uid } }
        override suspend fun splitParts(splitUid: String) = items.filter { !it.deleted && it.splitUid == splitUid }.sortedBy { it.splitIndex }
        override suspend fun firstDate() = items.minOfOrNull { it.date }
        override suspend fun lastUsedAccountUid() = "card"
        override suspend fun knownExternalIds(ids: List<String>): Set<String> = error("Not used in web tests")
        override suspend fun statementCandidates(accountUid: String, type: TransactionType, amount: Long, at: Long, from: Long, to: Long): List<Transaction> = error("Not used in web tests")
        override suspend fun findSame(accountUid: String, type: TransactionType, amount: Long, from: Long, to: Long): List<Transaction> = error("Not used in web tests")
        override fun observeUncategorizedFromPush(): Flow<List<String>> = error("Not used in web tests")
        override suspend fun categoryForNote(type: TransactionType, note: String): String? = error("Not used in web tests")
        override suspend fun frequentCategoryUids(type: TransactionType, limit: Int): List<String> = error("Not used in web tests")
        override suspend fun findByReceipt(receipt: String): Transaction? = error("Not used in web tests")
        override suspend fun receiptCandidates(type: TransactionType, amount: Long, at: Long, from: Long, to: Long): List<Transaction> = error("Not used in web tests")
    }

    val data = RepositoryWebData(transactions, accounts, categories)
    val edits = RepositoryWebEdits(transactions, accounts, categories)
    val sessions = object : WebSessions {
        override fun login(code: String, userAgent: String?) = null
        override fun check(token: String?) = token == "web-test"
    }

    private fun transaction(uid: String, type: TransactionType, amount: Long, account: String, category: String?, note: String) = Transaction(
        uid = uid, type = type, date = LocalDateTime.of(2026, 10, 26, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        amount = amount, currencyCode = "RUB", accountUid = account, accountAmount = amount, categoryUid = category,
        note = note, updatedAt = 0, deviceId = "test",
    )
}
