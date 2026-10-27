package com.familyfinance.data.push

import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.PushSeenEntity
import com.familyfinance.data.db.UnknownCardEntity
import com.familyfinance.data.db.toDomain
import com.familyfinance.domain.bank.Bank
import com.familyfinance.domain.bank.BankEvent
import com.familyfinance.domain.bank.BankParser
import com.familyfinance.domain.bank.PushRouting
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.repository.TransactionRepository
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PushResult {
    /** Not a money movement (codes, ads, balance reminders). */
    data object Ignored : PushResult
    /** Already turned into an operation earlier. */
    data object Duplicate : PushResult
    /** Neither the card nor the bank fallback is linked to an account. */
    data class NoAccount(val event: BankEvent) : PushResult
    data class Recorded(val transaction: Transaction, val unknownCard: Boolean) : PushResult
}

/** Turns a bank notification into an operation: parse, skip repeats, pick the account and a likely category. */
@Singleton
class PushImporter @Inject constructor(
    private val db: FinanceDatabase,
    private val transactions: TransactionRepository,
) {

    suspend fun handle(bank: Bank, title: String?, text: String?, postedAt: Long): PushResult {
        val event = BankParser.parse(bank, title, text) ?: return PushResult.Ignored
        val bankDao = db.bankDao()
        // The same notification can be posted again (updates, re-delivery after a restart): same content
        // in the same minute is one operation.
        if (bankDao.markSeen(PushSeenEntity(dedupeKey(event, postedAt), postedAt)) == -1L) return PushResult.Duplicate
        bankDao.forgetSeenBefore(postedAt - SEEN_KEEP_MS)

        val route = PushRouting.route(event, bankDao.cards().map { it.toDomain() })
        if (route.unknownCard && event.card != null) {
            bankDao.upsertUnknown(UnknownCardEntity(bank.code, event.card!!, postedAt, route.accountUid))
        }
        val accountUid = route.accountUid ?: return PushResult.NoAccount(event)
        val account = db.accountDao().get(accountUid) ?: return PushResult.NoAccount(event)

        val type = if (event.income) TransactionType.INCOME else TransactionType.EXPENSE
        val transaction = Transaction(
            uid = UUID.randomUUID().toString(),
            type = type,
            date = postedAt,
            amount = event.amount,
            // Notifications of these banks are in rubles; the account keeps its own currency.
            currencyCode = account.currencyCode,
            accountUid = accountUid,
            accountAmount = event.amount,
            categoryUid = transactions.categoryForNote(type, event.payee),
            note = event.payee,
            updatedAt = 0,
            deviceId = "",
            source = Transaction.SOURCE_PUSH,
        )
        transactions.save(transaction)
        return PushResult.Recorded(transaction, route.unknownCard)
    }

    /**
     * A notification still in the shade when the listener connects, possibly recorded before. One that went through
     * [handle] is skipped as a repeat; one older than the seen marks are kept is skipped too, as it could no longer
     * be told apart from a new one.
     */
    suspend fun handleMissed(bank: Bank, title: String?, text: String?, postedAt: Long, now: Long = System.currentTimeMillis()): PushResult {
        if (now - postedAt > SEEN_KEEP_MS) return PushResult.Ignored
        return handle(bank, title, text, postedAt)
    }

    private fun dedupeKey(event: BankEvent, postedAt: Long): String {
        val raw = listOf(event.bank.code, event.income, event.amount, event.card, event.payee, postedAt / MINUTE_MS).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val SEEN_KEEP_MS = 7 * 24 * 60 * 60_000L
    }
}
