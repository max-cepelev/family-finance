package com.familyfinance.domain.model

/**
 * "Операции из уведомлений [bank] по карте [last4] записывать на [accountUid]". [last4] null is the
 * bank's fallback for notifications without a card number and for cards not linked yet.
 */
data class BankCard(
    override val uid: String,
    /** [com.familyfinance.domain.bank.Bank.code]. */
    val bank: String,
    val last4: String?,
    val accountUid: String,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
) : Syncable
