package com.familyfinance.domain.receipt

import com.familyfinance.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The QR code printed on a Russian cash receipt: `t=20261026T1218&s=392.97&fn=…&i=…&fp=…&n=1`.
 * [fn], [fd] and [fp] identify the receipt at the tax service, so they are kept as printed.
 */
data class ReceiptQr(
    /** Cash register time, local to the shop. */
    val dateTime: LocalDateTime,
    /** Total in kopecks. */
    val amount: Long,
    /** Fiscal storage number: one per cash register, so it stands for the shop. */
    val fn: String,
    /** Fiscal document number ("i"). */
    val fd: String,
    /** Fiscal sign ("fp"). */
    val fp: String,
    /** 1 purchase, 2 refund of a purchase, 3 payout by the shop, 4 refund of a payout. */
    val kind: Int,
    /** "t" as printed, with or without seconds. */
    private val timeText: String,
) {
    /** Money goes out for a purchase and for returning a payout; it comes back otherwise. */
    val type: TransactionType get() = if (kind == 1 || kind == 4) TransactionType.EXPENSE else TransactionType.INCOME

    /** Last digits of [fn], to tell registers apart on screen. */
    val registerTail: String get() = fn.takeLast(4)

    /** One spelling per receipt, so the same receipt scanned twice compares equal. */
    fun canonical(): String =
        "t=$timeText&s=${BigDecimal.valueOf(amount, 2).toPlainString()}&fn=$fn&i=$fd&fp=$fp&n=$kind"

    companion object {
        private val WITH_SECONDS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        private val WITHOUT_SECONDS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")
        private val DIGITS = Regex("""\d{1,20}""")

        /** Null when the text is not a receipt QR code (a link, a product code, a damaged scan). */
        fun parse(raw: String): ReceiptQr? {
            val fields = raw.trim().split('&').mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null else part.substring(0, eq).lowercase() to part.substring(eq + 1)
            }.toMap()
            val t = fields["t"] ?: return null
            val dateTime = time(t) ?: return null
            val amount = fields["s"]?.let(::kopecks) ?: return null
            val fn = fields["fn"]?.takeIf { DIGITS.matches(it) } ?: return null
            val fd = fields["i"]?.takeIf { DIGITS.matches(it) } ?: return null
            val fp = fields["fp"]?.takeIf { DIGITS.matches(it) } ?: return null
            val kind = fields["n"]?.toIntOrNull()?.takeIf { it in 1..4 } ?: 1
            return ReceiptQr(dateTime, amount, fn, fd, fp, kind, t)
        }

        private fun time(text: String): LocalDateTime? = runCatching {
            if (text.length == 15) LocalDateTime.parse(text, WITH_SECONDS) else LocalDateTime.parse(text, WITHOUT_SECONDS)
        }.getOrNull()

        private fun kopecks(text: String): Long? = runCatching {
            BigDecimal(text.replace(',', '.')).movePointRight(2).longValueExact()
        }.getOrNull()?.takeIf { it > 0 }
    }
}
