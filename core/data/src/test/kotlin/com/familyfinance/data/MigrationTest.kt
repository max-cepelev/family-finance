package com.familyfinance.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.familyfinance.data.db.FinanceDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phones already running the app keep their data when the schema grows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinanceDatabase::class.java)

    @Test fun `version 4 operations survive the move to receipts`() = runTest {
        helper.createDatabase(NAME, 4).apply {
            execSQL(
                "INSERT INTO transactions (uid, type, date, amount, currencyCode, accountUid, accountAmount, toAccountUid, " +
                    "toAccountAmount, categoryUid, note, updatedAt, deleted, deviceId, source) " +
                    "VALUES ('tx', 'EXPENSE', 1, 39297, 'RUB', 'acc', 39297, NULL, NULL, 'food', 'Magnit', 5, 0, 'me', 'push')",
            )
            close()
        }
        helper.runMigrationsAndValidate(NAME, 5, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), FinanceDatabase::class.java, NAME)
            .allowMainThreadQueries().build()
        val tx = db.transactionDao().get("tx")!!
        assertEquals("Magnit", tx.note)
        assertEquals("push", tx.source)
        assertNull(tx.receipt)
        assertNull(db.cashRegisterDao().get("any"))
        db.close()
    }

    @Test fun `version 5 operations are not split`() = runTest {
        helper.createDatabase(NAME, 5).apply {
            execSQL(
                "INSERT INTO transactions (uid, type, date, amount, currencyCode, accountUid, accountAmount, toAccountUid, " +
                    "toAccountAmount, categoryUid, note, updatedAt, deleted, deviceId, source, receipt) " +
                    "VALUES ('tx', 'EXPENSE', 1, 39297, 'RUB', 'acc', 39297, NULL, NULL, 'food', 'Magnit', 5, 0, 'me', 'push', 'qr')",
            )
            close()
        }
        helper.runMigrationsAndValidate(NAME, 6, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), FinanceDatabase::class.java, NAME)
            .allowMainThreadQueries().build()
        val tx = db.transactionDao().get("tx")!!
        assertEquals("qr", tx.receipt)
        assertNull(tx.splitUid)
        assertEquals(0, tx.splitIndex)
        db.close()
    }

    private companion object {
        const val NAME = "migration-test"
    }
}
