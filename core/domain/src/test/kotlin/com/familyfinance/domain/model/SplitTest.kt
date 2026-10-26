package com.familyfinance.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTest {
    private val whole = Transaction(
        "tx", TransactionType.EXPENSE, 100, 125430, "RUB", "acc", 125430,
        categoryUid = "fruit", note = "Лента", source = Transaction.SOURCE_RECEIPT, receipt = "t=1&s=1254.30",
        updatedAt = 0, deviceId = "",
    )
    private var next = 0
    private val newUid = { "new${++next}" }

    @Test fun `first part takes the remainder and keeps the receipt`() {
        val result = Split.build(
            whole,
            listOf(Split.Part("fruit", 0), Split.Part("chem", 40000), Split.Part("toys", 20000)),
            partUids = emptyList(),
            newUid = newUid,
        ) as Split.Result.Valid
        val parts = result.transactions
        assertEquals(listOf(65430L, 40000L, 20000L), parts.map { it.amount })
        assertEquals(listOf("tx", "new1", "new2"), parts.map { it.uid })
        assertEquals(listOf("tx", "tx", "tx"), parts.map { it.splitUid })
        assertEquals(listOf(0, 1, 2), parts.map { it.splitIndex })
        assertEquals("t=1&s=1254.30", parts[0].receipt)
        assertNull(parts[1].receipt)
        assertTrue(parts.all { it.note == "Лента" && it.date == 100L && it.source == Transaction.SOURCE_RECEIPT })

        val back = Split.whole(parts.reversed())
        assertEquals(125430L, back.amount)
        assertEquals("fruit", back.categoryUid)
        assertEquals("t=1&s=1254.30", back.receipt)
    }

    @Test fun `editing keeps part uids and the group`() {
        val first = Split.build(whole, listOf(Split.Part("a", 0), Split.Part("b", 100)), emptyList(), newUid) as Split.Result.Valid
        val again = Split.build(
            Split.whole(first.transactions).copy(amount = 125430),
            listOf(Split.Part("a", 0), Split.Part("b", 200), Split.Part("c", 300)),
            partUids = first.transactions.map { it.uid },
            newUid = newUid,
        ) as Split.Result.Valid
        assertEquals(listOf("tx", "new1", "new2"), again.transactions.map { it.uid })
        assertEquals(setOf("tx"), again.transactions.map { it.splitUid }.toSet())
    }

    @Test fun `parts must leave something for the first one`() {
        assertEquals(Split.Result.NoRemainder, Split.build(whole, listOf(Split.Part("a", 0), Split.Part("b", 125430)), emptyList(), newUid))
        assertEquals(Split.Result.EmptyPart(1), Split.build(whole, listOf(Split.Part("a", 0), Split.Part("b", 0)), emptyList(), newUid))
    }

    @Test fun `account amount in another currency is shared in proportion`() {
        val usd = whole.copy(amount = 1000, currencyCode = "USD", accountAmount = 90000)
        val parts = (Split.build(usd, listOf(Split.Part("a", 0), Split.Part("b", 333)), emptyList(), newUid) as Split.Result.Valid).transactions
        assertEquals(listOf(667L, 333L), parts.map { it.amount })
        assertEquals(listOf(60030L, 29970L), parts.map { it.accountAmount })
        assertEquals(90000L, parts.sumOf { it.accountAmount })
    }
}
