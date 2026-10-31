package com.familyfinance.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.AccountWithBalance
import com.familyfinance.domain.model.Daily
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.CategoryNames
import com.familyfinance.ui.transactions.DayUi
import com.familyfinance.ui.transactions.rows
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class AccountOperationsUiState(
    val month: YearMonth,
    val loading: Boolean = true,
    /** Null once the account is gone (deleted on this or the other phone). */
    val account: AccountWithBalance? = null,
    /** Name of its group: "Банковская карта". */
    val groupName: String = "",
    val mainCurrency: String = "RUB",
    val days: List<DayUi> = emptyList(),
)

/** One account: its balance and its operations by month, transfers to and from it included. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountOperationsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
) : ViewModel() {
    val uid: String = checkNotNull(savedState.get<String>(ARG_UID))
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<AccountOperationsUiState> = month.flatMapLatest { shown ->
        val zone = ZoneId.systemDefault()
        val from = shown.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = shown.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        combine(
            transactions.observeBetween(from, to),
            accounts.observeAccountsWithBalance(),
            accounts.observeGroups(),
            categories.observeAll(),
            accounts.observeCurrencies(),
        ) { items, balances, groups, categoryList, currencies ->
            val main = MainCurrency.from(currencies)
            val mine = items.filter { it.accountUid == uid || it.toAccountUid == uid }
            val account = balances.firstOrNull { it.account.uid == uid }
            val names = CategoryNames(categoryList)
            val byUid = balances.associate { it.account.uid to it.account }
            AccountOperationsUiState(
                month = shown,
                loading = false,
                account = account,
                groupName = groups.firstOrNull { it.uid == account?.account?.groupUid }?.name.orEmpty(),
                mainCurrency = main.code,
                days = Daily.group(mine, zone, main).map { day -> DayUi(day.date, day.totals, rows(day.transactions, names, byUid)) },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountOperationsUiState(month.value))

    fun showMonth(value: YearMonth) {
        month.value = value
    }

    companion object {
        const val ARG_UID = "uid"
    }
}
