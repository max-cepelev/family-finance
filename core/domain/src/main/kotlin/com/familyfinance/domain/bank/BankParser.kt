package com.familyfinance.domain.bank

/** Banks whose push notifications are understood. [code] is stored and synced; never change it. */
enum class Bank(val code: String, val packageName: String, val title: String) {
    SBER("sber", "ru.sberbankmobile", "Сбер"),
    TBANK("tbank", "com.idamob.tinkoff.android", "Т-Банк"),
    RAIF("raif", "ru.raiffeisennews", "Райффайзен");

    companion object {
        fun byPackage(packageName: String?): Bank? = entries.firstOrNull { it.packageName == packageName }
        fun byCode(code: String?): Bank? = entries.firstOrNull { it.code == code }
    }
}

/** A money movement read from a notification. */
data class BankEvent(
    val bank: Bank,
    val income: Boolean,
    /** Kopecks, always positive. */
    val amount: Long,
    /** Shop or counterparty, "" when the notification has none. */
    val payee: String,
    /** Last four digits of the card or account, null when the notification has none (T-Bank pushes). */
    val card: String?,
)

/**
 * Text rules for push notifications of Russian banks, checked on real notifications
 * (see docs/bank-formats.md). Pure Kotlin, so they are tested off-device.
 */
object BankParser {
    private const val AMT = """(\d{1,3}(?: \d{3})+|\d+)(?:[.,](\d{1,2}))?"""
    private const val CUR = """(?:₽|RUB|руб\.?|р\.)"""

    // ➕ 10 ₽ по СБП от ИМЯ Ф. 💳 10 ₽ •• 2824 — amount in the title, the body is the balance.
    private val SBER = Regex("""([➕➖])\s*$AMT\s*$CUR\s*(.*?)\s*(?:💳|$)""")
    private val SBER_CARD = Regex("""••\s*(\d{4})""")

    // Заплатили картой *5790 - 17.00 ₽ в MARMALATO 086. Теперь на карте 14 025.10 ₽
    private val RAIF = Regex("""\*(\d{4})\s*([+-])\s*$AMT\s*$CUR\s*(.*?)(?=\s*[Тт]еперь на|$)""")

    // Райффайзенбанк Пополнение на 10 ₽, счет RUB. Имя Ф. Доступно 10 ₽ (and the older SMS wording)
    private val TBANK = Regex(
        """(Пополнение|Зачисление|Поступление|Возврат|Перевод|Покупка|Оплата|Списание|Снятие|Платеж|Платёж)""" +
            """[^0-9₽]*?$AMT\s*$CUR(.*?)(?:Доступно|Баланс|$)""",
    )
    private val TBANK_INCOME = setOf("Пополнение", "Зачисление", "Поступление", "Возврат")
    private val ANY_CARD = Regex("""[*•]{1,2}\s*(\d{4})""")

    private val NOT_TX = Regex(
        """никому не (?:говорите|сообщайте)|\bкод\b|\bkod\b|отказ|отклонен|не удалось|недостаточно средств""",
        RegexOption.IGNORE_CASE,
    )

    /** [title] and [text] as the notification carries them; null when it is not a money movement. */
    fun parse(bank: Bank, title: String?, text: String?): BankEvent? =
        parseText(bank, listOfNotNull(title, text).joinToString(" "))

    fun parseText(bank: Bank, raw: String): BankEvent? {
        val text = normalize(raw)
        if (text.isEmpty() || NOT_TX.containsMatchIn(text)) return null
        return when (bank) {
            Bank.SBER -> sber(text)
            Bank.RAIF -> raif(text)
            Bank.TBANK -> tbank(text)
        }
    }

    private fun sber(text: String): BankEvent? {
        val m = SBER.find(text) ?: return null
        val (sign, whole, fraction, payee) = m.destructured
        return BankEvent(Bank.SBER, sign == "➕", amount(whole, fraction), cleanPayee(payee), SBER_CARD.find(text)?.groupValues?.get(1))
    }

    private fun raif(text: String): BankEvent? {
        val m = RAIF.find(text) ?: return null
        val (card, sign, whole, fraction, payee) = m.destructured
        return BankEvent(Bank.RAIF, sign == "+", amount(whole, fraction), cleanPayee(payee), card)
    }

    private fun tbank(text: String): BankEvent? {
        val m = TBANK.find(text) ?: return null
        val (kind, whole, fraction, rest) = m.destructured
        // The push puts the counterparty bank in the title, right before the operation kind.
        val payee = cleanPayee(rest).ifEmpty { cleanPayee(text.substring(0, m.range.first)) }
        return BankEvent(Bank.TBANK, kind in TBANK_INCOME, amount(whole, fraction), payee, ANY_CARD.find(text)?.groupValues?.get(1))
    }

    internal fun normalize(s: String): String = s
        .replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')
        .replace('−', '-').replace('–', '-')
        .replace(Regex("""^\s*null\b"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** "1 349", "50" -> 134950 kopecks. */
    private fun amount(whole: String, fraction: String): Long =
        whole.replace(" ", "").toLong() * 100 + fraction.padEnd(2, '0').ifEmpty { "00" }.toLong()

    internal fun cleanPayee(s: String): String {
        var p = s
            .replace(Regex("""^[\s,.]*сч[её]т\s+RUB[.,]?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""по номеру телефона""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""через СБП|по СБП""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\+?\d[\d ()-]{9,}\d"""), "")
            .replace(Regex("""^[\s,.]*(?:в|от|на)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""^[\s,.:-]+|[\s,:-]+$"""), "")
        // Drop a trailing sentence dot but keep initials like "Максим Ц."
        if (p.endsWith(".") && !Regex(""".*\s\p{Lu}\.$""").matches(p)) p = p.dropLast(1).trim()
        return p
    }
}
