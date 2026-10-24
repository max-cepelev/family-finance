package com.familyfinance.domain.importer.mmbak

import com.familyfinance.domain.model.Balances
import com.familyfinance.domain.model.Money
import com.familyfinance.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Cross-checks the import against the backup itself: every account balance computed from our
 * transactions must equal the sum Money Manager's own rows give. Skipped unless -Pmmbak.path is set.
 */
class RealBackupTest {

    @Test fun `balances match the source backup`() {
        val path = System.getProperty("mmbak.path").orEmpty()
        assumeTrue("mmbak.path not set", path.isNotEmpty() && File(path).isFile)
        val file = File(path)

        JdbcMmbakSource(file).use { source ->
            val bundle = MmbakReader("test").read(source)
            println("Импорт: ${bundle.counts()}")
            println("Отчёт: ${bundle.report.copy(warnings = emptyList())}")
            bundle.report.warnings.forEach { println("  ! $it") }
            // Marked USD in Money Manager, but every operation on it is in rubles.
            bundle.accounts.singleOrNull { it.name == "ВТБ Лена" }?.let { assertEquals("RUB", it.currencyCode) }

            val expected = source.query(
                "SELECT assetUid, SUM(CASE WHEN DO_TYPE IN ('0','4') THEN AMOUNT_ACCOUNT ELSE -AMOUNT_ACCOUNT END) AS bal " +
                    "FROM INOUTCOME WHERE IS_DEL = 0 GROUP BY assetUid",
            ) { it.string("assetUid")!! to it.string("bal")!! }.toMap()
            val names = source.query("SELECT uid, NIC_NAME FROM ASSETS") { it.string("uid")!! to it.string("NIC_NAME")!! }

            val actual = Balances.byAccount(bundle.transactions)
            for ((assetUid, name) in names) {
                val want = Money.toMinor(expected[assetUid]?.toDouble() ?: 0.0, 2)
                val got = actual[MmbakUids.of("account", assetUid)] ?: 0L
                println("  %-22s %14s".format(name, Money.toDecimal(got, 2).toPlainString()))
                assertEquals("баланс «$name»", want, got)
            }

            val sourceRows = source.query("SELECT COUNT(*) AS n FROM INOUTCOME WHERE IS_DEL = 0") { it.string("n")!!.toInt() }.single()
            val transfers = bundle.transactions.count { it.type == TransactionType.TRANSFER }
            assertEquals("строк = операций + вторых половин переводов", sourceRows, bundle.transactions.size + transfers)
        }
    }
}
