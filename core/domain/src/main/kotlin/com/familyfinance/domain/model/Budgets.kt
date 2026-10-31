package com.familyfinance.domain.model

/** How a [budget] stands in a month: [spent] in minor units of the main currency. */
data class BudgetState(val budget: Budget, val spent: Long) {
    val left: Long get() = budget.amount - spent
    val share: Double get() = if (budget.amount <= 0) 0.0 else spent.toDouble() / budget.amount
    val level: Level get() = when {
        spent > budget.amount -> Level.OVER
        share >= Budgets.NEAR -> Level.NEAR
        else -> Level.OK
    }

    enum class Level { OK, NEAR, OVER }
}

object Budgets {
    /** From here on a budget shows as nearly spent. */
    const val NEAR = 0.9

    /** Shares of the limit that warn, once each per month. */
    val WARNINGS = listOf(80, 100)

    /**
     * Each budget against a month's operations: expenses in its category and the category's subcategories, or all
     * expenses, in the main currency. Parts of a split operation count in their own categories.
     */
    fun progress(budgets: List<Budget>, month: List<Transaction>, categories: List<Category>, main: MainCurrency): List<BudgetState> {
        val parent = categories.associate { it.uid to it.parentUid }
        val byTop = HashMap<String?, Long>()
        var all = 0L
        for (tx in month) {
            if (tx.deleted || tx.type != TransactionType.EXPENSE) continue
            val amount = main.convert(tx.amount, tx.currencyCode)
            all += amount
            val top = tx.categoryUid?.let { parent[it] ?: it }
            byTop[top] = (byTop[top] ?: 0L) + amount
            if (top != tx.categoryUid) byTop[tx.categoryUid] = (byTop[tx.categoryUid] ?: 0L) + amount
        }
        return budgets.filterNot { it.deleted }.map { b -> BudgetState(b, if (b.categoryUid == null) all else byTop[b.categoryUid] ?: 0L) }
    }

    /** The highest of [WARNINGS] that [state] has reached, or 0. */
    fun reached(state: BudgetState): Int = WARNINGS.lastOrNull { state.spent * 100 >= it.toLong() * state.budget.amount } ?: 0
}
