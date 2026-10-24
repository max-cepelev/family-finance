package com.familyfinance.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.AccountWithBalance
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AccountGroupUi(
    val name: String,
    val accounts: List<AccountWithBalance>,
    /** Sum of the group in the main currency. */
    val total: Long,
)

data class AccountsUiState(
    val loading: Boolean = true,
    val mainCurrency: String = "RUB",
    val assets: Long = 0,
    val liabilities: Long = 0,
    val groups: List<AccountGroupUi> = emptyList(),
) {
    val total: Long get() = assets + liabilities
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    repository: AccountRepository,
) : ViewModel() {

    val state: StateFlow<AccountsUiState> = combine(
        repository.observeGroups(),
        repository.observeAccountsWithBalance(),
        repository.observeCurrencies(),
    ) { groups, accounts, currencies ->
        val main = MainCurrency.from(currencies)
        fun inMain(item: AccountWithBalance): Long = main.convert(item.balance, item.account.currencyCode)

        val visible = accounts.filterNot { it.account.hidden }
        val byGroup = accounts.groupBy { it.account.groupUid }
        val ordered = groups.mapNotNull { group ->
            byGroup[group.uid]?.let { AccountGroupUi(group.name, it, it.filterNot { a -> a.account.hidden }.sumOf(::inMain)) }
        } + listOfNotNull(
            byGroup.filterKeys { key -> groups.none { it.uid == key } }.values.flatten()
                .takeIf { it.isNotEmpty() }
                ?.let { AccountGroupUi("Без группы", it, it.sumOf(::inMain)) },
        )
        AccountsUiState(
            loading = false,
            mainCurrency = main.code,
            assets = visible.map(::inMain).filter { it > 0 }.sum(),
            liabilities = visible.map(::inMain).filter { it < 0 }.sum(),
            groups = ordered,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())
}
