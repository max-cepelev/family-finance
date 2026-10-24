package com.familyfinance.ui.common

import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The month shown on Operations, Calendar and Statistics, shared like in Money Manager. */
@Singleton
class SelectedPeriod @Inject constructor() {
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /** Day the Operations list should scroll to once, set from the calendar. */
    private val _focusDay = MutableStateFlow<LocalDate?>(null)
    val focusDay: StateFlow<LocalDate?> = _focusDay.asStateFlow()

    fun show(month: YearMonth) {
        _month.value = month
    }

    fun focus(day: LocalDate) {
        _month.value = YearMonth.from(day)
        _focusDay.value = day
    }

    fun focusConsumed() {
        _focusDay.value = null
    }
}
