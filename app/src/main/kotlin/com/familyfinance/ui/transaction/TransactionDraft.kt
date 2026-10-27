package com.familyfinance.ui.transaction

import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.Money
import com.familyfinance.domain.model.Split
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

enum class DraftError { AMOUNT, ACCOUNT, TO_ACCOUNT, TO_AMOUNT, SPLIT }

/** A part of a split operation as typed; the first part's amount is the remainder and not typed. */
data class PartDraft(val categoryUid: String?, val amountText: String = "")

sealed interface DraftResult {
    data class Valid(val transaction: Transaction) : DraftResult
    data class Invalid(val errors: Set<DraftError>) : DraftResult
}

/** What the edit form holds, as typed. [build] turns it into a [Transaction] or reports what is wrong. */
data class TransactionDraft(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    /** Null means "the currency of the chosen account". Set only for edited transactions in another currency. */
    val currencyCode: String? = null,
    val accountUid: String? = null,
    val toAccountUid: String? = null,
    /** Amount credited to [toAccountUid], needed only when its currency differs. */
    val toAmountText: String = "",
    val categoryUid: String? = null,
    val date: Long,
    val note: String = "",
    /** The stored transaction being edited, null for a new one. */
    val original: Transaction? = null,
    /** Kept through edits: where the operation came from and the receipt attached to it. */
    val source: String? = null,
    val receipt: String? = null,
    /** Split across categories when there are two parts or more; one part means the split editor was just opened. */
    val parts: List<PartDraft> = emptyList(),
    /** Stored uids of the parts being edited, by position. */
    val partUids: List<String> = emptyList(),
) {
    val isSplit: Boolean get() = parts.size >= 2


    fun currency(accounts: Map<String, Account>): String? =
        currencyCode ?: accounts[accountUid]?.currencyCode

    fun needsToAmount(accounts: Map<String, Account>): Boolean {
        if (type != TransactionType.TRANSFER) return false
        val to = accounts[toAccountUid] ?: return false
        return to.currencyCode != currency(accounts)
    }

    fun build(
        accounts: Map<String, Account>,
        decimals: (String) -> Int,
        newUid: () -> String = { UUID.randomUUID().toString() },
    ): DraftResult {
        val errors = mutableSetOf<DraftError>()
        val account = accounts[accountUid]
        if (account == null) errors += DraftError.ACCOUNT
        val currency = currency(accounts)
        val amount = currency?.let { parseAmount(amountText, decimals(it)) }
        if (amount == null || amount == 0L) errors += DraftError.AMOUNT

        val to = if (type == TransactionType.TRANSFER) accounts[toAccountUid] else null
        if (type == TransactionType.TRANSFER && (to == null || to.uid == account?.uid)) errors += DraftError.TO_ACCOUNT
        val toAmount = when {
            to == null -> null
            to.currencyCode == currency -> amount
            else -> parseAmount(toAmountText, decimals(to.currencyCode)).also {
                if (it == null || it == 0L) errors += DraftError.TO_AMOUNT
            }
        }
        if (errors.isNotEmpty()) return DraftResult.Invalid(errors)

        return DraftResult.Valid(
            Transaction(
                uid = original?.uid ?: newUid(),
                type = type,
                date = date,
                amount = amount!!,
                currencyCode = currency!!,
                accountUid = account!!.uid,
                accountAmount = accountAmount(account, currency, amount),
                toAccountUid = to?.uid,
                toAccountAmount = toAmount,
                categoryUid = if (type == TransactionType.TRANSFER) null else categoryUid,
                note = note.trim(),
                updatedAt = original?.updatedAt ?: 0L,
                deviceId = original?.deviceId.orEmpty(),
                source = source,
                receipt = receipt,
            ),
        )
    }

    /** What saving writes: the operation or its parts, and the stored parts that are gone. */
    data class Writes(val save: List<Transaction>, val delete: List<String>)

    /**
     * [whole] as it is written: as parts when split, as one operation otherwise. A split that went back to one part,
     * or parts that were removed, leave deletions behind. Null when the parts do not add up.
     */
    fun writes(whole: Transaction, decimals: Int, newUid: () -> String = { UUID.randomUUID().toString() }): Writes? {
        val written = if (isSplit) {
            val split = parts.mapIndexed { i, p -> Split.Part(p.categoryUid, if (i == 0) 0 else parseAmount(p.amountText, decimals) ?: 0) }
            when (val result = Split.build(whole, split, partUids, newUid)) {
                is Split.Result.Valid -> result.transactions
                else -> return null
            }
        } else {
            val single = parts.singleOrNull()?.let { whole.copy(categoryUid = it.categoryUid) } ?: whole
            listOf(single.copy(splitUid = null, splitIndex = 0))
        }
        return Writes(written, partUids - written.map { it.uid }.toSet())
    }

    /**
     * The form edits the amount in the transaction currency. When it differs from the account currency
     * (only possible for imported operations), keep the original exchange rate.
     */
    private fun accountAmount(account: Account, currency: String, amount: Long): Long {
        if (account.currencyCode == currency) return amount
        val o = original?.takeIf { it.accountUid == account.uid && it.currencyCode == currency && it.amount != 0L }
            ?: return amount
        return BigDecimal.valueOf(o.accountAmount)
            .multiply(BigDecimal.valueOf(amount))
            .divide(BigDecimal.valueOf(o.amount), 0, RoundingMode.HALF_UP)
            .toLong()
    }

    companion object {
        fun from(tx: Transaction, decimals: (String) -> Int) = TransactionDraft(
            type = tx.type,
            amountText = format(tx.amount, decimals(tx.currencyCode)),
            currencyCode = tx.currencyCode,
            accountUid = tx.accountUid,
            toAccountUid = tx.toAccountUid,
            toAmountText = tx.toAccountAmount?.let { amount ->
                // Decimals of the target currency are not known here; 2 fits every currency we import.
                format(amount, 2)
            }.orEmpty(),
            categoryUid = tx.categoryUid,
            date = tx.date,
            note = tx.note,
            original = tx,
            source = tx.source,
            receipt = tx.receipt,
        )

        /** As the keypad types it: "56", "0,82". */
        fun format(minor: Long, decimals: Int): String =
            Money.toDecimal(minor, decimals).stripTrailingZeros().toPlainString().replace('.', Calculator.COMMA)

        /** The amount as typed: a number ("1 234,50") or a keypad expression ("120+45,5"); null if invalid. */
        fun parseAmount(text: String, decimals: Int): Long? =
            Calculator.evaluate(text)?.let { runCatching { Money.toMinor(it, decimals) }.getOrNull() }
    }
}
