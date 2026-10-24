package com.familyfinance.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.data.mmbak.AndroidMmbakSource
import com.familyfinance.data.repository.RoomImportRepository
import com.familyfinance.data.repository.RoomTransactionRepository
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.data.sync.RoomSyncStore
import com.familyfinance.domain.importer.mmbak.MmbakReader
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.sync.DirectorySyncFolder
import com.familyfinance.domain.sync.SyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Two phones, each with its own Room database, syncing through one folder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSyncTest {
    @get:Rule val tmp = TemporaryFolder()

    private val phones = ArrayList<Phone>()

    private inner class Phone(val id: String, dir: File) {
        val db: FinanceDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FinanceDatabase::class.java)
            .allowMainThreadQueries().build()
        val changes = LocalChanges(db)
        val transactions = RoomTransactionRepository(db, object : DeviceIdentity { override val deviceId = id }, changes)
        val imports = RoomImportRepository(db, changes)
        val engine = SyncEngine(id, "Phone $id", DirectorySyncFolder(dir), RoomSyncStore(db))
        suspend fun sync() = engine.sync()
        suspend fun balances() = db.transactionDao().observeBalances().first().associate { it.uid to it.balance }.filterValues { it != 0L }
        init { phones += this }
    }

    @After fun tearDown() = phones.forEach { it.db.close() }

    private lateinit var dir: File

    private fun pair(): Pair<Phone, Phone> {
        dir = tmp.newFolder("sync")
        return Phone("aaaaaaaa-0000-0000-0000-000000000001", dir) to Phone("bbbbbbbb-0000-0000-0000-000000000002", dir)
    }

    private fun expense(uid: String, amount: Long) = Transaction(
        uid = uid, type = TransactionType.EXPENSE, date = 1000, amount = amount, currencyCode = "RUB",
        accountUid = "acc", accountAmount = amount, updatedAt = 0, deviceId = "",
    )

    @Test fun `edits and deletions reach the other phone and are not echoed`() = runTest {
        val (a, b) = pair()
        a.transactions.save(expense("t1", 100))
        a.sync()
        b.sync()
        assertEquals(100L, b.transactions.get("t1")!!.amount)

        b.transactions.save(b.transactions.get("t1")!!.copy(amount = 300, accountAmount = 300))
        b.sync()
        a.sync()
        assertEquals(300L, a.transactions.get("t1")!!.amount)

        a.transactions.delete("t1")
        a.sync()
        b.sync()
        assertTrue(b.db.transactionDao().get("t1")!!.deleted)

        // Nothing left to send: applied remote changes did not enter the outbox.
        assertEquals(0, a.sync().exported)
        assertEquals(0, b.sync().exported)
        assertEquals("Phone ${a.id}", b.db.syncDao().device(a.id)!!.name)
    }

    /** The whole imported backup travels through the log and gives identical balances. */
    @Test fun `real backup imported on one phone appears on the other`() = runTest {
        val path = System.getProperty("mmbak.path").orEmpty()
        assumeTrue("mmbak.path not set", path.isNotEmpty() && File(path).isFile)
        val (a, b) = pair()
        val bundle = AndroidMmbakSource(File(path)).use { MmbakReader(a.id).read(it) }
        a.imports.apply(bundle)

        val sent = a.sync()
        val got = b.sync()
        println("Отправлено: ${sent.exported}, получено: ${got.received}, применено: ${got.applied}")
        dir.listFiles()!!.forEach { println("  ${it.name}: ${it.length() / 1024} КБ") }
        assertEquals(sent.exported, got.received)
        assertEquals(got.received, got.applied)
        assertEquals(a.balances(), b.balances())
        assertEquals(bundle.transactions.size, b.db.transactionDao().observeCount().first())
    }
}
