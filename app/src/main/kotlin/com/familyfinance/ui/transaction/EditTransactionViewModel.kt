package com.familyfinance.ui.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.CategoryNames
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditTransactionUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val draft: TransactionDraft? = null,
    /** Accounts offered in pickers: visible ones plus whatever the draft already uses. */
    val accounts: List<Account> = emptyList(),
    /** Categories of the draft's type offered in the picker. */
    val categories: List<Category> = emptyList(),
    /** Quick picks: the most used categories of the draft's type. */
    val frequent: List<Category> = emptyList(),
    val accountName: String = "",
    /** Balance of the chosen account, in its currency (minor units). */
    val accountBalance: Long? = null,
    val accountCurrency: String = "",
    val toAccountName: String = "",
    val categoryName: String = "",
    /** Top-level category, for the avatar next to the category field. */
    val categoryTop: Category? = null,
    val currency: String = "",
    val toCurrency: String = "",
    val needsToAmount: Boolean = false,
    val errors: Set<DraftError> = emptySet(),
    /** Increments on every "save and add another", so the screen can confirm it. */
    val savedTick: Int = 0,
    val done: Boolean = false,
)

private data class Progress(val errors: Set<DraftError>, val savedTick: Int, val done: Boolean)

@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val uid: String? = savedState.get<String>(ARG_UID)?.takeIf { it.isNotEmpty() }
    private val draft = MutableStateFlow<TransactionDraft?>(null)
    private val progress = MutableStateFlow(Progress(emptySet(), 0, false))
    private val frequentUids = MutableStateFlow<List<String>>(emptyList())

    private val currencies = accountRepository.observeCurrencies()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val accounts = accountRepository.observeAccountsWithBalance()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val categories = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<EditTransactionUiState> =
        combine(draft, accounts, categories, frequentUids, progress) { d, accountList, categoryList, frequent, p ->
            if (d == null) return@combine EditTransactionUiState(isNew = uid == null, done = p.done)
            val byUid = accountList.associateBy { it.account.uid }
            val accountsByUid = byUid.mapValues { it.value.account }
            val names = CategoryNames(categoryList)
            val categoryType = if (d.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
            val chosen = byUid[d.accountUid]
            EditTransactionUiState(
                loading = false,
                isNew = uid == null,
                draft = d,
                accounts = accountList.map { it.account }
                    .filter { !it.hidden || it.uid == d.accountUid || it.uid == d.toAccountUid },
                categories = categoryList.filter {
                    it.type == categoryType && (!it.archived || it.uid == d.categoryUid)
                },
                frequent = if (d.type == TransactionType.TRANSFER) emptyList()
                else frequent.mapNotNull { names.get(it) }.filter { it.type == categoryType && !it.archived },
                accountName = chosen?.account?.name.orEmpty(),
                accountBalance = chosen?.balance,
                accountCurrency = chosen?.account?.currencyCode.orEmpty(),
                toAccountName = byUid[d.toAccountUid]?.account?.name.orEmpty(),
                categoryName = names.label(d.categoryUid).orEmpty(),
                categoryTop = names.top(d.categoryUid),
                currency = d.currency(accountsByUid).orEmpty(),
                toCurrency = byUid[d.toAccountUid]?.account?.currencyCode.orEmpty(),
                needsToAmount = d.needsToAmount(accountsByUid),
                errors = p.errors,
                savedTick = p.savedTick,
                done = p.done,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditTransactionUiState(isNew = uid == null))

    init {
        viewModelScope.launch {
            val existing = uid?.let { transactions.get(it) }
            val initial = if (existing != null) {
                TransactionDraft.from(existing, decimalsOf(accountRepository.observeCurrencies().first()))
            } else {
                TransactionDraft(date = System.currentTimeMillis(), accountUid = transactions.lastUsedAccountUid())
            }
            draft.value = initial
            loadFrequent(initial.type)
        }
    }

    fun setType(type: TransactionType) {
        val current = draft.value ?: return
        if (current.type == type) return
        // Categories are per type, so the old one never fits the new type.
        draft.value = current.copy(
            type = type,
            categoryUid = null,
            toAccountUid = current.toAccountUid.takeIf { type == TransactionType.TRANSFER },
        )
        viewModelScope.launch { loadFrequent(type) }
    }

    /** A keypad key for the amount ([toAccount] false) or the credited amount of a transfer. */
    fun pressKey(key: Char, toAccount: Boolean = false) {
        if (toAccount) {
            edit { it.copy(toAmountText = Calculator.press(it.toAmountText, key)) }
            clearErrors(DraftError.TO_AMOUNT)
        } else {
            edit { it.copy(amountText = Calculator.press(it.amountText, key)) }
            clearErrors(DraftError.AMOUNT)
        }
    }

    fun clearAmount(toAccount: Boolean = false) =
        edit { if (toAccount) it.copy(toAmountText = "") else it.copy(amountText = "") }

    fun setNote(text: String) = edit { it.copy(note = text) }

    fun pickAccount(uid: String) {
        // A newly chosen account brings its own currency.
        edit { it.copy(accountUid = uid, currencyCode = null) }
        clearErrors(DraftError.ACCOUNT, DraftError.TO_ACCOUNT, DraftError.AMOUNT)
    }

    fun pickToAccount(uid: String) {
        edit { it.copy(toAccountUid = uid) }
        clearErrors(DraftError.TO_ACCOUNT, DraftError.TO_AMOUNT)
    }

    fun pickCategory(uid: String?) = edit { it.copy(categoryUid = uid) }

    fun setDate(date: LocalDate) = edit {
        val zone = ZoneId.systemDefault()
        val time = Instant.ofEpochMilli(it.date).atZone(zone).toLocalTime()
        it.copy(date = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli())
    }

    fun save() = save(addAnother = false)

    /**
     * Saves and starts the next operation with the same type, date, account and category:
     * entering a receipt item by item only needs the amount and the note.
     */
    fun saveAndAddAnother() = save(addAnother = true)

    private fun save(addAnother: Boolean) {
        val d = draft.value ?: return
        val byUid = accounts.value.associate { it.account.uid to it.account }
        when (val result = d.build(byUid, decimalsOf(currencies.value))) {
            is DraftResult.Invalid -> progress.update { it.copy(errors = result.errors) }
            is DraftResult.Valid -> viewModelScope.launch {
                transactions.save(result.transaction)
                if (addAnother) {
                    draft.value = d.copy(amountText = "", toAmountText = "", note = "", original = null, currencyCode = null)
                    progress.update { it.copy(errors = emptySet(), savedTick = it.savedTick + 1) }
                    loadFrequent(d.type)
                } else {
                    progress.update { it.copy(done = true) }
                }
            }
        }
    }

    fun delete() {
        val uid = uid ?: return
        viewModelScope.launch {
            transactions.delete(uid)
            progress.update { it.copy(done = true) }
        }
    }

    private suspend fun loadFrequent(type: TransactionType) {
        frequentUids.value =
            if (type == TransactionType.TRANSFER) emptyList() else transactions.frequentCategoryUids(type, FREQUENT_COUNT)
    }

    private fun edit(change: (TransactionDraft) -> TransactionDraft) {
        draft.update { it?.let(change) }
    }

    private fun clearErrors(vararg cleared: DraftError) {
        progress.update { it.copy(errors = it.errors - cleared.toSet()) }
    }

    private fun decimalsOf(list: List<Currency>): (String) -> Int {
        val byCode = list.associate { it.code to it.decimals }
        return { code -> byCode[code] ?: 2 }
    }

    companion object {
        const val ARG_UID = "uid"
        private const val FREQUENT_COUNT = 6
    }
}
