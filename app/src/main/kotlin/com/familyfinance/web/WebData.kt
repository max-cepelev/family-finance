package com.familyfinance.web

import com.familyfinance.domain.csv.AppCsv
import com.familyfinance.domain.csv.CsvOperation
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.Daily
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.model.Split
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.CategoryNames
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** One operation as the web page shows it: every field already a string. */
data class WebRow(
    /** What the edit panel opens: the operation, or the first part of a split one. */
    val uid: String,
    /** Emoji or first letter of the category. */
    val icon: String,
    val category: String,
    val note: String,
    /** "чек", "пуш", "выписка", "разделена". */
    val tags: List<String>,
    val account: String,
    val amount: String,
    val type: TransactionType,
)

data class WebDay(val day: Int, val weekday: String, val total: String, val negative: Boolean, val rows: List<WebRow>)

data class WebMonth(
    val month: YearMonth,
    val title: String,
    val income: String,
    val expense: String,
    val net: String,
    val days: List<WebDay>,
    val filter: WebFilter = WebFilter(),
    val filterLabel: String = "",
)

/** Data for the pages, read through the same repositories as the phone screens. */
interface WebData {
    /** Operations of [month] by day, newest first; [query] keeps rows whose category, note or account contains it, and the totals follow. */
    suspend fun month(month: YearMonth, query: String = "", filter: WebFilter = WebFilter()): WebMonth

    suspend fun statistics(month: YearMonth, type: TransactionType, parentUid: String?): WebStatistics
    suspend fun accounts(): WebAccounts
    suspend fun categories(type: CategoryType): WebCategories
    suspend fun export(month: YearMonth, period: WebExportPeriod, accountUid: String?): WebExport
}

class RepositoryWebData @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
) : WebData {

    override suspend fun export(month: YearMonth, period: WebExportPeriod, accountUid: String?): WebExport {
        val zone = ZoneId.systemDefault()
        val (from, to) = period.range(month, zone)
        val names = CategoryNames(categories.observeAll().first())
        val byUid = accounts.observeAccounts().first().associateBy { it.uid }
        val items = WebFilter(account = accountUid).apply(transactions.observeBetween(from, to).first(), emptyList())
        val text = AppCsv.write(items.sortedBy { it.date }.map { tx ->
            val category = names.get(tx.categoryUid)
            val top = names.top(tx.categoryUid)
            CsvOperation(LocalDateTime.ofInstant(Instant.ofEpochMilli(tx.date), zone), tx.type, tx.amount,
                tx.currencyCode, byUid[tx.accountUid]?.name.orEmpty(), tx.toAccountUid?.let { byUid[it]?.name },
                top?.name, category?.takeIf { it.uid != top?.uid }?.name, tx.note)
        })
        return WebExport(period.fileName(month), text)
    }

    override suspend fun categories(type: CategoryType): WebCategories =
        WebCategories.build(type, categories.observeAll().first(), categories.observeUsage().first())

    override suspend fun accounts(): WebAccounts = WebAccounts.build(accounts.observeAccountsWithBalance().first(),
        accounts.observeGroups().first(), MainCurrency.from(accounts.observeCurrencies().first()))

    override suspend fun statistics(month: YearMonth, type: TransactionType, parentUid: String?): WebStatistics {
        val zone = ZoneId.systemDefault()
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return WebStatistics.build(month, type, parentUid, transactions.observeBetween(from, to).first(),
            categories.observeAll().first(), MainCurrency.from(accounts.observeCurrencies().first()))
    }

    override suspend fun month(month: YearMonth, query: String, filter: WebFilter): WebMonth {
        val zone = ZoneId.systemDefault()
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val categoryList = categories.observeAll().first()
        val accountList = accounts.observeAccounts().first()
        return WebMonths.build(
            month = month,
            items = filter.apply(transactions.observeBetween(from, to).first(), categoryList),
            main = MainCurrency.from(accounts.observeCurrencies().first()),
            categories = categoryList,
            accounts = accountList,
            query = query,
            zone = zone,
        ).copy(filter = filter, filterLabel = listOfNotNull(
            filter.account?.let { uid -> accountList.firstOrNull { it.uid == uid }?.name ?: "Неизвестный счёт" },
            filter.category?.let { uid -> CategoryNames(categoryList).label(uid) ?: if (uid.isEmpty()) "Без категории" else "Неизвестная категория" },
        ).joinToString(" · "))
    }
}

/** The page of a month from already loaded data, so it can be checked without a database. */
object WebMonths {
    fun build(
        month: YearMonth,
        items: List<Transaction>,
        main: MainCurrency,
        categories: List<Category>,
        accounts: List<Account>,
        query: String,
        zone: ZoneId,
    ): WebMonth {
        val names = CategoryNames(categories)
        val byUid = accounts.associateBy { it.uid }
        val needle = query.trim().lowercase()
        // Rows the search keeps, with their transactions, so the totals cover only what is shown.
        val found = Daily.group(items, zone, main).map { day ->
            day.date to day.transactions.groupBy { it.splitUid ?: it.uid }.values
                .map { group -> group.sortedBy { it.splitIndex }.let { row(it, names, byUid) to it } }
                .filter { (row, _) -> needle.isEmpty() || listOf(row.category, row.note, row.account).any { f -> needle in f.lowercase() } }
        }.filter { (_, rows) -> rows.isNotEmpty() }
        val totals = Daily.totals(found.flatMap { (_, rows) -> rows.flatMap { it.second } }, main)
        val days = found.map { (date, rows) ->
            val net = Daily.totals(rows.flatMap { it.second }, main).net
            WebDay(date.dayOfMonth, Dates.weekday(date), MoneyFormatter.format(kotlin.math.abs(net), main.code), net < 0, rows.map { it.first })
        }
        return WebMonth(
            month = month,
            title = Dates.month(month),
            income = MoneyFormatter.format(totals.income, main.code),
            expense = MoneyFormatter.format(totals.expense, main.code),
            net = (if (totals.net > 0) "+" else "") + MoneyFormatter.format(totals.net, main.code),
            days = days,
        )
    }

    private fun row(parts: List<Transaction>, names: CategoryNames, accounts: Map<String, Account>): WebRow {
        val tx = if (parts.size > 1) Split.whole(parts) else parts[0]
        val account = accounts[tx.accountUid]?.name ?: "?"
        val top = names.top(tx.categoryUid)
        val (icon, category) = when {
            tx.type == TransactionType.TRANSFER -> "⇄" to "Перевод"
            parts.size > 1 -> "⑂" to parts.joinToString(", ") { names.get(it.categoryUid)?.name ?: "Без категории" }
            else -> (names.get(tx.categoryUid)?.icon ?: top?.icon ?: top?.name?.take(1)?.uppercase() ?: "·") to
                (names.label(tx.categoryUid) ?: "Без категории")
        }
        val tags = listOfNotNull(
            "чек".takeIf { tx.receipt != null },
            "пуш".takeIf { tx.source == Transaction.SOURCE_PUSH },
            "выписка".takeIf { tx.source == Transaction.SOURCE_STATEMENT },
        )
        return WebRow(
            uid = parts[0].uid,
            icon = icon,
            category = category,
            note = tx.note,
            tags = tags,
            account = if (tx.type == TransactionType.TRANSFER) "$account → ${accounts[tx.toAccountUid]?.name ?: "?"}" else account,
            amount = MoneyFormatter.format(tx.amount, tx.currencyCode),
            type = tx.type,
        )
    }
}
