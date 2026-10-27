package com.familyfinance.web

import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.AccountWithBalance
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.transaction.DraftError
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebEditTest {
    /** Keeps operations in a map; what the editor does not call is left out. */
    private class Transactions(vararg stored: Transaction) : TransactionRepository {
        val byUid = stored.associateBy { it.uid }.toMutableMap()
        val deleted = mutableListOf<String>()
        override suspend fun get(uid: String) = byUid[uid]
        override suspend fun splitParts(splitUid: String) = byUid.values.filter { it.splitUid == splitUid }.sortedBy { it.splitIndex }
        override suspend fun saveAll(transactions: List<Transaction>, deleteUids: List<String>) {
            transactions.forEach { byUid[it.uid] = it }
            deleteUids.forEach { byUid.remove(it); deleted += it }
        }
        override suspend fun delete(uid: String) { byUid.remove(uid); deleted += uid }
        override suspend fun lastUsedAccountUid() = "card"
        override fun observeCount(): Flow<Int> = TODO()
        override fun observeBetween(from: Long, to: Long): Flow<List<Transaction>> = TODO()
        override suspend fun save(transaction: Transaction) = TODO()
        override suspend fun knownExternalIds(ids: List<String>): Set<String> = TODO()
        override suspend fun statementCandidates(accountUid: String, type: TransactionType, amount: Long, at: Long, from: Long, to: Long): List<Transaction> = TODO()
        override suspend fun findSame(accountUid: String, type: TransactionType, amount: Long, from: Long, to: Long): List<Transaction> = TODO()
        override suspend fun firstDate(): Long? = TODO()
        override fun observeUncategorizedFromPush(): Flow<List<String>> = TODO()
        override suspend fun categoryForNote(type: TransactionType, note: String): String? = TODO()
        override suspend fun frequentCategoryUids(type: TransactionType, limit: Int): List<String> = TODO()
        override suspend fun findByReceipt(receipt: String): Transaction? = TODO()
        override suspend fun receiptCandidates(type: TransactionType, amount: Long, at: Long, from: Long, to: Long): List<Transaction> = TODO()
    }

    private val accounts = object : AccountRepository {
        override fun observeAccounts() = flowOf(listOf(account("card", "Карта Райффайзен"), account("cash", "Наличные")))
        override fun observeCurrencies() = flowOf(listOf(Currency("RUB", "Рубль", "₽", 2, true, 1.0, updatedAt = 0, deviceId = "d")))
        override fun observeGroups(): Flow<List<AccountGroup>> = TODO()
        override fun observeAccountsWithBalance(): Flow<List<AccountWithBalance>> = TODO()
        override suspend fun saveAccount(account: Account) = TODO()
        override suspend fun saveGroup(group: AccountGroup) = TODO()
        override suspend fun saveCurrency(currency: Currency) = TODO()
        override suspend fun countTransactions(accountUid: String): Int = TODO()
        override suspend fun deleteAccount(uid: String): Boolean = TODO()
    }

    private val categories = object : CategoryRepository {
        override fun observeAll() = flowOf(
            listOf(
                category("food", null, "Питание"),
                category("groceries", "food", "Продукты"),
                category("cafe", "food", "Кафе"),
                category("salary", null, "Зарплата", CategoryType.INCOME),
            ),
        )
        override fun observeUsage(): Flow<Map<String, Int>> = TODO()
        override suspend fun save(category: Category) = TODO()
        override suspend fun delete(uid: String): Boolean = TODO()
        override suspend fun merge(fromUid: String, toUid: String): Int = TODO()
    }

    private val zone = ZoneId.systemDefault()
    private val pushed = Transaction(
        uid = "t1", type = TransactionType.EXPENSE,
        date = LocalDateTime.of(2026, 10, 27, 9, 15, 42).atZone(zone).toInstant().toEpochMilli(),
        amount = 4299, currencyCode = "RUB", accountUid = "card", accountAmount = 4299, categoryUid = "groceries",
        note = "К&Б", updatedAt = 1, deviceId = "phone", source = Transaction.SOURCE_PUSH, receipt = "t=20261027T0915&s=42.99&fn=7380440800000001&i=1&fp=2&n=1",
    )

    private fun fields(vararg pairs: Pair<String, String>): WebFields = pairs.groupBy({ it.first }, { it.second })

    @Test fun `a new expense is saved as typed`() = runTest {
        val transactions = Transactions()
        val edits = RepositoryWebEdits(transactions, accounts, categories)
        val form = edits.open(null, YearMonth.of(2026, 10))
        assertEquals("card", form.account)
        assertEquals(listOf("", "food", "groceries", "cafe"), form.categories.map { it.value })

        val errors = edits.save(
            fields("month" to "2026-10", "type" to "EXPENSE", "amount" to "1 254,30", "date" to "2026-10-26", "time" to "14:42",
                "account" to "cash", "category" to "groceries", "note" to " Лента "),
        )
        assertNull(errors)
        val saved = transactions.byUid.values.single()
        assertEquals(125430L, saved.amount)
        assertEquals("cash", saved.accountUid)
        assertEquals("groceries", saved.categoryUid)
        assertEquals("Лента", saved.note)
        assertEquals(LocalDateTime.of(2026, 10, 26, 14, 42).atZone(zone).toInstant().toEpochMilli(), saved.date)
        assertNull(saved.source)
    }

    @Test fun `editing keeps what the browser cannot change`() = runTest {
        val transactions = Transactions(pushed)
        val edits = RepositoryWebEdits(transactions, accounts, categories)
        val form = edits.open("t1", YearMonth.of(2026, 10))
        assertEquals("09:15", form.time)
        assertEquals("Чек прикреплён · касса •• 0001", form.receipt)

        // The form shows minutes; untouched, the stored seconds stay. Source and receipt come from the phone.
        assertNull(
            edits.save(
                fields("uid" to "t1", "type" to "EXPENSE", "amount" to "45", "date" to form.date, "time" to form.time,
                    "account" to "card", "category" to "cafe", "note" to "К&Б"),
            ),
        )
        val saved = transactions.byUid.getValue("t1")
        assertEquals(4500L, saved.amount)
        assertEquals("cafe", saved.categoryUid)
        assertEquals(pushed.date, saved.date)
        assertEquals(Transaction.SOURCE_PUSH, saved.source)
        assertEquals(pushed.receipt, saved.receipt)
    }

    @Test fun `errors come back on the form and nothing is saved`() = runTest {
        val transactions = Transactions()
        val form = RepositoryWebEdits(transactions, accounts, categories)
            .save(fields("type" to "TRANSFER", "amount" to "", "account" to "card", "toAccount" to "card"))
        assertNotNull(form)
        assertEquals(setOf(DraftError.AMOUNT, DraftError.TO_ACCOUNT), form!!.errors)
        assertTrue(transactions.byUid.isEmpty())
    }

    @Test fun `switching type drops the category`() = runTest {
        val edits = RepositoryWebEdits(Transactions(pushed), accounts, categories)
        val form = edits.change(fields("uid" to "t1", "type" to "EXPENSE", "amount" to "42,99", "account" to "card", "category" to "groceries"), "type:INCOME")
        assertEquals(TransactionType.INCOME, form.type)
        assertEquals("", form.category)
        assertEquals(listOf("", "salary"), form.categories.map { it.value })
    }

    @Test fun `split from the browser and deleted as a whole`() = runTest {
        val transactions = Transactions(pushed)
        val edits = RepositoryWebEdits(transactions, accounts, categories)
        val base = arrayOf("uid" to "t1", "type" to "EXPENSE", "amount" to "42,99", "account" to "card", "note" to "К&Б")

        val split = edits.change(fields(*base, "category" to "groceries"), "split")
        assertEquals(listOf("groceries", ""), split.parts.map { it.category })

        val counted = edits.change(fields(*base, "partCategory" to "groceries", "partCategory" to "cafe", "partAmount" to "12,99"), null)
        assertEquals("30,00 ₽", counted.remainder.replace(' ', ' '))

        assertNull(edits.save(fields(*base, "partCategory" to "groceries", "partCategory" to "cafe", "partAmount" to "12,99")))
        val parts = transactions.splitParts("t1")
        assertEquals(listOf(3000L, 1299L), parts.map { it.amount })
        assertEquals(listOf("groceries", "cafe"), parts.map { it.categoryUid })

        edits.delete("t1")
        assertTrue(transactions.byUid.isEmpty())
    }

    private fun account(uid: String, name: String) = Account(uid, null, name, "RUB", 0, updatedAt = 0, deviceId = "d")

    private fun category(uid: String, parent: String?, name: String, type: CategoryType = CategoryType.EXPENSE) =
        Category(uid, parent, name, type, 0, updatedAt = 0, deviceId = "d")
}
