package com.familyfinance.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/** Amounts are stored as Long in minor units (kopecks); these helpers convert from/to decimals. */
object Money {

    /** "1234.5" with 2 decimals -> 123450. Accepts comma as decimal separator. */
    fun parseMinor(text: String, decimals: Int): Long =
        toMinor(BigDecimal(text.trim().replace(',', '.')), decimals)

    fun toMinor(value: Double, decimals: Int): Long = toMinor(BigDecimal.valueOf(value), decimals)

    fun toMinor(value: BigDecimal, decimals: Int): Long =
        value.movePointRight(decimals).setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun toDecimal(minor: Long, decimals: Int): BigDecimal = BigDecimal.valueOf(minor, decimals)
}
