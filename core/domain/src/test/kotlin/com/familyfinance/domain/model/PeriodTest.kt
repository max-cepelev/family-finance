package com.familyfinance.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class PeriodTest {
    private val zone = ZoneId.of("Asia/Yekaterinburg")

    @Test fun `week runs Monday to Monday, across years too`() {
        val week = Period.Week.containing(LocalDate.of(2021, 1, 1)) // a Friday
        assertEquals(LocalDate.of(2020, 12, 28), week.start)
        assertEquals(LocalDate.of(2021, 1, 4), week.endExclusive)
        assertEquals(7L, week.days)
        assertEquals(LocalDate.of(2021, 1, 4), week.next().start)
    }

    @Test fun `month and year step by their own length`() {
        assertEquals(Period.Month(YearMonth.of(2021, 1)), Period.Month(YearMonth.of(2021, 2)).previous())
        assertEquals(28L, Period.Month(YearMonth.of(2021, 2)).days)
        assertEquals(366L, Period.Year(2020).days)
        assertEquals(Period.Year(2021), Period.Year(2020).next())
    }

    @Test fun `custom includes both picked days and has no neighbours`() {
        val q1 = Period.Custom.between(LocalDate.of(2021, 3, 31), LocalDate.of(2021, 1, 1))
        assertEquals(LocalDate.of(2021, 1, 1), q1.start)
        assertEquals(90L, q1.days)
        assertNull(q1.next())
    }

    @Test fun `presets are relative to today`() {
        val today = LocalDate.of(2026, 10, 24)
        assertEquals(Period.Custom(LocalDate.of(2026, 7, 25), today), Period.Custom.lastThreeMonths(today))
        assertEquals(Period.Custom(LocalDate.of(2026, 1, 1), today), Period.Custom.yearToDate(today))
        assertEquals(Period.Custom(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)), Period.Custom.lastYear(today))
        assertEquals(Period.Custom(LocalDate.of(2018, 1, 1), today), Period.Custom.allTime(LocalDate.of(2018, 1, 1), today))
    }

    @Test fun `millis cover local midnights`() {
        val (from, to) = Period.Month(YearMonth.of(2021, 2)).millis(zone)
        assertEquals(LocalDate.of(2021, 2, 1).atStartOfDay(zone).toInstant().toEpochMilli(), from)
        assertEquals(LocalDate.of(2021, 3, 1).atStartOfDay(zone).toInstant().toEpochMilli(), to)
    }
}
