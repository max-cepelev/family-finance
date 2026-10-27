package com.familyfinance.ui.csv

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.csv.AppCsv
import com.familyfinance.domain.csv.CsvOperation
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.CategoryNames
import com.familyfinance.ui.common.Dates
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportPeriod { MONTH, YEAR, ALL, CUSTOM }

data class ExportChoice(
    val period: ExportPeriod = ExportPeriod.YEAR,
    val month: YearMonth = YearMonth.now(),
    val year: Int = LocalDate.now().year,
    val from: LocalDate = LocalDate.now().withDayOfMonth(1),
    val to: LocalDate = LocalDate.now(),
    /** Null for all accounts. */
    val accountUid: String? = null,
)

data class ExportUiState(
    val choice: ExportChoice = ExportChoice(),
    val accounts: List<Account> = emptyList(),
    /** Operations the file will hold. */
    val count: Int = 0,
    /** First lines of the file, as a sample. */
    val sample: List<String> = emptyList(),
    val firstYear: Int = LocalDate.now().year,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CsvExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactions: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val choice = MutableStateFlow(ExportChoice())
    private val message = MutableStateFlow<String?>(null)
    private val firstYear = MutableStateFlow(LocalDate.now().year)
    private val zone = ZoneId.systemDefault()

    private val selected = choice.flatMapLatest { c ->
        val (from, to) = range(c)
        transactions.observeBetween(from, to).map { list -> list.filter { c.accountUid == null || it.accountUid == c.accountUid || it.toAccountUid == c.accountUid } }
    }

    val state: StateFlow<ExportUiState> = combine(
        choice, selected, accountRepository.observeAccounts(), categoryRepository.observeAll(),
        combine(message, firstYear) { m, y -> m to y },
    ) { c, list, accounts, categories, (m, year) ->
        val names = CategoryNames(categories)
        val byUid = accounts.associateBy { it.uid }
        ExportUiState(
            choice = c,
            accounts = accounts.filter { !it.hidden },
            count = list.size,
            sample = AppCsv.write(list.take(2).map { operation(it, byUid, names) }).removePrefix("﻿").lines().filter { it.isNotEmpty() },
            firstYear = year,
            message = m,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportUiState())

    init {
        viewModelScope.launch {
            transactions.firstDate()?.let { firstYear.value = Instant.ofEpochMilli(it).atZone(zone).year }
        }
    }

    fun setPeriod(value: ExportPeriod) = choice.update { it.copy(period = value) }
    fun setMonth(value: YearMonth) = choice.update { it.copy(month = value) }
    fun setYear(value: Int) = choice.update { it.copy(year = value) }
    fun setFrom(value: LocalDate) = choice.update { it.copy(from = value, to = maxOf(value, it.to)) }
    fun setTo(value: LocalDate) = choice.update { it.copy(to = value, from = minOf(value, it.from)) }
    fun setAccount(uid: String?) = choice.update { it.copy(accountUid = uid) }
    fun messageShown() { message.value = null }

    /** "budget-2026.csv", "budget-2026-10.csv", "budget.csv". */
    fun fileName(): String {
        val c = choice.value
        return when (c.period) {
            ExportPeriod.MONTH -> "budget-${c.month}.csv"
            ExportPeriod.YEAR -> "budget-${c.year}.csv"
            ExportPeriod.ALL -> "budget.csv"
            ExportPeriod.CUSTOM -> "budget-${c.from}-${c.to}.csv"
        }
    }

    fun write(uri: Uri) {
        viewModelScope.launch {
            message.value = try {
                val list = selected.first().sortedBy { it.date }
                val names = CategoryNames(categoryRepository.observeAll().first())
                // Hidden accounts too: old operations still name them.
                val byUid = accountRepository.observeAccounts().first().associateBy { it.uid }
                val text = AppCsv.write(list.map { operation(it, byUid, names) })
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("файл недоступен")
                }
                "Сохранено: ${Dates.operations(list.size)}"
            } catch (e: Exception) {
                "Не удалось сохранить: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    private fun range(c: ExportChoice): Pair<Long, Long> {
        fun start(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (c.period) {
            ExportPeriod.MONTH -> start(c.month.atDay(1)) to start(c.month.plusMonths(1).atDay(1))
            ExportPeriod.YEAR -> start(LocalDate.of(c.year, 1, 1)) to start(LocalDate.of(c.year + 1, 1, 1))
            ExportPeriod.ALL -> Long.MIN_VALUE to Long.MAX_VALUE
            ExportPeriod.CUSTOM -> start(c.from) to start(c.to.plusDays(1))
        }
    }

    private fun operation(tx: Transaction, accounts: Map<String, Account>, names: CategoryNames): CsvOperation {
        val category = names.get(tx.categoryUid)
        val top = names.top(tx.categoryUid)
        return CsvOperation(
            dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(tx.date), zone),
            type = tx.type,
            amount = kotlin.math.abs(tx.amount),
            currency = tx.currencyCode,
            account = accounts[tx.accountUid]?.name.orEmpty(),
            toAccount = tx.toAccountUid?.let { accounts[it]?.name },
            category = top?.name,
            subcategory = category?.takeIf { it.uid != top?.uid }?.name,
            note = tx.note,
        )
    }
}
