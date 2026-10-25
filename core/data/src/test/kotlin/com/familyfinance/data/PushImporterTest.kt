package com.familyfinance.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.data.push.PushImporter
import com.familyfinance.data.push.PushResult
import com.familyfinance.data.repository.RoomAccountRepository
import com.familyfinance.data.repository.RoomBankCardRepository
import com.familyfinance.data.repository.RoomTransactionRepository
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.domain.bank.Bank
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.BankCard
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real notification texts (docs/bank-formats.md) turned into operations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PushImporterTest {
    private lateinit var db: FinanceDatabase
    private lateinit var importer: PushImporter
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var cards: RoomBankCardRepository
    private val me = object : DeviceIdentity { override val deviceId = "me" }
    private val now = 1_800_000_000_000L

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FinanceDatabase::class.java)
            .allowMainThreadQueries().build()
        val changes = LocalChanges(db)
        transactions = RoomTransactionRepository(db, me, changes)
        cards = RoomBankCardRepository(db, me, changes)
        importer = PushImporter(db, transactions)
        val accounts = RoomAccountRepository(db, me, changes)
        listOf("sber-me", "sber-wife", "sber-default").forEachIndexed { i, uid ->
            accounts.saveAccount(Account(uid, null, uid, "RUB", i, updatedAt = 0, deviceId = ""))
        }
        cards.save(BankCard("c1", Bank.SBER.code, "2824", "sber-me", 0, deviceId = ""))
        cards.save(BankCard("c2", Bank.SBER.code, "1111", "sber-wife", 0, deviceId = ""))
        cards.save(BankCard("c3", Bank.SBER.code, null, "sber-default", 0, deviceId = ""))
    }

    @After fun tearDown() = db.close()

    @Test fun `card picks the account, repeat is ignored, category comes from the same payee`() = runTest {
        transactions.save(
            Transaction("old", TransactionType.EXPENSE, 1, 5000, "RUB", "sber-me", 5000, categoryUid = "clothes", note = "O'STIN", updatedAt = 0, deviceId = ""),
        )
        val first = importer.handle(Bank.SBER, "➖ 1 349,50 ₽ O'STIN", "💳 12 000 ₽ •• 2824", now) as PushResult.Recorded
        assertEquals("sber-me", first.transaction.accountUid)
        assertEquals(134950L, first.transaction.amount)
        assertEquals("clothes", first.transaction.categoryUid)
        assertEquals(Transaction.SOURCE_PUSH, first.transaction.source)

        assertEquals(PushResult.Duplicate, importer.handle(Bank.SBER, "➖ 1 349,50 ₽ O'STIN", "💳 12 000 ₽ •• 2824", now + 5_000))

        val wife = importer.handle(Bank.SBER, "➕ 10 ₽ по СБП от ИВАН П.", "💳 10 ₽ •• 1111", now) as PushResult.Recorded
        assertEquals("sber-wife", wife.transaction.accountUid)
        assertEquals(TransactionType.INCOME, wife.transaction.type)
    }

    @Test fun `unknown card goes to the fallback and asks to be linked`() = runTest {
        val r = importer.handle(Bank.SBER, "➖ 10 ₽ Райффайзенбанк", "💳 0 ₽ •• 7777", now) as PushResult.Recorded
        assertEquals("sber-default", r.transaction.accountUid)
        assertTrue(r.unknownCard)
        assertEquals(listOf("7777"), db.bankDao().observeUnknown().first().map { it.last4 })
        // Uncategorized operations from notifications are offered for review.
        assertEquals(listOf(r.transaction.uid), transactions.observeUncategorizedFromPush().first())

        cards.save(BankCard("c4", Bank.SBER.code, "7777", "sber-me", 0, deviceId = ""))
        assertEquals(emptyList<Any>(), db.bankDao().observeUnknown().first())
    }

    @Test fun `codes are ignored, a bank without links records nothing`() = runTest {
        assertEquals(PushResult.Ignored, importer.handle(Bank.TBANK, "Т-Банк", "Никому не говорите код 2286!", now))
        assertTrue(importer.handle(Bank.RAIF, "Заплатили картой *5790", "- 17.00 ₽ в MARMALATO 086. Теперь на карте 14 025.10 ₽", now) is PushResult.NoAccount)
        assertEquals(0, db.transactionDao().observeCount().first())
    }
}
