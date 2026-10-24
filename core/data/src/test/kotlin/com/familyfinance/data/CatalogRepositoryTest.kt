package com.familyfinance.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.data.repository.RoomAccountRepository
import com.familyfinance.data.repository.RoomCategoryRepository
import com.familyfinance.data.repository.RoomTransactionRepository
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.sync.LogEntry
import com.familyfinance.domain.sync.SyncLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Accounts and categories edited by the user: guards against losing history, merging, sync of the changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogRepositoryTest {
    private lateinit var db: FinanceDatabase
    private lateinit var accounts: RoomAccountRepository
    private lateinit var categories: RoomCategoryRepository
    private lateinit var transactions: RoomTransactionRepository
    private val me = object : DeviceIdentity { override val deviceId = "me" }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FinanceDatabase::class.java)
            .allowMainThreadQueries().build()
        val changes = LocalChanges(db)
        accounts = RoomAccountRepository(db, me, changes)
        categories = RoomCategoryRepository(db, me, changes)
        transactions = RoomTransactionRepository(db, me, changes)
    }

    @After fun tearDown() = db.close()

    private fun category(uid: String, parent: String? = null, icon: String? = null) =
        Category(uid, parent, uid, CategoryType.EXPENSE, 0, updatedAt = 0, deviceId = "", icon = icon)

    private fun expense(uid: String, category: String, account: String = "card") = Transaction(
        uid = uid, type = TransactionType.EXPENSE, date = 1, amount = 100, currencyCode = "RUB",
        accountUid = account, accountAmount = 100, categoryUid = category, updatedAt = 0, deviceId = "",
    )

    @Test fun `merge moves operations, hides the source and queues everything for sync`() = runTest {
        categories.save(category("food"))
        categories.save(category("snacks", parent = "food"))
        categories.save(category("snacks2", parent = "food"))
        listOf("t1", "t2", "t3").forEach { transactions.save(expense(it, "snacks2")) }
        transactions.save(expense("t4", "snacks"))
        db.syncDao().clearPending()

        assertEquals(3, categories.merge(fromUid = "snacks2", toUid = "snacks"))

        assertEquals(mapOf("snacks" to 4), categories.observeUsage().first())
        assertTrue(db.categoryDao().get("snacks2")!!.archived)
        // Three moved operations and the hidden category go to the other phone.
        assertEquals(4, db.syncDao().pending(100).size)
    }

    @Test fun `only empty categories and accounts can be deleted`() = runTest {
        categories.save(category("food"))
        categories.save(category("lunch", parent = "food"))
        categories.save(category("unused"))
        transactions.save(expense("t1", "lunch"))
        accounts.saveAccount(Account("card", null, "Карта", "RUB", 0, updatedAt = 0, deviceId = ""))
        accounts.saveAccount(Account("empty", null, "Пустой", "RUB", 1, updatedAt = 0, deviceId = ""))

        assertFalse(categories.delete("lunch"))   // has an operation
        assertFalse(categories.delete("food"))    // has a subcategory
        assertTrue(categories.delete("unused"))
        assertTrue(db.categoryDao().get("unused")!!.deleted)

        assertFalse(accounts.deleteAccount("card"))
        assertEquals(1, accounts.countTransactions("card"))
        assertTrue(accounts.deleteAccount("empty"))
        assertEquals(listOf("card"), accounts.observeAccounts().first().map { it.uid })
    }

    @Test fun `emoji survives the sync log`() {
        val line = SyncLog.encode(category("food", icon = "🍽️"))
        assertEquals("🍽️", (SyncLog.decode(line).toRecord() as Category).icon)
        // A log written before emoji existed still reads.
        val old = """{"t":"category","uid":"x","name":"Еда","type":"EXPENSE","sortOrder":0,"archived":false,"updatedAt":1,"deleted":false,"deviceId":"d"}"""
        assertEquals(null, (SyncLog.decode(old) as LogEntry.CategoryRecord).icon)
    }
}
