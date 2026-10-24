package com.familyfinance.domain.importer.mmbak

import com.familyfinance.domain.importer.ImportFormatException
import com.familyfinance.domain.model.Balances
import com.familyfinance.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.sql.DriverManager

/** Runs the reader on a small hand-made database with the Money Manager schema subset we read. */
class MmbakReaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun backup(vararg inserts: String) = tmp.newFile("test.mmbak").also { file ->
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { c ->
            c.createStatement().use { st ->
                (SCHEMA + BASE_ROWS + inserts).forEach(st::execute)
            }
        }
    }

    private fun read(file: java.io.File) = JdbcMmbakSource(file).use { MmbakReader("dev-1").read(it) }

    @Test fun `maps categories with hierarchy and keeps deleted ones archived`() {
        val bundle = read(backup())
        val food = bundle.categories.single { it.name == "Питание" }
        val lunch = bundle.categories.single { it.name == "обед" }
        val old = bundle.categories.single { it.name == "Старое" }
        assertNull(food.parentUid)
        assertEquals(food.uid, lunch.parentUid)
        assertTrue(old.archived)
    }

    @Test fun `collapses transfer halves into one transfer`() {
        val bundle = read(backup(
            tx(10, "3", "500.00", asset = "a1", to = "a2", trans = "T1"),
            tx(11, "4", "500.00", asset = "a2", to = "a1", trans = "T1"),
        ))
        val transfer = bundle.transactions.single { it.type == TransactionType.TRANSFER }
        assertEquals(50000, transfer.amount)
        assertEquals(bundle.accounts.single { it.name == "Карта" }.uid, transfer.accountUid)
        assertEquals(bundle.accounts.single { it.name == "Наличные" }.uid, transfer.toAccountUid)
        assertEquals(1, bundle.report.transfersMerged)
    }

    @Test fun `balances use account amounts, negatives and adjustments`() {
        val bundle = read(backup(
            tx(1, "0", "1000.00", ctg = "2"),
            tx(2, "1", "250.50", ctg = "18"),
            tx(3, "1", "-0.15", ctg = "18"),         // discount booked as negative expense
            tx(4, "0", "99.99", ctg = "-4"),          // balance adjustment
            tx(5, "1", "56.0", asset = "usd", amountAccount = 0.82, ctg = "18"),
            tx(6, "1", "1.00", ctg = "18", deleted = 1),
        ))
        val balances = Balances.byAccount(bundle.transactions)
        val card = bundle.accounts.single { it.name == "Карта" }
        val usd = bundle.accounts.single { it.name == "Доллары" }
        assertEquals(100000L - 25050 + 15 + 9999, balances[card.uid])
        assertEquals(-82L, balances[usd.uid])
        assertEquals("USD", usd.currencyCode)
        assertEquals(1, bundle.report.balanceAdjustments)
        assertEquals(1, bundle.report.skippedDeleted)
        assertNull(bundle.transactions.single { it.amount == 9999L }.categoryUid)
    }

    @Test fun `account with a wrong currency takes the currency of its operations`() {
        val bundle = read(backup(
            "INSERT INTO ASSETS VALUES ('vtb', 'ВТБ', '3', 'RUB_USD', '0', 4, 1)",
            tx(1, "1", "100.00", asset = "vtb", ctg = "18"),
            tx(2, "0", "250.00", asset = "vtb", ctg = "2"),
            tx(3, "1", "56.0", asset = "usd", amountAccount = 0.82, ctg = "18"),
        ))
        assertEquals("RUB", bundle.accounts.single { it.name == "ВТБ" }.currencyCode)
        // Really converted once, so it stays in dollars.
        assertEquals("USD", bundle.accounts.single { it.name == "Доллары" }.currencyCode)
        assertEquals(1, bundle.report.warningCount)
    }

    @Test fun `same backup gives the same uids`() {
        val file = backup(tx(1, "1", "10.00", ctg = "18"))
        assertEquals(read(file), read(file))
    }

    @Test(expected = ImportFormatException::class)
    fun `rejects foreign sqlite file`() {
        val file = tmp.newFile("other.db")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use {
            it.createStatement().execute("CREATE TABLE notes (id INTEGER)")
        }
        read(file)
    }

    private fun tx(
        id: Int, doType: String, money: String, asset: String = "a1", to: String = "", ctg: String = "",
        trans: String = "", amountAccount: Double? = null, deleted: Int = 0,
    ) = "INSERT INTO INOUTCOME (uid, DO_TYPE, ZMONEY, AMOUNT_ACCOUNT, ZDATE, assetUid, toAssetUid, ctgUid, " +
        "ZCONTENT, ZDATA, txUidTrans, IS_DEL, currencyUid, UTIME) VALUES ('t$id', '$doType', '$money', " +
        "${amountAccount ?: money.toDouble()}, '${1600000000000 + id}', '$asset', '$to', '$ctg', 'n$id', 'n$id', " +
        "'$trans', $deleted, 'RUB_RUB', ${1600000000000 + id})"

    private companion object {
        val SCHEMA = listOf(
            "CREATE TABLE CURRENCY (uid, NAME, ISO, IS_DEL, RATE, SYMBOL, IS_MAIN_CURRENCY, DECIMAL_POINT, MODIFY_DATE)",
            "CREATE TABLE ASSETGROUP (uid, ACC_GROUP_NAME, ORDERSEQ, IS_DEL, USETIME)",
            "CREATE TABLE ASSETS (uid, NIC_NAME, groupUid, currencyUid, ZDATA, ORDERSEQ, A_UTIME)",
            "CREATE TABLE ZCATEGORY (uid, NAME, TYPE, STATUS, pUid, ORDERSEQ, C_IS_DEL, C_UTIME)",
            "CREATE TABLE INOUTCOME (uid, DO_TYPE VARCHAR, ZMONEY VARCHAR, AMOUNT_ACCOUNT REAL, ZDATE VARCHAR, " +
                "assetUid, toAssetUid, ctgUid, ZCONTENT, ZDATA, txUidTrans, IS_DEL, currencyUid, UTIME)",
        )
        val BASE_ROWS = listOf(
            "INSERT INTO CURRENCY VALUES ('RUB_RUB', 'RUB', 'RUB', 0, 1.0, 'руб', 1, 2, 1)",
            "INSERT INTO CURRENCY VALUES ('RUB_USD', 'USD', 'USD', 0, 68.5, '$', 0, 2, 1)",
            "INSERT INTO ASSETGROUP VALUES ('3', 'Банковская карта', 1, 0, 1)",
            "INSERT INTO ASSETGROUP VALUES ('11', 'Кошелек', 2, 0, 1)",
            "INSERT INTO ASSETS VALUES ('a1', 'Карта', '3', 'RUB_RUB', '0', 1, 1)",
            "INSERT INTO ASSETS VALUES ('a2', 'Наличные', '11', 'RUB_RUB', '', 2, 1)",
            "INSERT INTO ASSETS VALUES ('usd', 'Доллары', '3', 'RUB_USD', '1', 3, 1)",
            "INSERT INTO ZCATEGORY VALUES ('2', 'Зарплата', 0, 0, '0', 1, NULL, 1)",
            "INSERT INTO ZCATEGORY VALUES ('6', 'Питание', 1, 0, '0', 2, NULL, 1)",
            "INSERT INTO ZCATEGORY VALUES ('18', 'обед', 1, 2, '6', 3, NULL, 1)",
            "INSERT INTO ZCATEGORY VALUES ('x', 'Старое', 1, 0, '', 4, 1, 1)",
        )
    }
}
