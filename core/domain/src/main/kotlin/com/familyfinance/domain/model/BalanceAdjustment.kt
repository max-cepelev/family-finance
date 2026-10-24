package com.familyfinance.domain.model

/** "Изменить остаток": an operation without a category that brings the account to the wanted balance. */
object BalanceAdjustment {
    const val NOTE = "Корректировка остатка"

    /** Null when the balance already matches. Amounts in the account currency, minor units. */
    fun transaction(account: Account, current: Long, wanted: Long, date: Long, uid: String): Transaction? {
        val difference = wanted - current
        if (difference == 0L) return null
        val amount = kotlin.math.abs(difference)
        return Transaction(
            uid = uid,
            type = if (difference > 0) TransactionType.INCOME else TransactionType.EXPENSE,
            date = date,
            amount = amount,
            currencyCode = account.currencyCode,
            accountUid = account.uid,
            accountAmount = amount,
            note = NOTE,
            updatedAt = 0,
            deviceId = "",
        )
    }
}
