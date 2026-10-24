package com.familyfinance.ui.transaction

import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDraftTest {

    private fun account(uid: String, currency: String = "RUB") =
        Account(uid = uid, groupUid = null, name = uid, currencyCode = currency, sortOrder = 0, updatedAt = 0, deviceId = "d")

    private val accounts = listOf(account("card"), account("cash"), account("usd", "USD")).associateBy { it.uid }
    private val decimals: (String) -> Int = { 2 }

    private fun TransactionDraft.valid() = (build(accounts, decimals) { "new" } as DraftResult.Valid).transaction
    private fun TransactionDraft.errors() = (build(accounts, decimals) as DraftResult.Invalid).errors

    @Test fun `expense takes the account currency and parses russian input`() {
        val tx = TransactionDraft(amountText = "1 234,5", accountUid = "card", categoryUid = "food", date = 10).valid()
        assertEquals("new", tx.uid)
        assertEquals(123450, tx.amount)
        assertEquals(123450, tx.accountAmount)
        assertEquals("RUB", tx.currencyCode)
        assertEquals("food", tx.categoryUid)
    }

    @Test fun `reports missing amount and account`() {
        assertEquals(setOf(DraftError.AMOUNT, DraftError.ACCOUNT), TransactionDraft(amountText = "abc", date = 0).errors())
        assertEquals(setOf(DraftError.AMOUNT), TransactionDraft(amountText = "0", accountUid = "card", date = 0).errors())
    }

    @Test fun `transfer needs a different target and drops the category`() {
        val base = TransactionDraft(type = TransactionType.TRANSFER, amountText = "100", accountUid = "card", categoryUid = "x", date = 0)
        assertEquals(setOf(DraftError.TO_ACCOUNT), base.copy(toAccountUid = "card").errors())

        val tx = base.copy(toAccountUid = "cash").valid()
        assertEquals("cash", tx.toAccountUid)
        assertEquals(10000L, tx.toAccountAmount)
        assertNull(tx.categoryUid)
    }

    @Test fun `cross currency transfer asks for the credited amount`() {
        val base = TransactionDraft(type = TransactionType.TRANSFER, amountText = "9000", accountUid = "card", toAccountUid = "usd", date = 0)
        assertTrue(base.needsToAmount(accounts))
        assertEquals(setOf(DraftError.TO_AMOUNT), base.errors())
        assertEquals(10000L, base.copy(toAmountText = "100").valid().toAccountAmount)
    }

    @Test fun `editing an imported foreign currency operation keeps its rate`() {
        // Imported: 56 RUB paid from a USD account, 0.82 USD booked on the account.
        val original = Transaction(
            uid = "t1", type = TransactionType.EXPENSE, date = 5, amount = 5600, currencyCode = "RUB",
            accountUid = "usd", accountAmount = 82, updatedAt = 77, deviceId = "other",
        )
        val draft = TransactionDraft.from(original, decimals)
        assertEquals("56", draft.amountText)

        val edited = draft.copy(amountText = "112").valid()
        assertEquals("t1", edited.uid)
        assertEquals("RUB", edited.currencyCode)
        assertEquals(11200, edited.amount)
        assertEquals(164, edited.accountAmount)
    }
}
