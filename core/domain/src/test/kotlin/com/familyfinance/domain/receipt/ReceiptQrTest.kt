package com.familyfinance.domain.receipt

import com.familyfinance.domain.model.TransactionType
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Made-up fiscal numbers in the real format.
class ReceiptQrTest {
    @Test
    fun parsesPurchase() {
        val qr = ReceiptQr.parse("t=20261026T1218&s=392.97&fn=7380440700076549&i=12345&fp=1234567890&n=1")!!
        assertEquals(LocalDateTime.of(2026, 10, 26, 12, 18), qr.dateTime)
        assertEquals(39297L, qr.amount)
        assertEquals("7380440700076549", qr.fn)
        assertEquals("12345", qr.fd)
        assertEquals("1234567890", qr.fp)
        assertEquals(TransactionType.EXPENSE, qr.type)
        assertEquals("6549", qr.registerTail)
    }

    @Test
    fun canonicalIgnoresFieldOrderAndAmountSpelling() {
        val a = ReceiptQr.parse("t=20261026T121805&s=1254.3&fn=9960440300000001&i=77&fp=42&n=1")!!
        val b = ReceiptQr.parse("fn=9960440300000001&fp=42&i=77&n=1&s=1254.30&t=20261026T121805")!!
        assertEquals(a.canonical(), b.canonical())
        assertEquals("t=20261026T121805&s=1254.30&fn=9960440300000001&i=77&fp=42&n=1", a.canonical())
        assertEquals(LocalDateTime.of(2026, 10, 26, 12, 18, 5), a.dateTime)
    }

    @Test
    fun refundIsIncomeAndMissingKindIsPurchase() {
        assertEquals(TransactionType.INCOME, ReceiptQr.parse("t=20261026T1218&s=10&fn=1&i=2&fp=3&n=2")!!.type)
        assertEquals(TransactionType.EXPENSE, ReceiptQr.parse("t=20261026T1218&s=10&fn=1&i=2&fp=3")!!.type)
        assertEquals(1000L, ReceiptQr.parse("t=20261026T1218&s=10&fn=1&i=2&fp=3")!!.amount)
    }

    @Test
    fun rejectsOtherCodes() {
        assertNull(ReceiptQr.parse("https://example.com/?t=1"))
        assertNull(ReceiptQr.parse("4601234567890"))
        assertNull(ReceiptQr.parse("t=20261026T1218&s=392.97&fn=73804&i=1"))
        assertNull(ReceiptQr.parse("t=2026-10-26&s=392.97&fn=1&i=2&fp=3&n=1"))
        assertNull(ReceiptQr.parse("t=20261026T1218&s=0&fn=1&i=2&fp=3&n=1"))
    }
}
