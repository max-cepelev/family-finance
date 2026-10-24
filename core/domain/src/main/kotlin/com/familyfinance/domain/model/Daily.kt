package com.familyfinance.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/** Converts amounts to the main currency with the stored rates. */
class MainCurrency(val code: String, private val rates: Map<String, Double>) {
    fun convert(amount: Long, currencyCode: String): Long =
        if (currencyCode == code) amount else (amount * (rates[currencyCode] ?: 1.0)).roundToLong()

    companion object {
        fun from(currencies: List<Currency>): MainCurrency = MainCurrency(
            code = currencies.firstOrNull { it.isMain }?.code ?: currencies.firstOrNull()?.code ?: "RUB",
            rates = currencies.associate { it.code to it.rateToMain },
        )
    }
}

/** Income and expense of a period in the main currency; transfers move money but are not counted. */
data class Totals(val income: Long = 0, val expense: Long = 0) {
    val net: Long get() = income - expense
}

data class DayGroup(
    val date: LocalDate,
    /** Newest first. */
    val transactions: List<Transaction>,
    val totals: Totals,
)

object Daily {

    fun totals(transactions: Iterable<Transaction>, main: MainCurrency): Totals {
        var income = 0L
        var expense = 0L
        for (tx in transactions) {
            if (tx.deleted) continue
            when (tx.type) {
                TransactionType.INCOME -> income += main.convert(tx.amount, tx.currencyCode)
                TransactionType.EXPENSE -> expense += main.convert(tx.amount, tx.currencyCode)
                TransactionType.TRANSFER -> Unit
            }
        }
        return Totals(income, expense)
    }

    /** Groups by local calendar day, newest day first. */
    fun group(transactions: Iterable<Transaction>, zone: ZoneId, main: MainCurrency): List<DayGroup> =
        transactions
            .filterNot { it.deleted }
            .groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
            .toSortedMap(reverseOrder())
            .map { (date, items) ->
                DayGroup(date, items.sortedByDescending { it.date }, totals(items, main))
            }
}
