package com.familyfinance.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceAdjustmentTest {
    private val account = Account("a", null, "Сбербанк", "RUB", 0, updatedAt = 0, deviceId = "d")

    @Test fun `brings the balance to the wanted value`() {
        val down = BalanceAdjustment.transaction(account, current = 17835774, wanted = 17800000, date = 1, uid = "x")!!
        assertEquals(TransactionType.EXPENSE, down.type)
        assertEquals(35774L, down.accountAmount)
        assertEquals(17800000L, 17835774 - down.accountAmount)
        assertNull(down.categoryUid)

        val up = BalanceAdjustment.transaction(account, current = -235, wanted = 0, date = 1, uid = "y")!!
        assertEquals(TransactionType.INCOME, up.type)
        assertEquals(235L, up.amount)
    }

    @Test fun `nothing when it already matches`() {
        assertNull(BalanceAdjustment.transaction(account, current = 100, wanted = 100, date = 1, uid = "z"))
    }
}
