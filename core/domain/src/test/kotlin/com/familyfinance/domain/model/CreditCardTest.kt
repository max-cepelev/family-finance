package com.familyfinance.domain.model

import com.familyfinance.domain.sync.SyncLog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CreditCardTest {
    @Test fun `debt and available follow the balance`() {
        assertEquals(11081542L, CreditCard.debt(-11081542))
        assertEquals(3918458L, CreditCard.available(15000000, -11081542))
        // Paid more than owed: no debt, the extra adds to what can be spent.
        assertEquals(0L, CreditCard.debt(5000))
        assertEquals(15005000L, CreditCard.available(15000000, 5000))
        // Over the limit: nothing available rather than a negative amount.
        assertEquals(0L, CreditCard.available(100, -500))
    }

    @Test fun `next payment is this month until the day passes`() {
        val today = LocalDate.of(2026, 11, 22)
        assertEquals(LocalDate.of(2026, 11, 25), CreditCard.nextPayment(25, today))
        assertEquals(LocalDate.of(2026, 11, 22), CreditCard.nextPayment(22, today))
        assertEquals(LocalDate.of(2026, 12, 10), CreditCard.nextPayment(10, today))
        assertEquals(3L, CreditCard.daysUntil(CreditCard.nextPayment(25, today), today))
    }

    @Test fun `a day past the month end falls on its last day`() {
        assertEquals(LocalDate.of(2027, 2, 28), CreditCard.nextPayment(31, LocalDate.of(2027, 2, 1)))
        assertEquals(LocalDate.of(2027, 2, 28), CreditCard.nextPayment(30, LocalDate.of(2027, 1, 31)))
    }

    @Test fun `credit settings travel in the sync log`() {
        val card = Account("a", null, "Кредитка Сбер", "RUB", 1, updatedAt = 5, deviceId = "me", isCredit = true, creditLimit = 15000000, paymentDay = 25)
        assertEquals(card, SyncLog.decode(SyncLog.encode(card)).toRecord())
        // A line from an older app version has no credit fields: a plain account.
        val old = """{"t":"account","uid":"b","name":"Наличные","currencyCode":"RUB","sortOrder":2,"hidden":false,"updatedAt":1,"deleted":false,"deviceId":"x"}"""
        val plain = SyncLog.decode(old).toRecord() as Account
        assertEquals(false, plain.isCredit)
        assertEquals(null, plain.paymentDay)
    }
}
