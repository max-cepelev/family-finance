package com.familyfinance.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** A span of calendar days for statistics: [start] inclusive, [endExclusive] exclusive. */
sealed interface Period {
    val start: LocalDate
    val endExclusive: LocalDate

    /** The same kind of period before/after this one; null for a custom period, which has no neighbours. */
    fun previous(): Period?
    fun next(): Period?

    val days: Long get() = ChronoUnit.DAYS.between(start, endExclusive)

    /** Epoch millis `[from, to)` of this period in [zone], for querying transactions. */
    fun millis(zone: ZoneId): Pair<Long, Long> =
        start.atStartOfDay(zone).toInstant().toEpochMilli() to endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Monday to Sunday. */
    data class Week(val monday: LocalDate) : Period {
        init { require(monday.dayOfWeek == DayOfWeek.MONDAY) { "$monday is not a Monday" } }
        override val start get() = monday
        override val endExclusive: LocalDate get() = monday.plusWeeks(1)
        override fun previous() = Week(monday.minusWeeks(1))
        override fun next() = Week(monday.plusWeeks(1))

        companion object {
            fun containing(date: LocalDate) = Week(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        }
    }

    data class Month(val month: YearMonth) : Period {
        override val start: LocalDate get() = month.atDay(1)
        override val endExclusive: LocalDate get() = month.plusMonths(1).atDay(1)
        override fun previous() = Month(month.minusMonths(1))
        override fun next() = Month(month.plusMonths(1))
    }

    data class Year(val year: Int) : Period {
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val endExclusive: LocalDate get() = LocalDate.of(year + 1, 1, 1)
        override fun previous() = Year(year - 1)
        override fun next() = Year(year + 1)
    }

    /** Both days included, as the user picks them. */
    data class Custom(val from: LocalDate, val to: LocalDate) : Period {
        init { require(!to.isBefore(from)) { "$to is before $from" } }
        override val start get() = from
        override val endExclusive: LocalDate get() = to.plusDays(1)
        override fun previous(): Period? = null
        override fun next(): Period? = null

        companion object {
            /** In whatever order the two days were picked. */
            fun between(a: LocalDate, b: LocalDate) = if (b.isBefore(a)) Custom(b, a) else Custom(a, b)

            fun lastThreeMonths(today: LocalDate) = Custom(today.minusMonths(3).plusDays(1), today)
            fun yearToDate(today: LocalDate) = Custom(today.withDayOfYear(1), today)
            fun lastYear(today: LocalDate) = Custom(LocalDate.of(today.year - 1, 1, 1), LocalDate.of(today.year - 1, 12, 31))
            /** From the first operation (or today when there are none) to today. */
            fun allTime(firstDay: LocalDate?, today: LocalDate) = between(firstDay ?: today, today)
        }
    }
}
