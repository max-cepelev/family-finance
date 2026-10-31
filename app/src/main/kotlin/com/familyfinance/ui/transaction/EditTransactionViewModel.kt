package com.familyfinance.ui.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.CashRegister
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.model.Split
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.receipt.ReceiptQr
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CashRegisterRepository
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
    /** Emoji of the chosen category, else of its top-level one. */
    val categoryIcon: String? = null,
    val currency: String = "",
    val toCurrency: String = "",
    val needsToAmount: Boolean = false,
    val errors: Set<DraftError> = emptySet(),
    /** Set when the operation is made from, or carries, a scanned receipt. */
    val receipt: ReceiptUi? = null,
    /** Parts of the split being edited; the first one shows the remainder. Empty when not split. */
    val parts: List<PartUi> = emptyList(),
    /** Increments on every "save and add another", so the screen can confirm it. */
    val savedTick: Int = 0,
    val done: Boolean = false,
)

data class PartUi(
    val categoryUid: String?,
    /** "Питание › Фрукты/Овощи"; empty for a part without a category. */
    val label: String,
    /** Leaf name, for listing the parts in one line. */
    val shortName: String,
    val top: Category?,
    val icon: String?,
    /** What the keypad typed; for the first part, the computed remainder. */
    val amountText: String,
    /** In minor units, null while the typed text is not a number. */
    val amount: Long?,
)

data class ReceiptUi(
    /** Last digits of the register's fiscal number. */
    val registerTail: String,
    /** Receipt time, "12:18". */
    val time: String,
    /** The register's shop and category were filled in from an earlier receipt. */
    val known: Boolean,
    /** Save the shop and category for the next receipts from this register. */
    val remember: Boolean,
    /** Label to value, as printed on the receipt: what the tax service needs to find it. */
    val details: List<Pair<String, String>> = emptyList(),
)

private data class Progress(val errors: Set<DraftError>, val savedTick: Int, val done: Boolean)

@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val registers: CashRegisterRepository,
) : ViewModel() {

    private val uid: String? = savedState.get<String>(ARG_UID)?.takeIf { it.isNotEmpty() }

    /** A new operation from a scanned receipt; known before anything loads, so the screen can lay out for it. */
    /** A new operation started from an account's screen is on that account. */
    private val presetAccount: String? = savedState.get<String>(ARG_ACCOUNT)?.takeIf { it.isNotEmpty() }
    private val scanned: ReceiptQr? = savedState.get<String>(ARG_RECEIPT)?.let(ReceiptQr::parse)?.takeIf { uid == null }
    val fromReceipt: Boolean get() = scanned != null
    private val registerKnown = MutableStateFlow(false)
    private val remember = MutableStateFlow(true)
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
        combine(
            combine(draft, registerKnown, remember) { d, known, keep -> Triple(d, known, keep) },
            accounts, categories, frequentUids, progress,
        ) { (d, known, keep), accountList, categoryList, frequent, p ->
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
                categoryIcon = names.get(d.categoryUid)?.icon ?: names.top(d.categoryUid)?.icon,
                currency = d.currency(accountsByUid).orEmpty(),
                toCurrency = byUid[d.toAccountUid]?.account?.currencyCode.orEmpty(),
                needsToAmount = d.needsToAmount(accountsByUid),
                errors = p.errors,
                parts = partsUi(d, names, decimalsOf(currencies.value)(d.currency(accountsByUid) ?: "RUB")),
                receipt = d.receipt?.let(ReceiptQr::parse)?.let { qr ->
                    ReceiptUi(
                        qr.registerTail, TIME.format(qr.dateTime), known, keep,
                        details = listOf(
                            "Дата" to DATE_TIME.format(qr.dateTime),
                            "Сумма" to TransactionDraft.format(qr.amount, 2) + " ₽",
                            "ФН" to qr.fn,
                            "ФД" to qr.fd,
                            "ФП" to qr.fp,
                        ),
                    )
                },
                savedTick = p.savedTick,
                done = p.done,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditTransactionUiState(isNew = uid == null))

    init {
        viewModelScope.launch {
            val existing = uid?.let { transactions.get(it) }
            val initial = if (existing != null) {
                val decimals = decimalsOf(accountRepository.observeCurrencies().first())
                val parts = existing.splitUid?.let { transactions.splitParts(it) }.orEmpty()
                if (parts.size >= 2) {
                    TransactionDraft.from(Split.whole(parts), decimals).copy(
                        parts = parts.map { PartDraft(it.categoryUid, TransactionDraft.format(it.amount, decimals(it.currencyCode))) },
                        partUids = parts.map { it.uid },
                    )
                } else {
                    TransactionDraft.from(existing, decimals)
                }
            } else if (scanned != null) {
                draftFrom(scanned)
            } else {
                TransactionDraft(date = System.currentTimeMillis(), accountUid = presetAccount ?: transactions.lastUsedAccountUid())
            }
            draft.value = initial
            loadFrequent(initial.type)
        }
    }

    fun setType(type: TransactionType) {
        val current = draft.value ?: return
        if (current.type == type) return
        // Categories are per type, so the old one never fits the new type, nor do the parts' ones.
        draft.value = current.copy(
            type = type,
            categoryUid = null,
            parts = emptyList(),
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

    /** Starts the split editor with the current category as the part that takes the remainder. */
    fun openSplit() = edit { if (it.parts.isEmpty()) it.copy(parts = listOf(PartDraft(it.categoryUid))) else it }

    /** Leaves the split editor; a single part is no split, it is just the category. */
    fun closeSplit() = edit { if (it.parts.size == 1) it.copy(categoryUid = it.parts[0].categoryUid, parts = emptyList()) else it }

    fun addPart(categoryUid: String?) {
        edit { it.copy(parts = it.parts + PartDraft(categoryUid)) }
        clearErrors(DraftError.SPLIT)
    }

    fun setPartCategory(index: Int, categoryUid: String?) = edit { d ->
        d.copy(parts = d.parts.mapIndexed { i, p -> if (i == index) p.copy(categoryUid = categoryUid) else p })
    }

    fun removePart(index: Int) {
        if (index == 0) return
        edit { d -> d.copy(parts = d.parts.filterIndexed { i, _ -> i != index }) }
        clearErrors(DraftError.SPLIT)
    }

    fun pressPartKey(index: Int, key: Char) {
        if (index == 0) return
        edit { d -> d.copy(parts = d.parts.mapIndexed { i, p -> if (i == index) p.copy(amountText = Calculator.press(p.amountText, key)) else p }) }
        clearErrors(DraftError.SPLIT)
    }

    fun clearPart(index: Int) = edit { d ->
        d.copy(parts = d.parts.mapIndexed { i, p -> if (i == index) p.copy(amountText = "") else p })
    }

    /** For a receipt attached to the wrong operation; takes effect on save. */
    fun detachReceipt() = edit { it.copy(receipt = null) }

    fun setRemember(value: Boolean) {
        remember.value = value
    }

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
                if (!saveParts(d, result.transaction)) return@launch
                rememberRegister(result.transaction)
                if (addAnother) {
                    draft.value = d.copy(
                        amountText = "", toAmountText = "", note = "", original = null, currencyCode = null,
                        source = null, receipt = null, parts = emptyList(), partUids = emptyList(),
                    )
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
        val parts = draft.value?.partUids.orEmpty()
        viewModelScope.launch {
            if (parts.isEmpty()) transactions.delete(uid) else transactions.saveAll(emptyList(), deleteUids = parts)
            progress.update { it.copy(done = true) }
        }
    }

    /** Writes the operation, as parts when split. False when the parts do not add up, with the error shown. */
    private suspend fun saveParts(d: TransactionDraft, whole: Transaction): Boolean {
        val writes = d.writes(whole, decimalsOf(currencies.value)(whole.currencyCode))
        if (writes == null) {
            progress.update { it.copy(errors = it.errors + DraftError.SPLIT) }
            return false
        }
        transactions.saveAll(writes.save, deleteUids = writes.delete)
        return true
    }

    private fun partsUi(d: TransactionDraft, names: CategoryNames, decimals: Int): List<PartUi> {
        if (d.parts.isEmpty()) return emptyList()
        val typed = d.parts.map { TransactionDraft.parseAmount(it.amountText, decimals) }
        val total = TransactionDraft.parseAmount(d.amountText, decimals) ?: 0L
        val remainder = Split.remainder(total, typed.drop(1).map { it ?: 0L })
        return d.parts.mapIndexed { i, p ->
            PartUi(
                categoryUid = p.categoryUid,
                label = names.label(p.categoryUid).orEmpty(),
                shortName = names.get(p.categoryUid)?.name ?: "Без категории",
                top = names.top(p.categoryUid),
                icon = names.get(p.categoryUid)?.icon ?: names.top(p.categoryUid)?.icon,
                amountText = if (i == 0) TransactionDraft.format(remainder, decimals) else p.amountText,
                amount = if (i == 0) remainder else typed[i],
            )
        }
    }

    private suspend fun draftFrom(qr: ReceiptQr): TransactionDraft {
        val register = registers.get(qr.fn)
        registerKnown.value = register != null
        return TransactionDraft(
            type = qr.type,
            amountText = TransactionDraft.format(qr.amount, 2),
            date = qr.dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            accountUid = transactions.lastUsedAccountUid(),
            categoryUid = register?.categoryUid,
            note = register?.payee.orEmpty(),
            source = Transaction.SOURCE_RECEIPT,
            receipt = qr.canonical(),
        )
    }

    /** The shop name is what identifies a register to the user, so nothing is remembered without one. */
    private suspend fun rememberRegister(tx: Transaction) {
        val qr = scanned ?: return
        if (!remember.value || tx.note.isBlank()) return
        registers.save(CashRegister(qr.fn, tx.note, tx.categoryUid, updatedAt = 0L, deviceId = ""))
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
        const val ARG_RECEIPT = "receipt"
        const val ARG_ACCOUNT = "account"
        private val TIME = java.time.format.DateTimeFormatter.ofPattern("H:mm")
        private val DATE_TIME = java.time.format.DateTimeFormatter.ofPattern("d.MM.yyyy H:mm")
        private const val FREQUENT_COUNT = 6
    }
}
