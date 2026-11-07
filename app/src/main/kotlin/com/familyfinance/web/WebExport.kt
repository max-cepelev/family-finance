package com.familyfinance.web

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class WebExportPeriod {
    MONTH, YEAR, ALL;

    fun range(month: YearMonth, zone: ZoneId): Pair<Long, Long> {
        fun start(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (this) {
            MONTH -> start(month.atDay(1)) to start(month.plusMonths(1).atDay(1))
            YEAR -> start(LocalDate.of(month.year, 1, 1)) to start(LocalDate.of(month.year + 1, 1, 1))
            ALL -> Long.MIN_VALUE to Long.MAX_VALUE
        }
    }

    fun fileName(month: YearMonth): String = when (this) {
        MONTH -> "budget-$month.csv"
        YEAR -> "budget-${month.year}.csv"
        ALL -> "budget.csv"
    }
}

data class WebExport(val fileName: String, val text: String)
