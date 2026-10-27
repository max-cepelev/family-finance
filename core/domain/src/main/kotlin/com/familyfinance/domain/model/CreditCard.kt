package com.familyfinance.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What a credit card account shows: the debt, what is left of the limit, the next payment date. */
object CreditCard {

    /** A card balance below zero is what is owed; above zero, nothing is. */
    fun debt(balance: Long): Long = (-balance).coerceAtLeast(0)

    /** The limit minus the debt; money put on top of the debt adds to it. */
    fun available(limit: Long, balance: Long): Long = (limit + balance).coerceAtLeast(0)

    /** The payment day on or after [today]; a day past the month's end falls on its last day. */
    fun nextPayment(day: Int, today: LocalDate): LocalDate {
        val thisMonth = onDay(today, day)
        return if (thisMonth.isBefore(today)) onDay(today.plusMonths(1), day) else thisMonth
    }

    fun daysUntil(date: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    private fun onDay(month: LocalDate, day: Int): LocalDate = month.withDayOfMonth(day.coerceIn(1, month.lengthOfMonth()))
}
