package com.familyfinance.domain.bank

import com.familyfinance.domain.model.BankCard
import org.junit.Assert.assertEquals
import org.junit.Test

class PushRoutingTest {
    private fun card(bank: Bank, last4: String?, account: String, deleted: Boolean = false) =
        BankCard("$bank$last4", bank.code, last4, account, 0, deleted, "d")

    private val cards = listOf(
        card(Bank.SBER, "2824", "sber-me"),
        card(Bank.SBER, "1111", "sber-wife"),          // two cards of one bank
        card(Bank.SBER, null, "sber-default"),
        card(Bank.SBER, "3333", "old", deleted = true),
        card(Bank.TBANK, null, "tbank"),
    )

    private fun event(bank: Bank, card: String?) = BankEvent(bank, false, 100, "", card)

    @Test fun `each card of a bank goes to its own account`() {
        assertEquals(PushRoute("sber-me", false), PushRouting.route(event(Bank.SBER, "2824"), cards))
        assertEquals(PushRoute("sber-wife", false), PushRouting.route(event(Bank.SBER, "1111"), cards))
    }

    @Test fun `unknown or deleted card goes to the bank fallback and is reported`() {
        assertEquals(PushRoute("sber-default", true), PushRouting.route(event(Bank.SBER, "7777"), cards))
        assertEquals(PushRoute("sber-default", true), PushRouting.route(event(Bank.SBER, "3333"), cards))
    }

    @Test fun `no card number uses the fallback quietly, nothing linked means no account`() {
        assertEquals(PushRoute("tbank", false), PushRouting.route(event(Bank.TBANK, null), cards))
        assertEquals(PushRoute(null, true), PushRouting.route(event(Bank.RAIF, "5790"), cards))
    }
}
