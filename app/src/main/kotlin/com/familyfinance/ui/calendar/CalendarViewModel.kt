package com.familyfinance.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.model.Daily
import com.familyfinance.domain.model.MainCurrency
import com.familyfinance.domain.model.Totals
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.SelectedPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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

data class CalendarDay(val date: LocalDate, val totals: Totals, val count: Int)

data class CalendarUiState(
    val month: YearMonth,
    val loading: Boolean = true,
    val mainCurrency: String = "RUB",
    val totals: Totals = Totals(),
    /** Only days with operations. */
    val days: Map<LocalDate, CalendarDay> = emptyMap(),
    val selected: LocalDate? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val period: SelectedPeriod,
) : ViewModel() {

    private val selected = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<CalendarUiState> = period.month.flatMapLatest { month ->
        val zone = ZoneId.systemDefault()
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        combine(transactions.observeBetween(from, to), accounts.observeCurrencies(), selected) { items, currencies, picked ->
            val main = MainCurrency.from(currencies)
            val days = Daily.group(items, zone, main).associate { it.date to CalendarDay(it.date, it.totals, it.transactions.size) }
            val today = LocalDate.now(zone)
            CalendarUiState(
                month = month,
                loading = false,
                mainCurrency = main.code,
                totals = Daily.totals(items, main),
                days = days,
                // Default: today when it is in this month, otherwise nothing until a day is tapped.
                selected = picked?.takeIf { YearMonth.from(it) == month }
                    ?: today.takeIf { YearMonth.from(it) == month },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState(period.month.value))

    fun showMonth(month: YearMonth) = period.show(month)

    fun select(date: LocalDate) {
        selected.value = date
    }

    /** Makes the Operations tab scroll to [date]. */
    fun openDay(date: LocalDate) = period.focus(date)
}
