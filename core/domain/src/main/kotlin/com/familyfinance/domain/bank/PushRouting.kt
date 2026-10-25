package com.familyfinance.domain.bank

import com.familyfinance.domain.model.BankCard

/** Where an operation from a notification goes. */
data class PushRoute(
    /** Null when neither the card nor the bank fallback is linked: nothing can be recorded. */
    val accountUid: String?,
    /** The notification named a card that is not linked yet (it went to the fallback, if any). */
    val unknownCard: Boolean,
)

object PushRouting {
    fun route(event: BankEvent, cards: List<BankCard>): PushRoute {
        val ofBank = cards.filter { !it.deleted && it.bank == event.bank.code }
        val exact = event.card?.let { digits -> ofBank.firstOrNull { it.last4 == digits } }
        if (exact != null) return PushRoute(exact.accountUid, unknownCard = false)
        val fallback = ofBank.firstOrNull { it.last4 == null }
        return PushRoute(fallback?.accountUid, unknownCard = event.card != null)
    }
}
