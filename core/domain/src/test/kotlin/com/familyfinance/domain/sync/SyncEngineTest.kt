package com.familyfinance.domain.sync

import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SyncEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private val idA = "aaaaaaaa-0000-0000-0000-000000000001"
    private val idB = "bbbbbbbb-0000-0000-0000-000000000002"

    private inner class Phone(val id: String, val name: String, dir: File) {
        val store = InMemorySyncStore()
        val engine = SyncEngine(id, name, DirectorySyncFolder(dir), store, clock = { 1L })
        suspend fun sync() = engine.sync()
        fun tx(uid: String) = store.get<Transaction>(uid)
    }

    private fun phones(): Triple<Phone, Phone, File> {
        val dir = tmp.newFolder("sync")
        return Triple(Phone(idA, "Phone A", dir), Phone(idB, "Phone B", dir), dir)
    }

    private fun tx(uid: String, amount: Long, updatedAt: Long, device: String, deleted: Boolean = false) = Transaction(
        uid = uid, type = TransactionType.EXPENSE, date = 1000, amount = amount, currencyCode = "RUB",
        accountUid = "acc", accountAmount = amount, updatedAt = updatedAt, deleted = deleted, deviceId = device,
    )

    @Test fun `changes travel both ways and edits win by time`() = runTest {
        val (a, b, _) = phones()
        a.store.put(tx("t1", 100, updatedAt = 10, device = idA))
        a.sync()
        b.sync()
        assertEquals(100L, b.tx("t1")!!.amount)
        assertEquals("Phone A", b.store.deviceNames[idA])

        b.store.put(tx("t1", 250, updatedAt = 20, device = idB))
        b.sync()
        a.sync()
        assertEquals(250L, a.tx("t1")!!.amount)

        // Deletion is a newer version too.
        a.store.put(tx("t1", 250, updatedAt = 30, device = idA, deleted = true))
        a.sync(); b.sync()
        assertTrue(b.tx("t1")!!.deleted)
    }

    @Test fun `concurrent edits converge to the same winner`() = runTest {
        val (a, b, _) = phones()
        a.store.put(tx("t1", 100, updatedAt = 50, device = idA))
        b.store.put(tx("t1", 999, updatedAt = 40, device = idB))
        a.store.put(tx("t2", 1, updatedAt = 70, device = idA))
        b.store.put(tx("t2", 2, updatedAt = 70, device = idB)) // same time: device id decides
        a.sync(); b.sync(); a.sync()

        assertEquals(100L, a.tx("t1")!!.amount)
        assertEquals(100L, b.tx("t1")!!.amount)
        assertEquals(a.tx("t2"), b.tx("t2"))
        assertEquals(2L, a.tx("t2")!!.amount) // "bbbb…" > "aaaa…"
    }

    @Test fun `applied remote changes are not echoed back`() = runTest {
        val (a, b, dir) = phones()
        a.store.put(tx("t1", 100, updatedAt = 10, device = idA))
        a.sync(); b.sync(); b.sync()
        val bLog = File(dir, SyncLog.fileName(idB)).readLines()
        assertEquals(listOf("device"), bLog.map { SyncLog.decode(it)::class.simpleName!!.lowercase() })
    }

    @Test fun `first sync into a folder exports existing data and a later device gets everything`() = runTest {
        val (a, _, dir) = phones()
        a.store.put(tx("t1", 100, updatedAt = 10, device = idA))
        a.store.markExported(Long.MAX_VALUE) // existed before sync was turned on
        a.sync()
        val late = Phone("cccccccc-0000-0000-0000-000000000003", "Phone C", dir)
        late.sync()
        assertEquals(100L, late.tx("t1")!!.amount)
    }

    @Test fun `incomplete last line waits, conflict copies are ignored`() = runTest {
        val (a, b, dir) = phones()
        a.store.put(tx("t1", 100, updatedAt = 10, device = idA))
        a.sync()
        val logA = File(dir, SyncLog.fileName(idA))
        val half = SyncLog.encode(tx("t2", 5, updatedAt = 11, device = idA))
        logA.appendText(half.take(30))
        File(dir, "familyfinance-$idA.sync-conflict-20261024-120000-ABCDEFG.jsonl")
            .writeText(SyncLog.encode(tx("t3", 7, updatedAt = 12, device = idA)) + "\n")

        val first = b.sync()
        assertEquals(1, first.received)
        assertNull(b.tx("t2"))
        assertNull(b.tx("t3"))

        logA.appendText(half.drop(30) + "\n")
        b.sync()
        assertEquals(5L, b.tx("t2")!!.amount)
    }

    @Test fun `entry from a newer app version blocks the log at that point`() = runTest {
        val (a, b, dir) = phones()
        a.store.put(tx("t1", 100, updatedAt = 10, device = idA))
        a.sync()
        val logA = File(dir, SyncLog.fileName(idA))
        logA.appendText("""{"t":"budget","uid":"x"}""" + "\n")
        logA.appendText(SyncLog.encode(tx("t2", 5, updatedAt = 11, device = idA)) + "\n")

        val report = b.sync()
        assertEquals(listOf(logA.name), report.blockedLogs)
        assertEquals(100L, b.tx("t1")!!.amount)
        assertNull(b.tx("t2"))
        // Still blocked next time, nothing skipped silently.
        assertEquals(listOf(logA.name), b.sync().blockedLogs)
    }

    @Test fun `a record edited twice before export is written once`() = runTest {
        val (a, _, dir) = phones()
        a.store.put(tx("t1", 100, updatedAt = 10, device = idA))
        a.store.put(tx("t1", 200, updatedAt = 11, device = idA))
        val report = a.sync()
        assertEquals(1, report.exported)
        assertEquals(1, report.logCount)
        assertEquals(File(dir, SyncLog.fileName(idA)).length(), report.logBytes)
        val lines = File(dir, SyncLog.fileName(idA)).readLines()
        assertEquals(2, lines.size) // device header + one record
        assertTrue(lines[1].contains("\"amount\":200"))
    }
}
