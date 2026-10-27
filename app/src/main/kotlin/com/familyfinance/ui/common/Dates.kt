package com.familyfinance.ui.common

import com.familyfinance.domain.model.Period
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Dates {
    val locale: Locale = Locale.forLanguageTag("ru-RU")
    private val monthTitle = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale)
    private val dayMonthWeekday = DateTimeFormatter.ofPattern("d MMMM, EE", locale)
    private val fullDate = DateTimeFormatter.ofPattern("d MMMM yyyy, EE", locale)
    private val timeOfDay = DateTimeFormatter.ofPattern("H:mm", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)

    fun month(month: YearMonth): String = monthTitle.format(month).replaceFirstChar { it.titlecase(locale) }

    fun monthName(month: Int): String =
        java.time.Month.of(month).getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

    fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)

    /** "25 февраля, четверг". */
    fun dayWithWeekday(date: LocalDate): String = "${dayMonth.format(date)}, ${weekday(date)}"

    /** "Сегодня, 28 февраля", "Вчера, …", "12 марта, пт" this year, "12 марта 2021, пт" otherwise. */
    fun relative(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return when {
            date == today -> "Сегодня, ${dayMonth.format(date)}"
            date == today.minusDays(1) -> "Вчера, ${dayMonth.format(date)}"
            date.year == today.year -> dayMonthWeekday.format(date)
            else -> fullDate.format(date)
        }
    }

    /** "янв", "мая": genitive short months, without the trailing dot CLDR adds. */
    private val SHORT_MONTHS = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

    private fun short(date: LocalDate) = "${date.dayOfMonth} ${SHORT_MONTHS[date.monthValue - 1]}"

    /** "25 ноя". */
    fun dayMonthShort(date: LocalDate): String = short(date)

    /** "18–24 янв 2021", "29 мар – 4 апр 2021", "28 дек 2020 – 3 янв 2021". Both days included. */
    fun range(from: LocalDate, to: LocalDate): String = when {
        from == to -> "${short(from)} ${from.year}"
        from.year != to.year -> "${short(from)} ${from.year} – ${short(to)} ${to.year}"
        from.month == to.month -> "${from.dayOfMonth}–${short(to)} ${to.year}"
        else -> "${short(from)} – ${short(to)} ${to.year}"
    }

    /** Header title of a statistics period. */
    fun period(period: Period): String = when (period) {
        is Period.Month -> month(period.month)
        is Period.Year -> period.year.toString()
        is Period.Week -> range(period.start, period.endExclusive.minusDays(1))
        is Period.Custom -> range(period.from, period.to)
    }

    /** "18 марта 2021". */
    fun dayMonthYear(date: LocalDate): String = dayMonthYear.format(date)

    /** Russian plural: plural(5, "операция", "операции", "операций") -> "5 операций". */
    fun plural(count: Int, one: String, few: String, many: String): String {
        val word = when {
            count % 100 in 11..14 -> many
            count % 10 == 1 -> one
            count % 10 in 2..4 -> few
            else -> many
        }
        return "${java.text.NumberFormat.getIntegerInstance(locale).format(count)} $word"
    }

    fun operations(count: Int): String = plural(count, "операция", "операции", "операций")

    /** "1 день", "3 дня", "5 дней". */
    fun days(count: Long): String = plural(count.toInt(), "день", "дня", "дней")

    /** "только что", "5 минут назад", "сегодня в 14:32", "вчера в 9:05", "12 марта в 18:40". */
    fun ago(epochMillis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val minutes = ((now - epochMillis) / 60_000).toInt()
        if (minutes < 1) return "только что"
        if (minutes < 60) return plural(minutes, "минуту", "минуты", "минут") + " назад"
        return moment(epochMillis, now, zone)
    }

    /** "сегодня в 14:32", "вчера в 9:05", "12 марта в 18:40", "20 марта 2023 в 21:51". */
    fun moment(epochMillis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val clock = timeOfDay.format(time)
        return when (time.toLocalDate()) {
            today -> "сегодня в $clock"
            today.minusDays(1) -> "вчера в $clock"
            else -> {
                val day = if (time.year == today.year) dayMonth.format(time) else dayMonthYear.format(time)
                "$day в $clock"
            }
        }
    }
}
