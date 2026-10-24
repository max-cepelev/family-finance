package com.familyfinance.domain.model

object Balances {

    /** Balance per account uid, in each account's currency (minor units). */
    fun byAccount(transactions: Iterable<Transaction>): Map<String, Long> {
        val result = HashMap<String, Long>()
        for (tx in transactions) {
            if (tx.deleted) continue
            when (tx.type) {
                TransactionType.INCOME -> result.merge(tx.accountUid, tx.accountAmount, Long::plus)
                TransactionType.EXPENSE -> result.merge(tx.accountUid, -tx.accountAmount, Long::plus)
                TransactionType.TRANSFER -> {
                    result.merge(tx.accountUid, -tx.accountAmount, Long::plus)
                    val to = tx.toAccountUid ?: continue
                    result.merge(to, tx.toAccountAmount ?: tx.accountAmount, Long::plus)
                }
            }
        }
        return result
    }
}
