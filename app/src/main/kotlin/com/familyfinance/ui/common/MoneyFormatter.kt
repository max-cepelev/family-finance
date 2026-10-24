package com.familyfinance.ui.common

import com.familyfinance.domain.model.Money
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object MoneyFormatter {
    private val locale = Locale.forLanguageTag("ru-RU")

    fun format(minor: Long, currencyCode: String, decimals: Int = 2): String {
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(Money.toDecimal(minor, decimals))
        return "$number ${symbol(currencyCode)}"
    }

    /** Whole units without currency, for tight spots like calendar cells: 5527600 -> "55 276". */
    fun compact(minor: Long, decimals: Int = 2): String =
        NumberFormat.getIntegerInstance(locale)
            .format(Money.toDecimal(minor, decimals).setScale(0, RoundingMode.HALF_UP))

    /** Plain editable number with kopecks only when present: 17835774 -> "178357,74", 100000 -> "1000". */
    fun compactExact(minor: Long, decimals: Int = 2): String =
        Money.toDecimal(minor, decimals).stripTrailingZeros().toPlainString().replace('.', ',')

    /** 2134 -> "2 134". */
    fun count(value: Int): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun symbol(code: String): String =
        runCatching { java.util.Currency.getInstance(code).getSymbol(locale) }.getOrDefault(code)
}
