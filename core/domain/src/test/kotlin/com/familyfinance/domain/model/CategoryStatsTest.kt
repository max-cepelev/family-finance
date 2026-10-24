package com.familyfinance.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryStatsTest {
    private val main = MainCurrency("RUB", mapOf("RUB" to 1.0))

    private fun category(uid: String, parent: String? = null) =
        Category(uid, parent, uid, CategoryType.EXPENSE, 0, updatedAt = 0, deviceId = "d")

    private val categories = listOf(category("food"), category("lunch", "food"), category("milk", "food"), category("bus"))

    private fun tx(category: String?, amount: Long, type: TransactionType = TransactionType.EXPENSE) = Transaction(
        uid = "$category$amount", type = type, date = 0, amount = amount, currencyCode = "RUB", accountUid = "a",
        accountAmount = amount, categoryUid = category, updatedAt = 0, deviceId = "d",
    )

    private val txs = listOf(
        tx("lunch", 300), tx("milk", 200), tx("food", 50), tx("bus", 400), tx(null, 10),
        tx("lunch", 999, TransactionType.INCOME),
    )

    @Test fun `rolls subcategories up to the top level`() {
        assertEquals(
            listOf(CategoryShare("food", 550), CategoryShare("bus", 400), CategoryShare(null, 10)),
            CategoryStats.shares(txs, categories, TransactionType.EXPENSE, main),
        )
    }

    @Test fun `drills into one category`() {
        assertEquals(
            listOf(CategoryShare("lunch", 300), CategoryShare("milk", 200), CategoryShare("food", 50)),
            CategoryStats.shares(txs, categories, TransactionType.EXPENSE, main, parentUid = "food"),
        )
    }
}
