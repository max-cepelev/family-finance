package com.familyfinance.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DailyTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val main = MainCurrency("RUB", mapOf("RUB" to 1.0, "USD" to 90.0))

    private fun at(day: Int, hour: Int) = LocalDateTime.of(2023, 3, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun tx(uid: String, type: TransactionType, amount: Long, date: Long, currency: String = "RUB") = Transaction(
        uid = uid, type = type, date = date, amount = amount, currencyCode = currency, accountUid = "a",
        accountAmount = amount, toAccountUid = if (type == TransactionType.TRANSFER) "b" else null,
        updatedAt = 0, deviceId = "d",
    )

    @Test fun `groups by local day, newest first, transfers excluded from totals`() {
        val days = Daily.group(
            listOf(
                tx("1", TransactionType.EXPENSE, 100_00, at(1, 10)),
                tx("2", TransactionType.INCOME, 500_00, at(1, 23)),   // still March 1st in Moscow
                tx("3", TransactionType.TRANSFER, 50_00, at(2, 9)),
                tx("4", TransactionType.EXPENSE, 1_00, at(2, 12), currency = "USD"),
                tx("5", TransactionType.EXPENSE, 7_00, at(2, 13)).copy(deleted = true),
            ),
            zone, main,
        )
        assertEquals(listOf(LocalDate.of(2023, 3, 2), LocalDate.of(2023, 3, 1)), days.map { it.date })
        assertEquals(listOf("4", "3"), days[0].transactions.map { it.uid })
        assertEquals(Totals(income = 0, expense = 90_00), days[0].totals)
        assertEquals(Totals(income = 500_00, expense = 100_00), days[1].totals)
    }
}
