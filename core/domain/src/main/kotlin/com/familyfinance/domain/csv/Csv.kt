package com.familyfinance.domain.csv

import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Semicolon-separated values as Russian Excel and banks write them: quotes around fields, "" for a quote. */
object Csv {
    const val BOM = '﻿'

    fun parse(text: String, delimiter: Char = ';'): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = if (text.startsWith(BOM)) 1 else 0
        fun endField() { row.add(field.toString()); field.clear() }
        fun endRow() {
            endField()
            if (row.size > 1 || row[0].isNotEmpty()) rows.add(row)
            row = ArrayList()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == delimiter -> endField()
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    endRow()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** One line, quoting only fields that need it. */
    fun line(fields: List<String>, delimiter: Char = ';'): String = fields.joinToString(delimiter.toString()) { f ->
        if (f.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }) "\"" + f.replace("\"", "\"\"") + "\"" else f
    }

    private val DATE_TIME = listOf(
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm"),
    )
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** "26.10.2026 07:48", with seconds or without a time (then midnight); null if not a date. */
    fun dateTime(text: String): LocalDateTime? {
        val t = text.trim()
        DATE_TIME.forEach { f -> runCatching { return LocalDateTime.parse(t, f) } }
        return runCatching { java.time.LocalDate.parse(t, DATE).atStartOfDay() }.getOrNull()
    }

    /** "1 330,78", "1330.78", "-52 400" in minor units of a 2-decimal currency; null if not a number. */
    fun amount(text: String): Long? {
        // Banks group thousands with ordinary, no-break or narrow spaces.
        val t = text.filterNot { it.isWhitespace() || it == '\u00A0' || it == '\u202F' }.replace('\u2212', '-').replace(',', '.')
        if (t.isEmpty()) return null
        return runCatching { BigDecimal(t).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact() }.getOrNull()
    }
}
