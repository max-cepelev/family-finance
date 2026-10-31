package com.familyfinance.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetsTest {
    private val main = MainCurrency("RUB", mapOf("USD" to 90.0))
    private fun category(uid: String, parent: String? = null, order: Int = 0, color: String? = null) =
        Category(uid, parent, uid, CategoryType.EXPENSE, order, updatedAt = 0, deviceId = "d", color = color)
    private fun tx(amount: Long, category: String?, type: TransactionType = TransactionType.EXPENSE, currency: String = "RUB") =
        Transaction("t$amount$category", type, 0, amount, currency, "a", amount, categoryUid = category, updatedAt = 0, deviceId = "d")
    private fun budget(category: String?, amount: Long) = Budget("b$category", category, amount, updatedAt = 0, deviceId = "d")

    private val categories = listOf(category("food"), category("bread", parent = "food"), category("fun", order = 1))

    @Test fun `a category budget counts its subcategories, the overall one every expense`() {
        val month = listOf(tx(30000, "food"), tx(12000, "bread"), tx(5000, "fun"), tx(100, "USD-cafe", currency = "USD"), tx(99999, "food", TransactionType.INCOME))
        val states = Budgets.progress(listOf(budget("food", 40000), budget(null, 60000), budget("bread", 10000)), month, categories, main)
        assertEquals(listOf(42000L, 56000L, 12000L), states.map { it.spent })
        assertEquals(listOf(BudgetState.Level.OVER, BudgetState.Level.NEAR, BudgetState.Level.OVER), states.map { it.level })
        assertEquals(-2000L, states[0].left)
    }

    @Test fun `warnings at 80 and 100 percent`() {
        assertEquals(0, Budgets.reached(BudgetState(budget(null, 10000), 7999)))
        assertEquals(80, Budgets.reached(BudgetState(budget(null, 10000), 8000)))
        assertEquals(100, Budgets.reached(BudgetState(budget(null, 10000), 10000)))
    }

    @Test fun `colours come from the palette by order, picked ones win, subcategories follow the parent`() {
        val colors = CategoryColors.resolve(listOf(category("fun", order = 1), category("food"), category("bread", "food"), category("car", order = 2, color = "#123456")))
        assertEquals(CategoryColors.PALETTE[0], colors["food"])
        assertEquals(CategoryColors.PALETTE[0], colors["bread"])
        assertEquals(CategoryColors.PALETTE[1], colors["fun"])
        assertEquals("#123456", colors["car"])
        assertEquals(CategoryColors.PALETTE[2], CategoryColors.nextFree(categories, CategoryType.EXPENSE))
    }

    @Test fun `a picked palette colour is not given to another category`() {
        val blue = CategoryColors.PALETTE[1]
        val colors = CategoryColors.resolve(listOf(category("food", color = blue), category("fun", order = 1), category("car", order = 2)))
        assertEquals(blue, colors["food"])
        assertEquals(CategoryColors.PALETTE[0], colors["fun"])
        assertEquals(CategoryColors.PALETTE[2], colors["car"])
    }
}
