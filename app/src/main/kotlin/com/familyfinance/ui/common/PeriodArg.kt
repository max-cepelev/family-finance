package com.familyfinance.ui.common

import com.familyfinance.domain.model.Period
import java.time.LocalDate
import java.time.YearMonth

/** A [Period] as a navigation argument: "M:2026-10", "W:2026-10-26", "Y:2026", "C:2026-10-01:2026-10-31". */
object PeriodArg {
    fun encode(period: Period): String = when (period) {
        is Period.Month -> "M:${period.month}"
        is Period.Week -> "W:${period.monday}"
        is Period.Year -> "Y:${period.year}"
        is Period.Custom -> "C:${period.from}:${period.to}"
    }

    /** The current month for anything unreadable. */
    fun decode(text: String?): Period = runCatching {
        val parts = text.orEmpty().split(':')
        when (parts[0]) {
            "M" -> Period.Month(YearMonth.parse(parts[1]))
            "W" -> Period.Week.containing(LocalDate.parse(parts[1]))
            "Y" -> Period.Year(parts[1].toInt())
            "C" -> Period.Custom.between(LocalDate.parse(parts[1]), LocalDate.parse(parts[2]))
            else -> null
        }
    }.getOrNull() ?: Period.Month(YearMonth.now())
}
