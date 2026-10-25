package com.familyfinance.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.Daily
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.model.Totals
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.CategoryNames
import com.familyfinance.ui.common.SelectedPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class TransactionRowUi(
    val uid: String,
    val type: TransactionType,
    val title: String,
    val subtitle: String,
    val amount: Long,
    val currencyCode: String,
    /** Top-level category: its first letter and color mark the row. Null for transfers. */
    val avatarName: String?,
    val avatarKey: String?,
    /** Emoji of the operation's category, else of its top-level category. */
    val avatarIcon: String? = null,
    /** Added from a bank notification. */
    val fromPush: Boolean = false,
    /** From a notification and still without a category: shown for review. */
    val needsCategory: Boolean = false,
)

data class DayUi(val date: LocalDate, val totals: Totals, val rows: List<TransactionRowUi>)

data class TransactionsUiState(
    val month: YearMonth,
    val loading: Boolean = true,
    val mainCurrency: String = "RUB",
    val totals: Totals = Totals(),
    val days: List<DayUi> = emptyList(),
    /** Operations from notifications without a category, newest first, over all months. */
    val toReview: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val period: SelectedPeriod,
) : ViewModel() {

    private val month = period.month
    val focusDay: StateFlow<LocalDate?> = period.focusDay

    val state: StateFlow<TransactionsUiState> = month.flatMapLatest { month ->
        val zone = ZoneId.systemDefault()
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        combine(
            transactions.observeBetween(from, to),
            accounts.observeAccounts(),
            categories.observeAll(),
            accounts.observeCurrencies(),
            transactions.observeUncategorizedFromPush(),
        ) { items, accountList, categoryList, currencies, toReview ->
            val main = MainCurrency.from(currencies)
            val names = CategoryNames(categoryList)
            val accountsByUid = accountList.associateBy { it.uid }
            TransactionsUiState(
                toReview = toReview,
                month = month,
                loading = false,
                mainCurrency = main.code,
                totals = Daily.totals(items, main),
                days = Daily.group(items, zone, main).map { day ->
                    DayUi(day.date, day.totals, day.transactions.map { row(it, names, accountsByUid) })
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState(month.value))

    fun showMonth(value: YearMonth) = period.show(value)

    fun focusConsumed() = period.focusConsumed()

    private fun row(tx: Transaction, names: CategoryNames, accounts: Map<String, Account>): TransactionRowUi {
        val account = accounts[tx.accountUid]?.name ?: "?"
        val (title, subtitle) = when (tx.type) {
            TransactionType.TRANSFER -> {
                val to = accounts[tx.toAccountUid]?.name ?: "?"
                val title = tx.note.ifBlank { "Перевод" }
                return TransactionRowUi(tx.uid, tx.type, title, "$account → $to", tx.amount, tx.currencyCode, null, null)
            }
            else -> {
                val category = names.label(tx.categoryUid) ?: "Без категории"
                if (tx.note.isBlank()) category to account else tx.note to "$category · $account"
            }
        }
        val top = names.top(tx.categoryUid)
        val icon = names.get(tx.categoryUid)?.icon ?: top?.icon
        val fromPush = tx.source == Transaction.SOURCE_PUSH
        return TransactionRowUi(
            tx.uid, tx.type, title, subtitle, tx.amount, tx.currencyCode, top?.name, top?.uid, icon,
            fromPush = fromPush, needsCategory = fromPush && tx.categoryUid == null,
        )
    }
}
