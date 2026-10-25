package com.familyfinance.ui.banks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.data.push.PushSettings
import com.familyfinance.data.push.UnknownCards
import com.familyfinance.domain.bank.Bank
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.BankCard
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.BankCardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CardRow(val card: BankCard, val accountName: String)

data class BankSection(val bank: Bank, val cards: List<CardRow>, val fallback: CardRow?)

/** A card seen in a notification but not linked yet. */
data class UnknownCardUi(val bank: Bank, val last4: String, val recordedAccountUid: String?, val recordedTo: String?)

/** The card panel while open. */
data class CardEditUi(
    val uid: String?,
    val bank: Bank,
    /** Null for the bank's fallback ("Без номера"). */
    val last4: String?,
    val accountUid: String?,
    val digitsError: String? = null,
    val accountError: Boolean = false,
) {
    val isFallback: Boolean get() = last4 == null
}

data class BanksUiState(
    val enabled: Boolean = false,
    val hasAccess: Boolean = false,
    val unknown: List<UnknownCardUi> = emptyList(),
    val sections: List<BankSection> = Bank.entries.map { BankSection(it, emptyList(), null) },
    val accounts: List<Account> = emptyList(),
    val edit: CardEditUi? = null,
)

@HiltViewModel
class BanksViewModel @Inject constructor(
    private val settings: PushSettings,
    private val cards: BankCardRepository,
    accounts: AccountRepository,
    unknownCards: UnknownCards,
) : ViewModel() {

    private val access = MutableStateFlow(settings.hasAccess())
    private val edit = MutableStateFlow<CardEditUi?>(null)

    val state: StateFlow<BanksUiState> = combine(
        combine(settings.enabled, access) { e, a -> e to a },
        cards.observeAll(),
        accounts.observeAccounts(),
        unknownCards.observe(),
        edit,
    ) { (enabled, hasAccess), cardList, accountList, unknown, editing ->
        val names = accountList.associate { it.uid to it.name }
        fun row(c: BankCard) = CardRow(c, names[c.accountUid] ?: "Счёт удалён")
        BanksUiState(
            enabled = enabled,
            hasAccess = hasAccess,
            unknown = unknown.mapNotNull { u ->
                Bank.byCode(u.bank)?.let { UnknownCardUi(it, u.last4, u.recordedAccountUid, u.recordedAccountUid?.let(names::get)) }
            },
            sections = Bank.entries.map { bank ->
                val ofBank = cardList.filter { it.bank == bank.code }
                BankSection(bank, ofBank.filter { it.last4 != null }.map(::row), ofBank.firstOrNull { it.last4 == null }?.let(::row))
            },
            accounts = accountList.filter { !it.hidden },
            edit = editing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BanksUiState())

    /** Called when the screen comes back from system settings. */
    fun refreshAccess() {
        access.value = settings.hasAccess()
    }

    fun setEnabled(value: Boolean) = settings.setEnabled(value)

    fun openNew(bank: Bank, last4: String? = "", fallback: Boolean = false, accountUid: String? = null) {
        edit.value = CardEditUi(uid = null, bank = bank, last4 = if (fallback) null else last4.orEmpty(), accountUid = accountUid)
    }

    fun openExisting(card: BankCard) {
        val bank = Bank.byCode(card.bank) ?: return
        edit.value = CardEditUi(uid = card.uid, bank = bank, last4 = card.last4, accountUid = card.accountUid)
    }

    fun setDigits(value: String) = edit.update { e ->
        e?.copy(last4 = value.filter(Char::isDigit).take(4), digitsError = null)
    }

    fun pickAccount(uid: String) = edit.update { it?.copy(accountUid = uid, accountError = false) }
    fun dismiss() = edit.update { null }

    fun save() {
        val e = edit.value ?: return
        val existing = state.value.sections.first { it.bank == e.bank }
        val digitsError = when {
            e.isFallback -> null
            e.last4!!.length != 4 -> "Нужно 4 цифры"
            existing.cards.any { it.card.last4 == e.last4 && it.card.uid != e.uid } -> "Эта карта уже привязана"
            else -> null
        }
        if (digitsError != null || e.accountUid == null) {
            edit.update { it?.copy(digitsError = digitsError, accountError = e.accountUid == null) }
            return
        }
        viewModelScope.launch {
            // One fallback per bank: editing "Без номера" reuses the stored one.
            val uid = e.uid ?: existing.fallback?.card?.uid?.takeIf { e.isFallback } ?: UUID.randomUUID().toString()
            cards.save(BankCard(uid, e.bank.code, e.last4, e.accountUid, updatedAt = 0, deviceId = ""))
            edit.value = null
        }
    }

    fun delete() {
        val uid = edit.value?.uid ?: return
        viewModelScope.launch {
            cards.delete(uid)
            edit.value = null
        }
    }
}
