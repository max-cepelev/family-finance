package com.familyfinance.domain.model

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

/**
 * One operation. A transfer is a single record with both sides
 * (unlike Money Manager, which stores it as two linked rows).
 */
data class Transaction(
    override val uid: String,
    val type: TransactionType,
    /** Epoch millis. */
    val date: Long,
    /** Amount in [currencyCode], minor units. Negative is allowed (e.g. a discount booked as expense). */
    val amount: Long,
    val currencyCode: String,
    val accountUid: String,
    /** [amount] converted to the currency of [accountUid]; equals [amount] when currencies match. */
    val accountAmount: Long,
    /** Destination of a transfer. */
    val toAccountUid: String? = null,
    /** Amount credited to [toAccountUid] in its currency. */
    val toAccountAmount: Long? = null,
    /** Null for transfers and balance adjustments. */
    val categoryUid: String? = null,
    val note: String = "",
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val deviceId: String,
    /** Where it came from: [SOURCE_PUSH] for operations added from a bank notification, null for manual ones. */
    val source: String? = null,
    /** [com.familyfinance.domain.receipt.ReceiptQr.canonical] of the receipt attached to it, if any. */
    val receipt: String? = null,
) : Syncable {
    companion object {
        const val SOURCE_PUSH = "push"
        const val SOURCE_RECEIPT = "receipt"
    }
}
