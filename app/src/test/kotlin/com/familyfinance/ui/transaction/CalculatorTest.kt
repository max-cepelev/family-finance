package com.familyfinance.ui.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalculatorTest {
    private fun eval(text: String) = Calculator.evaluate(text)?.stripTrailingZeros()?.toPlainString()

    private fun type(keys: String) = keys.fold("") { expr, key -> Calculator.press(expr, key) }

    @Test fun `evaluates with precedence and commas`() {
        assertEquals("165.5", eval("120+45,5"))
        assertEquals("14", eval("2+3×4"))
        assertEquals("4", eval("10-2×3"))
        assertEquals("33.33333333333333", eval("100÷3")) // 16 significant digits, plenty for money
        assertEquals("-0.15", eval("-0,15"))
        assertEquals("1234.5", eval("1 234,5"))
        assertEquals("1234.5", eval("1234.5"))
    }

    @Test fun `trailing operator is ignored, nonsense gives null`() {
        assertEquals("120", eval("120+"))
        assertNull(eval(""))
        assertNull(eval("5÷0"))
        assertNull(eval("abc"))
    }

    @Test fun `typing rules`() {
        assertEquals("120+45,5", type("120+45,5"))
        assertEquals("12×3", type("12+×3"))          // a new operator replaces the previous one
        assertEquals("0,5", type(",5"))               // comma first means "0,"
        assertEquals("1,25", type("1,2,57"))          // one comma per number, two decimals max
        assertEquals("7", type("07"))                 // no leading zeros
        assertEquals("", type("+"))                   // nothing to add to yet
        assertEquals("-5", type("-5"))
        assertEquals("12", type("123⌫"))
    }

    @Test fun `shows the result only for an actual operation`() {
        assertTrue(Calculator.hasOperation("120+45"))
        assertFalse(Calculator.hasOperation("120+"))
        assertFalse(Calculator.hasOperation("-5"))
        assertEquals("120 + 45,5 − 3 × 2", Calculator.display("120+45,5-3×2"))
        assertEquals("−0,15", Calculator.display("-0,15"))
    }

    @Test fun `draft accepts an expression as the amount`() {
        assertEquals(16550L, TransactionDraft.parseAmount("120+45,5", 2))
        assertEquals(BigDecimal("0.5"), Calculator.evaluate("1÷2")?.stripTrailingZeros())
    }
}
