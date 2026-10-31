package com.familyfinance.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.budget.BudgetWarnings
import com.familyfinance.domain.model.Budget
import com.familyfinance.domain.model.BudgetState
import com.familyfinance.domain.model.Budgets
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.BudgetRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.transaction.TransactionDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A budget as the list shows it; [category] is null for "Все расходы". */
data class BudgetUi(val state: BudgetState, val category: Category?)

/** The budget sheet as typed. */
data class BudgetEditUi(
    /** Null for a new budget. */
    val uid: String?,
    /** Null: all expenses. */
    val categoryUid: String?,
    val amountText: String,
    val warn: Boolean = true,
    val amountError: Boolean = false,
)

data class BudgetsUiState(
    val month: YearMonth,
    val loading: Boolean = true,
    val mainCurrency: String = "RUB",
    val overall: BudgetUi? = null,
    val byCategory: List<BudgetUi> = emptyList(),
    /** Top-level expense categories, for "На что". */
    val categories: List<Category> = emptyList(),
    val edit: BudgetEditUi? = null,
    /** What the chosen target took last month, a hint for the limit. */
    val lastMonth: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgets: BudgetRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val warnings: BudgetWarnings,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val edit = MutableStateFlow<BudgetEditUi?>(null)

    val state: StateFlow<BudgetsUiState> = month.flatMapLatest { shown ->
        combine(
            combine(budgets.observeAll(), monthItems(shown), monthItems(shown.minusMonths(1))) { a, b, c -> Triple(a, b, c) },
            categories.observeAll(),
            accounts.observeCurrencies(),
            edit,
        ) { (list, items, previous), cats, currencies, editing ->
            val main = MainCurrency.from(currencies)
            val byUid = cats.associateBy { it.uid }
            val states = Budgets.progress(list, items, cats, main).map { BudgetUi(it, it.budget.categoryUid?.let(byUid::get)) }
            val lastMonth = editing?.let { e ->
                Budgets.progress(listOf(Budget("hint", e.categoryUid, 1, updatedAt = 0, deviceId = "")), previous, cats, main).first().spent
            }
            BudgetsUiState(
                month = shown,
                loading = false,
                mainCurrency = main.code,
                overall = states.firstOrNull { it.budget().categoryUid == null },
                byCategory = states.filter { it.budget().categoryUid != null }.sortedByDescending { it.state.share },
                categories = cats.filter { it.type == CategoryType.EXPENSE && it.parentUid == null && !it.archived && !it.deleted }.sortedBy { it.sortOrder },
                edit = editing,
                lastMonth = lastMonth,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState(month.value))

    private fun BudgetUi.budget() = state.budget

    private fun monthItems(month: YearMonth): Flow<List<Transaction>> {
        val zone = ZoneId.systemDefault()
        return transactions.observeBetween(
            month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    fun showMonth(value: YearMonth) {
        month.value = value
    }

    fun openNew() {
        // "Все расходы" first, unless it already has a budget.
        val taken = state.value.overall != null
        edit.value = BudgetEditUi(uid = null, categoryUid = if (taken) state.value.categories.firstOrNull()?.uid else null, amountText = "")
    }

    fun openEdit(budget: Budget) {
        edit.value = BudgetEditUi(budget.uid, budget.categoryUid, TransactionDraft.format(budget.amount, 2), budget.warn)
    }

    fun setTarget(categoryUid: String?) = edit.update { it?.copy(categoryUid = categoryUid) }
    fun setAmount(text: String) = edit.update { it?.copy(amountText = text, amountError = false) }
    fun setWarn(value: Boolean) = edit.update { it?.copy(warn = value) }
    fun dismiss() = edit.update { null }

    fun save() {
        val e = edit.value ?: return
        val amount = TransactionDraft.parseAmount(e.amountText, 2)
        if (amount == null || amount <= 0) {
            edit.update { it?.copy(amountError = true) }
            return
        }
        viewModelScope.launch {
            // One budget per target: picking a category that has one edits that one.
            val existing = budgets.observeAll().first().firstOrNull { it.uid == e.uid || (e.uid == null && it.categoryUid == e.categoryUid) }
            val budget = (existing ?: Budget(UUID.randomUUID().toString(), e.categoryUid, amount, updatedAt = 0, deviceId = ""))
                .copy(categoryUid = e.categoryUid, amount = amount, warn = e.warn)
            budgets.save(budget)
            // Whatever this month has already reached is not news.
            val now = YearMonth.now()
            val main = MainCurrency.from(accounts.observeCurrencies().first())
            warnings.markReached(Budgets.progress(listOf(budget), monthItems(now).first(), categories.observeAll().first(), main).first())
            edit.value = null
        }
    }

    fun delete() {
        val uid = edit.value?.uid ?: return
        viewModelScope.launch {
            budgets.delete(uid)
            edit.value = null
        }
    }
}
