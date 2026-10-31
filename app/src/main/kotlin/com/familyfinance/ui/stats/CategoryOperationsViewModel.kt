package com.familyfinance.ui.stats

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.BudgetState
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryColors
import com.familyfinance.domain.model.Daily
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.model.Period
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.BudgetRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.CategoryNames
import com.familyfinance.ui.common.PeriodArg
import com.familyfinance.ui.common.categoryColor
import com.familyfinance.ui.transactions.DayUi
import com.familyfinance.ui.transactions.rows
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class CategoryOperationsUiState(
    val period: Period,
    val loading: Boolean = true,
    /** Null once the category is gone. */
    val category: Category? = null,
    val color: Color = Color.Gray,
    val mainCurrency: String = "RUB",
    /** Spent (or received) in the period, main currency. */
    val total: Long = 0,
    /** The category's budget against the period; only for a month and the whole category. */
    val budget: BudgetState? = null,
    val days: List<DayUi> = emptyList(),
)

/** Operations of one category over a statistics period: with its subcategories, or [ARG_ONLY] the category itself. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryOperationsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val budgets: BudgetRepository,
) : ViewModel() {
    val uid: String = checkNotNull(savedState.get<String>(ARG_UID))
    private val only: Boolean = savedState.get<Boolean>(ARG_ONLY) ?: false
    private val period = MutableStateFlow(PeriodArg.decode(savedState.get<String>(ARG_PERIOD)))

    val state: StateFlow<CategoryOperationsUiState> = period.flatMapLatest { shown ->
        val zone = ZoneId.systemDefault()
        val (from, to) = shown.millis(zone)
        combine(
            transactions.observeBetween(from, to),
            categories.observeAll(),
            accounts.observeAccounts(),
            accounts.observeCurrencies(),
            budgets.observeAll(),
        ) { items, categoryList, accountList, currencies, budgetList ->
            val main = MainCurrency.from(currencies)
            val category = categoryList.firstOrNull { it.uid == uid && !it.deleted }
            val included = if (only) setOf(uid) else setOf(uid) + categoryList.filter { it.parentUid == uid }.map { it.uid }
            val mine = items.filter { it.type != TransactionType.TRANSFER && it.categoryUid in included }
            val totals = Daily.totals(mine, main)
            val total = if (category?.type == com.familyfinance.domain.model.CategoryType.INCOME) totals.income else totals.expense
            val names = CategoryNames(categoryList)
            val byUid = accountList.associateBy { it.uid }
            CategoryOperationsUiState(
                period = shown,
                loading = false,
                category = category,
                color = categoryColor(CategoryColors.resolve(categoryList)[uid]),
                mainCurrency = main.code,
                total = total,
                budget = budgetList.takeIf { shown is Period.Month && !only }
                    ?.firstOrNull { it.categoryUid == uid }?.let { BudgetState(it, total) },
                days = Daily.group(mine, zone, main).map { day -> DayUi(day.date, day.totals, rows(day.transactions, names, byUid)) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryOperationsUiState(period.value))

    fun previous() {
        period.value.previous()?.let { period.value = it }
    }

    fun next() {
        period.value.next()?.let { period.value = it }
    }

    companion object {
        const val ARG_UID = "uid"
        const val ARG_ONLY = "only"
        const val ARG_PERIOD = "period"
    }
}
