package com.familyfinance.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An operation split across categories is stored as parts: ordinary operations with the same [Transaction.splitUid],
 * date, account and note, each with its own category and amount. Statistics, balances and day totals then need no
 * special case; only lists show the parts as one row.
 */
object Split {

    /** One part as the editor holds it: the first part's amount is not stored, it is whatever remains. */
    data class Part(val categoryUid: String?, val amount: Long)

    sealed interface Result {
        data class Valid(val transactions: List<Transaction>) : Result
        /** The other parts add up to the whole amount or more, so nothing is left for the first one. */
        data object NoRemainder : Result
        /** A part other than the first has no amount. */
        data class EmptyPart(val index: Int) : Result
    }

    /** What the first part gets: the total minus every other part. */
    fun remainder(total: Long, others: List<Long>): Long = total - others.sum()

    /**
     * Turns [whole] (the operation with its full amount) into parts. [parts] start with the remainder part, whose
     * amount is ignored. The first part keeps [whole]'s uid, receipt and source; [newUid] names the rest.
     * The amount in the account currency is shared in proportion, the rounding going to the first part.
     */
    fun build(whole: Transaction, parts: List<Part>, partUids: List<String?>, newUid: () -> String): Result {
        require(parts.size >= 2) { "A split needs at least two parts" }
        parts.drop(1).forEachIndexed { i, p -> if (p.amount <= 0) return Result.EmptyPart(i + 1) }
        val first = remainder(whole.amount, parts.drop(1).map { it.amount })
        if (first <= 0) return Result.NoRemainder

        val amounts = listOf(first) + parts.drop(1).map { it.amount }
        val accountAmounts = shares(whole.accountAmount, whole.amount, amounts)
        val group = whole.splitUid ?: whole.uid
        return Result.Valid(
            amounts.indices.map { i ->
                whole.copy(
                    uid = if (i == 0) whole.uid else partUids.getOrNull(i) ?: newUid(),
                    amount = amounts[i],
                    accountAmount = accountAmounts[i],
                    categoryUid = parts[i].categoryUid,
                    splitUid = group,
                    splitIndex = i,
                    // The receipt belongs to the whole purchase; keeping it on the first part is enough to find it.
                    receipt = if (i == 0) whole.receipt else null,
                )
            },
        )
    }

    /** The parts read back as one operation: summed amounts, the first part's category. */
    fun whole(parts: List<Transaction>): Transaction {
        val ordered = parts.sortedBy { it.splitIndex }
        val first = ordered.first()
        return first.copy(
            amount = ordered.sumOf { it.amount },
            accountAmount = ordered.sumOf { it.accountAmount },
            receipt = ordered.firstNotNullOfOrNull { it.receipt },
        )
    }

    private fun shares(accountTotal: Long, total: Long, amounts: List<Long>): List<Long> {
        if (accountTotal == total) return amounts
        val rest = amounts.drop(1).map {
            BigDecimal.valueOf(it).multiply(BigDecimal.valueOf(accountTotal))
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP).toLong()
        }
        return listOf(accountTotal - rest.sum()) + rest
    }
}
