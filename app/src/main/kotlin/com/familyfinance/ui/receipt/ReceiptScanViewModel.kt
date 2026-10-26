package com.familyfinance.ui.receipt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.CashRegister
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.receipt.ReceiptQr
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CashRegisterRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.CategoryNames
import com.familyfinance.ui.transactions.TransactionRowUi
import com.familyfinance.ui.transactions.transactionRow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A scanned receipt that looks like an operation already recorded, usually one from a bank notification. */
data class ReceiptMatchUi(
    val receipt: String,
    val registerTail: String,
    /** "Сегодня, 12:18"-style time of the receipt. */
    val time: String,
    val amount: Long,
    val isRefund: Boolean,
    val candidate: TransactionRowUi,
    /** Null when the operation has no payee to remember the register by. */
    val rememberHint: String?,
    val remember: Boolean,
)

sealed interface ReceiptEvent {
    /** No operation fits: make a new one from the receipt. */
    data class OpenForm(val receipt: String) : ReceiptEvent
    /** A short message; [openUid] adds an "Открыть" action for that operation. */
    data class Notice(val text: String, val openUid: String? = null) : ReceiptEvent
}

@HiltViewModel
class ReceiptScanViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val registers: CashRegisterRepository,
) : ViewModel() {
    private val _match = MutableStateFlow<ReceiptMatchUi?>(null)
    val match: StateFlow<ReceiptMatchUi?> = _match
    private var matched: Transaction? = null

    private val _events = Channel<ReceiptEvent>(Channel.BUFFERED)
    val events: Flow<ReceiptEvent> = _events.receiveAsFlow()

    fun onScanned(raw: String) {
        viewModelScope.launch {
            val qr = ReceiptQr.parse(raw)
            if (qr == null) {
                _events.send(ReceiptEvent.Notice("Это не QR-код кассового чека"))
                return@launch
            }
            val receipt = qr.canonical()
            transactions.findByReceipt(receipt)?.let {
                _events.send(ReceiptEvent.Notice("Этот чек уже добавлен", openUid = it.uid))
                return@launch
            }
            val zone = ZoneId.systemDefault()
            val at = qr.dateTime.atZone(zone).toInstant().toEpochMilli()
            val candidate = transactions.receiptCandidates(qr.type, qr.amount, at, at - MATCH_WINDOW, at + MATCH_WINDOW).firstOrNull()
            if (candidate == null) {
                _events.send(ReceiptEvent.OpenForm(receipt))
                return@launch
            }
            val names = CategoryNames(categories.observeAll().first())
            val byUid = accounts.observeAccounts().first().associateBy { it.uid }
            val row = transactionRow(candidate, names, byUid)
            val time = TIME.format(Instant.ofEpochMilli(candidate.date).atZone(zone))
            matched = candidate
            _match.value = ReceiptMatchUi(
                receipt = receipt,
                registerTail = qr.registerTail,
                time = TIME.format(qr.dateTime),
                amount = qr.amount,
                isRefund = qr.kind != 1,
                candidate = row.copy(subtitle = "${row.subtitle} · $time"),
                rememberHint = candidate.note.takeIf { it.isNotBlank() }?.let { payee ->
                    val category = names.label(candidate.categoryUid)
                    "Следующие чеки с неё — сразу «$payee»" + (category?.let { ", $it" } ?: "")
                },
                remember = candidate.note.isNotBlank(),
            )
        }
    }

    fun scannerFailed() {
        viewModelScope.launch { _events.send(ReceiptEvent.Notice("Сканер не открылся. Попробуйте ещё раз через минуту")) }
    }

    fun setRemember(value: Boolean) = _match.update { it?.copy(remember = value) }

    fun attach() {
        val m = _match.value ?: return
        val tx = matched ?: return
        viewModelScope.launch {
            transactions.save(tx.copy(receipt = m.receipt))
            val fn = ReceiptQr.parse(m.receipt)?.fn
            if (m.remember && fn != null && tx.note.isNotBlank()) {
                registers.save(CashRegister(fn, tx.note, tx.categoryUid, updatedAt = 0L, deviceId = ""))
            }
            close()
            _events.send(ReceiptEvent.Notice("Чек прикреплён к операции", openUid = tx.uid))
        }
    }

    fun otherPurchase() {
        val m = _match.value ?: return
        close()
        viewModelScope.launch { _events.send(ReceiptEvent.OpenForm(m.receipt)) }
    }

    fun dismiss() = close()

    private fun close() {
        _match.value = null
        matched = null
    }

    private companion object {
        /** A notification comes within a minute or two of the purchase; the register clock may be a bit off. */
        const val MATCH_WINDOW = 10 * 60 * 1000L
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
    }
}
