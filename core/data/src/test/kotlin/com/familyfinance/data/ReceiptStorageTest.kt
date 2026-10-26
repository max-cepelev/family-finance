package com.familyfinance.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.data.repository.RoomCashRegisterRepository
import com.familyfinance.data.repository.RoomTransactionRepository
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.data.sync.RoomSyncStore
import com.familyfinance.domain.model.CashRegister
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.sync.LogEntry
import com.familyfinance.domain.sync.RemoteBatch
import com.familyfinance.domain.sync.SyncLog
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Receipts attached to operations and registers remembered by their fiscal number. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceiptStorageTest {
    private lateinit var db: FinanceDatabase
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var registers: RoomCashRegisterRepository
    private val me = object : DeviceIdentity { override val deviceId = "me" }
    private val at = 1_800_000_000_000L
    private val minute = 60_000L
    private val receipt = "t=20270115T0800&s=392.97&fn=7380440700076549&i=12345&fp=1234567890&n=1"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FinanceDatabase::class.java)
            .allowMainThreadQueries().build()
        val changes = LocalChanges(db)
        transactions = RoomTransactionRepository(db, me, changes)
        registers = RoomCashRegisterRepository(db, me, changes)
    }

    @After fun tearDown() = db.close()

    private fun expense(uid: String, amount: Long, date: Long, source: String? = Transaction.SOURCE_PUSH) =
        Transaction(uid, TransactionType.EXPENSE, date, amount, "RUB", "acc", amount, note = "Magnit", source = source, updatedAt = 0, deviceId = "")

    private suspend fun candidates(amount: Long = 39297) =
        transactions.receiptCandidates(TransactionType.EXPENSE, amount, at, at - 10 * minute, at + 10 * minute).map { it.uid }

    @Test fun `receipt matches the closest operation with the same amount and is attached once`() = runTest {
        transactions.save(expense("far", 39297, at + 8 * minute))
        transactions.save(expense("near", 39297, at + minute))
        transactions.save(expense("other-sum", 39300, at))
        transactions.save(expense("too-late", 39297, at + 30 * minute))

        assertEquals(listOf("near", "far"), candidates())

        val near = transactions.get("near")!!
        transactions.save(near.copy(receipt = receipt))
        val attached = transactions.findByReceipt(receipt)!!
        assertEquals("near", attached.uid)
        // Attaching keeps where the operation came from.
        assertEquals(Transaction.SOURCE_PUSH, attached.source)
        assertEquals(listOf("far"), candidates())
    }

    @Test fun `deleted operation releases its receipt`() = runTest {
        transactions.save(expense("tx", 39297, at).copy(receipt = receipt))
        transactions.delete("tx")
        assertNull(transactions.findByReceipt(receipt))
    }

    @Test fun `register is remembered and synced like other records`() = runTest {
        registers.save(CashRegister("7380440700076549", "Лента", "food", updatedAt = 0, deviceId = ""))
        assertEquals("Лента", registers.get("7380440700076549")!!.payee)

        val store = RoomSyncStore(db)
        val outgoing = store.pendingChanges(10).map { it.record }
        assertTrue(outgoing.any { it is CashRegister && it.uid == "7380440700076549" })

        // The other phone renamed the shop later: its version wins.
        val line = SyncLog.encode(CashRegister("7380440700076549", "Лента Гипер", "food", updatedAt = Long.MAX_VALUE / 2, deviceId = "wife"))
        val record = (SyncLog.decode(line) as LogEntry.CashRegisterRecord).toRecord()!!
        store.applyRemote(RemoteBatch("familyfinance-wife.jsonl", "wife", "Wife", listOf(record), newOffset = line.length.toLong()))
        assertEquals("Лента Гипер", registers.get("7380440700076549")!!.payee)
    }

    @Test fun `receipt travels with the operation in the sync log`() {
        val tx = expense("tx", 39297, at).copy(receipt = receipt, updatedAt = 5, deviceId = "me")
        val back = SyncLog.decode(SyncLog.encode(tx)).toRecord() as Transaction
        assertEquals(receipt, back.receipt)
        assertEquals(Transaction.SOURCE_PUSH, back.source)
    }
}
