package com.familyfinance.web

import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.AccountWithBalance
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.ui.common.MoneyFormatter
import java.time.YearMonth

/** Null category means all; an empty category means uncategorized, never transfers. */
data class WebFilter(val account: String? = null, val category: String? = null, val type: TransactionType? = null, val only: Boolean = false) {
    fun apply(items: List<Transaction>, categories: List<Category>): List<Transaction> {
        val included = setOf(category) + if (only) emptySet() else categories.filter { it.parentUid == category && category != null }.map { it.uid }
        val known = categories.map { it.uid }.toSet()
        return items.filter { tx ->
            !tx.deleted && (account == null || tx.accountUid == account || tx.toAccountUid == account) &&
                (type == null || tx.type == type) && (category == null ||
                (tx.type != TransactionType.TRANSFER && if (category.isEmpty()) tx.categoryUid !in known else tx.categoryUid in included))
        }
    }

    fun url(path: String, month: YearMonth, query: String = ""): String = WebLinks.to(path, "month" to month.toString(),
        "account" to account, "category" to category, "type" to type?.name, "only" to "true".takeIf { only }, "q" to query.takeIf { it.isNotEmpty() })
}

data class WebAccountRow(val uid: String, val name: String, val balance: String, val hidden: Boolean, val negative: Boolean)
data class WebAccountGroup(val name: String, val total: String, val rows: List<WebAccountRow>)
data class WebAccounts(val assets: String, val liabilities: String, val total: String, val groups: List<WebAccountGroup>) {
    companion object {
        fun build(accounts: List<AccountWithBalance>, groups: List<AccountGroup>, main: MainCurrency): WebAccounts {
            val all = accounts.filterNot { it.account.deleted }
            val visible = all.filterNot { it.account.hidden }.map { main.convert(it.balance, it.account.currencyCode) }
            val byGroup = all.groupBy { it.account.groupUid }
            val known = groups.filterNot { it.deleted }.sortedBy { it.sortOrder }
            fun group(name: String, list: List<AccountWithBalance>) = WebAccountGroup(name,
                MoneyFormatter.format(list.filterNot { it.account.hidden }.sumOf { main.convert(it.balance, it.account.currencyCode) }, main.code),
                list.sortedBy { it.account.sortOrder }.map { WebAccountRow(it.account.uid, it.account.name,
                    MoneyFormatter.format(it.balance, it.account.currencyCode), it.account.hidden, it.balance < 0) })
            val rows = known.mapNotNull { g -> byGroup[g.uid]?.let { group(g.name, it) } } + listOfNotNull(
                all.filter { a -> known.none { it.uid == a.account.groupUid } }.takeIf { it.isNotEmpty() }?.let { group("Без группы", it) })
            return WebAccounts(MoneyFormatter.format(visible.filter { it > 0 }.sum(), main.code),
                MoneyFormatter.format(visible.filter { it < 0 }.sum(), main.code), MoneyFormatter.format(visible.sum(), main.code), rows)
        }
    }
}
