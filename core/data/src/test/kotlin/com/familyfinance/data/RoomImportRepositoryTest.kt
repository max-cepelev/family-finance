package com.familyfinance.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.mmbak.AndroidMmbakSource
import com.familyfinance.data.repository.RoomImportRepository
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.domain.importer.ImportBundle
import com.familyfinance.domain.importer.ImportCounts
import com.familyfinance.domain.importer.ImportReport
import com.familyfinance.domain.importer.mmbak.MmbakReader
import com.familyfinance.domain.importer.mmbak.MmbakUids
import com.familyfinance.domain.model.Balances
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomImportRepositoryTest {

    private lateinit var db: FinanceDatabase
    private lateinit var repository: RoomImportRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FinanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomImportRepository(db, LocalChanges(db))
    }

    @After fun tearDown() = db.close()

    @Test fun `older incoming version does not overwrite a newer local one`() = runTest {
        repository.apply(bundle(tx(updatedAt = 200, amount = 100)))
        val older = repository.apply(bundle(tx(updatedAt = 100, amount = 999)))
        val newer = repository.apply(bundle(tx(updatedAt = 300, amount = 555)))

        assertEquals(0, older.applied.transactions)
        assertEquals(1, newer.applied.transactions)
        assertEquals(-555L, db.transactionDao().observeBalances().first().single().balance)
    }

    /** Full device path: Android SQLite reader -> Room -> balance SQL, on a real backup. */
    @Test fun `real backup imports idempotently and balances match`() = runTest {
        val path = System.getProperty("mmbak.path").orEmpty()
        assumeTrue("mmbak.path not set", path.isNotEmpty() && File(path).isFile)

        val bundle = AndroidMmbakSource(File(path)).use { MmbakReader("test").read(it) }
        val first = repository.apply(bundle)
        assertEquals(first.received, first.applied)
        println("Записано: ${first.applied}")

        // Expected balances straight from the source rows, summed by SQLite and returned as INTEGER,
        // so they do not depend on how the reader converts REAL values.
        val expected = AndroidMmbakSource(File(path)).use { source ->
            source.query(
                "SELECT assetUid, CAST(ROUND(SUM(CASE WHEN DO_TYPE IN ('0','4') THEN AMOUNT_ACCOUNT " +
                    "ELSE -AMOUNT_ACCOUNT END) * 100) AS INTEGER) AS bal FROM INOUTCOME WHERE IS_DEL = 0 GROUP BY assetUid",
            ) { MmbakUids.of("account", it.string("assetUid")) to it.string("bal")!!.toLong() }.toMap()
        }.filterValues { it != 0L }
        val actual = db.transactionDao().observeBalances().first().associate { it.uid to it.balance }.filterValues { it != 0L }
        assertEquals(expected, actual)
        assertEquals(expected, Balances.byAccount(bundle.transactions).filterValues { it != 0L })
        assertEquals(bundle.transactions.size, db.transactionDao().observeCount().first())

        val again = repository.apply(bundle)
        assertEquals(ImportCounts(0, 0, 0, 0, 0), again.applied)
    }

    @Test fun `reads REAL columns with full precision`() {
        val file = File.createTempFile("real", ".db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.execSQL("CREATE TABLE t (v REAL)")
            it.execSQL("INSERT INTO t VALUES (222699.08)")
        }
        val value = AndroidMmbakSource(file).use { source -> source.query("SELECT v FROM t") { it.string("v") }.single() }
        assertEquals("222699.08", value)
        file.delete()
    }

    private fun tx(updatedAt: Long, amount: Long) = Transaction(
        uid = "t1", type = TransactionType.EXPENSE, date = 1, amount = amount, currencyCode = "RUB",
        accountUid = "a1", accountAmount = amount, updatedAt = updatedAt, deviceId = "d",
    )

    private fun bundle(vararg transactions: Transaction) = ImportBundle(
        currencies = emptyList(), groups = emptyList(), accounts = emptyList(), categories = emptyList(),
        transactions = transactions.toList(),
        report = ImportReport(19, 0, 0, 0, 0, emptyList()),
    )
}
