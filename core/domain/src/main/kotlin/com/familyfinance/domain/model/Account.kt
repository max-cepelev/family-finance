package com.familyfinance.domain.model

data class AccountGroup(
    override val uid: String,
    val name: String,
    val sortOrder: Int,
    /** Hidden from pickers but kept for history. */
    val archived: Boolean = false,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
) : Syncable

data class Account(
    override val uid: String,
    val groupUid: String?,
    val name: String,
    val currencyCode: String,
    val sortOrder: Int,
    val hidden: Boolean = false,
    val note: String = "",
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
    /** A credit card: its balance is a debt against [creditLimit]. See [CreditCard]. */
    val isCredit: Boolean = false,
    /** In the account currency, minor units; 0 when not given. */
    val creditLimit: Long = 0,
    /** Day of month the bank expects the payment, 1..31; null when not given. Shown only, nothing is reminded. */
    val paymentDay: Int? = null,
) : Syncable

data class AccountWithBalance(
    val account: Account,
    /** In the account currency, minor units. */
    val balance: Long,
)
