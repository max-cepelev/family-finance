package com.familyfinance.ui.transaction

import java.math.BigDecimal
import java.math.MathContext

/**
 * The amount keypad: an expression like "120+45,5" edited key by key and evaluated with the usual
 * precedence (× and ÷ before + and −). Decimal arithmetic, so money never goes through Double.
 */
object Calculator {
    const val PLUS = '+'
    const val MINUS = '-'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    const val COMMA = ','
    private val OPERATORS = setOf(PLUS, MINUS, TIMES, DIVIDE)
    private val SPACES = Regex("[\\s  ]")

    fun isOperator(c: Char) = c in OPERATORS

    /** True when the expression has an operation, i.e. a result worth showing separately. */
    fun hasOperation(expression: String): Boolean =
        expression.drop(1).any(::isOperator) && expression.last().isDigit()

    /** Value of [expression]; a trailing operator is ignored; null when empty or dividing by zero. */
    fun evaluate(expression: String): BigDecimal? {
        val text = expression.replace(SPACES, "").replace('.', COMMA).trimEnd { isOperator(it) }
        if (text.isEmpty()) return null
        val terms = ArrayList<BigDecimal>()
        var current: BigDecimal? = null
        var pending = PLUS
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (isOperator(c)) {
                when {
                    current != null -> pending = c
                    // A leading minus ("-0,15") is a sign: read it as "0 − 0,15".
                    c == MINUS -> { current = BigDecimal.ZERO; pending = MINUS }
                }
                i++
                continue
            }
            val start = i
            while (i < text.length && (text[i].isDigit() || text[i] == COMMA)) i++
            if (start == i) return null
            val number = text.substring(start, i).replace(COMMA, '.').let { if (it.startsWith('.')) "0$it" else it }
                .toBigDecimalOrNull() ?: return null
            current = when {
                current == null -> number
                pending == TIMES -> current.multiply(number)
                pending == DIVIDE -> if (number.signum() == 0) return null else current.divide(number, MathContext.DECIMAL64)
                else -> {
                    terms += current
                    if (pending == MINUS) number.negate() else number
                }
            }
        }
        current?.let(terms::add)
        return terms.fold(BigDecimal.ZERO, BigDecimal::add)
    }

    /** The expression after pressing [key]: a digit, [COMMA], an operator, or [BACKSPACE]. */
    fun press(expression: String, key: Char): String = when {
        key == BACKSPACE -> expression.dropLast(1)
        isOperator(key) -> when {
            expression.isEmpty() -> if (key == MINUS) "-" else expression
            isOperator(expression.last()) -> if (expression.length == 1) expression else expression.dropLast(1) + key
            else -> expression + key
        }
        key == COMMA -> {
            val number = expression.takeLastWhile { !isOperator(it) }
            when {
                COMMA in number -> expression
                number.isEmpty() -> expression + "0,"
                else -> expression + COMMA
            }
        }
        key.isDigit() -> {
            val number = expression.takeLastWhile { !isOperator(it) }
            val decimals = number.substringAfter(COMMA, "")
            when {
                // Kopecks: at most two digits after the comma.
                COMMA in number && decimals.length >= 2 -> expression
                // No "007": a lone leading zero is replaced.
                number == "0" -> expression.dropLast(1) + key
                expression.length >= MAX_LENGTH -> expression
                else -> expression + key
            }
        }
        else -> expression
    }

    /** "120+45,5" as shown: "120 + 45,5", with a real minus sign. */
    fun display(expression: String): String = buildString {
        expression.forEachIndexed { index, c ->
            when {
                isOperator(c) && index == 0 -> append(if (c == MINUS) '−' else c)
                isOperator(c) -> append(' ').append(if (c == MINUS) '−' else c).append(' ')
                else -> append(c)
            }
        }
    }

    const val BACKSPACE = '⌫'
    private const val MAX_LENGTH = 40
}
