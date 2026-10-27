package com.familyfinance.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.BalanceAdjustment
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.transaction.TransactionDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AccountForm(
    val name: String = "",
    val groupUid: String? = null,
    val currencyCode: String = "RUB",
    val note: String = "",
    val hidden: Boolean = false,
    /** New account only: typed opening balance. */
    val openingBalance: String = "",
    val isCredit: Boolean = false,
    /** Credit limit as typed. */
    val creditLimit: String = "",
    val paymentDay: Int? = null,
)

data class AccountEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val form: AccountForm = AccountForm(),
    val groups: List<AccountGroup> = emptyList(),
    /** Currencies offered for a new or still empty account. */
    val currencies: List<String> = emptyList(),
    val balance: Long = 0,
    val operations: Int = 0,
    val nameError: Boolean = false,
    /** Delete was asked for an account with operations: offer hiding instead. */
    val deleteBlocked: Boolean = false,
    val done: Boolean = false,
) {
    val currencyLocked: Boolean get() = !isNew && operations > 0
}

private data class Flags(val nameError: Boolean = false, val deleteBlocked: Boolean = false, val done: Boolean = false)

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {

    private val uid: String? = savedState.get<String>(ARG_UID)?.takeIf { it.isNotEmpty() }
    private val form = MutableStateFlow<AccountForm?>(null)
    private val flags = MutableStateFlow(Flags())
    private val operations = MutableStateFlow(0)
    private var stored: Account? = null

    val state: StateFlow<AccountEditUiState> = combine(
        form, accounts.observeGroups(), accounts.observeCurrencies(), accounts.observeAccountsWithBalance(),
        combine(flags, operations) { f, n -> f to n },
    ) { f, groups, currencies, balances, (fl, ops) ->
        if (f == null) return@combine AccountEditUiState(isNew = uid == null, done = fl.done)
        AccountEditUiState(
            loading = false,
            isNew = uid == null,
            form = f,
            groups = groups.filter { !it.archived || it.uid == f.groupUid },
            currencies = (currencies.map { it.code } + COMMON_CURRENCIES).distinct(),
            balance = balances.firstOrNull { it.account.uid == uid }?.balance ?: 0L,
            operations = ops,
            nameError = fl.nameError,
            deleteBlocked = fl.deleteBlocked,
            done = fl.done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountEditUiState(isNew = uid == null))

    init {
        viewModelScope.launch {
            val existing = uid?.let { id -> accounts.observeAccounts().first().firstOrNull { it.uid == id } }
            stored = existing
            form.value = if (existing != null) {
                operations.value = accounts.countTransactions(existing.uid)
                AccountForm(
                    existing.name, existing.groupUid, existing.currencyCode, existing.note, existing.hidden,
                    isCredit = existing.isCredit,
                    creditLimit = if (existing.creditLimit > 0) TransactionDraft.format(existing.creditLimit, 2) else "",
                    paymentDay = existing.paymentDay,
                )
            } else {
                val main = accounts.observeCurrencies().first().firstOrNull { it.isMain }?.code ?: "RUB"
                AccountForm(currencyCode = main)
            }
        }
    }

    fun setName(value: String) {
        edit { it.copy(name = value) }
        flags.update { it.copy(nameError = false) }
    }

    fun setNote(value: String) = edit { it.copy(note = value) }
    fun setHidden(value: Boolean) = edit { it.copy(hidden = value) }
    fun pickGroup(uid: String) = edit { it.copy(groupUid = uid) }
    fun pickCurrency(code: String) = edit { it.copy(currencyCode = code) }
    fun setOpeningBalance(value: String) = edit { it.copy(openingBalance = value) }
    fun setCredit(value: Boolean) = edit { it.copy(isCredit = value) }
    fun setCreditLimit(value: String) = edit { it.copy(creditLimit = value) }
    fun pickPaymentDay(day: Int?) = edit { it.copy(paymentDay = day) }

    fun createGroup(name: String) {
        val trimmed = name.trim().ifEmpty { return }
        viewModelScope.launch {
            val order = (accounts.observeGroups().first().maxOfOrNull { it.sortOrder } ?: 0) + 1
            val group = AccountGroup(UUID.randomUUID().toString(), trimmed, order, updatedAt = 0, deviceId = "")
            accounts.saveGroup(group)
            pickGroup(group.uid)
        }
    }

    /** "Изменить остаток": books the difference as an operation without a category. */
    fun adjustBalance(text: String) {
        val account = stored ?: return
        val wanted = TransactionDraft.parseAmount(text, 2) ?: return
        viewModelScope.launch {
            BalanceAdjustment.transaction(account, state.value.balance, wanted, System.currentTimeMillis(), UUID.randomUUID().toString())
                ?.let { transactions.save(it) }
            operations.value = accounts.countTransactions(account.uid)
        }
    }

    fun save() {
        val f = form.value ?: return
        if (f.name.isBlank()) {
            flags.update { it.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            val currencies = accounts.observeCurrencies().first()
            if (currencies.none { it.code == f.currencyCode }) {
                // First account in a currency the database does not know yet.
                accounts.saveCurrency(
                    Currency(
                        uid = f.currencyCode, name = f.currencyCode, symbol = MoneyFormatter.symbol(f.currencyCode), decimals = 2,
                        isMain = currencies.isEmpty(), rateToMain = 1.0, updatedAt = 0, deviceId = "",
                    ),
                )
            }
            val order = stored?.sortOrder ?: ((accounts.observeAccounts().first().maxOfOrNull { it.sortOrder } ?: 0) + 1)
            val account = (stored ?: Account(UUID.randomUUID().toString(), null, "", f.currencyCode, order, updatedAt = 0, deviceId = ""))
                .copy(
                    name = f.name.trim(), groupUid = f.groupUid, currencyCode = f.currencyCode, note = f.note.trim(), hidden = f.hidden,
                    isCredit = f.isCredit,
                    creditLimit = TransactionDraft.parseAmount(f.creditLimit, 2)?.coerceAtLeast(0) ?: 0,
                    paymentDay = f.paymentDay,
                )
            accounts.saveAccount(account)
            if (stored == null) {
                TransactionDraft.parseAmount(f.openingBalance, 2)?.let { opening ->
                    BalanceAdjustment.transaction(account, 0, opening, System.currentTimeMillis(), UUID.randomUUID().toString())
                        ?.let { transactions.save(it) }
                }
            }
            flags.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = uid ?: return
        viewModelScope.launch {
            if (accounts.deleteAccount(id)) flags.update { it.copy(done = true) }
            else flags.update { it.copy(deleteBlocked = true) }
        }
    }

    /** From the "can't delete" dialog: hide instead and close. */
    fun hideInstead() {
        flags.update { it.copy(deleteBlocked = false) }
        setHidden(true)
        save()
    }

    fun dismissDeleteBlocked() = flags.update { it.copy(deleteBlocked = false) }

    private fun edit(change: (AccountForm) -> AccountForm) = form.update { it?.let(change) }

    companion object {
        const val ARG_UID = "uid"
        private val COMMON_CURRENCIES = listOf("RUB", "USD", "EUR")
    }
}
