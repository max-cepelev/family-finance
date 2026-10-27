package com.familyfinance.domain.csv

import com.familyfinance.domain.bank.Bank
import com.familyfinance.domain.model.TransactionType
import java.time.LocalDateTime

/** One line of a bank statement. */
data class StatementRow(
    val dateTime: LocalDateTime,
    /** INCOME or EXPENSE. */
    val type: TransactionType,
    /** In the account currency, minor units, positive. */
    val amount: Long,
    val currency: String,
    val payee: String,
    /** Last digits of the card, when the statement names one. */
    val card: String?,
    /** The bank's document number: the same operation in the next statement has it too. */
    val reference: String,
)

data class Statement(val bank: Bank, val rows: List<StatementRow>, /** File line numbers that could not be read. */ val skipped: List<Int>) {
    /** "raif:ZR00000002OS38IC": what an imported operation keeps to be recognized in later statements. */
    fun externalId(row: StatementRow): String = "${bank.code}:${row.reference}"
}

/** Statement exports of the banks we know, recognized by their header. */
object BankStatements {
    private val RAIFFEISEN = listOf(
        "Дата операции", "Выполнено банком", "Номер документа",
        "Сумма в валюте операции (поступления)", "Сумма в валюте операции (расходы)", "Валюта операции",
        "Сумма в валюте счета (поступления)", "Сумма в валюте счета (расходы)", "Валюта счета",
        "Детали операции (назначение платежа)", "Номер карты",
    )

    /** Null when the rows are not a statement of a bank we know. */
    fun parse(rows: List<List<String>>): Statement? {
        val header = rows.firstOrNull()?.map { it.trim() } ?: return null
        if (header != RAIFFEISEN) return null
        val parsed = ArrayList<StatementRow>()
        val skipped = ArrayList<Int>()
        rows.drop(1).forEachIndexed { i, r ->
            raiffeisen(r)?.let(parsed::add) ?: skipped.add(i + 2)
        }
        return Statement(Bank.RAIF, parsed, skipped)
    }

    private fun raiffeisen(r: List<String>): StatementRow? {
        if (r.size < RAIFFEISEN.size) return null
        val dateTime = Csv.dateTime(r[0]) ?: return null
        val income = Csv.amount(r[6])?.takeIf { it > 0 }
        val expense = Csv.amount(r[7])?.takeIf { it > 0 }
        val (type, amount) = when {
            income != null -> TransactionType.INCOME to income
            expense != null -> TransactionType.EXPENSE to expense
            else -> return null
        }
        val payee = cleanPayee(r[9])
        // Some transfers come without a document number; what they are is stable enough to stand in for one.
        val reference = r[2].trim().ifEmpty { "auto-$dateTime-$type-$amount-${Integer.toHexString(payee.hashCode())}" }
        return StatementRow(
            dateTime = dateTime,
            type = type,
            amount = amount,
            currency = r[8].trim().ifEmpty { "RUB" },
            payee = payee,
            card = r[10].trim().removePrefix("*").takeIf { it.length == 4 && it.all(Char::isDigit) },
            reference = reference,
        )
    }

    /** "рублевый перевод,Заработная плата за …" reads better without the transfer kind in front. */
    private fun cleanPayee(text: String): String =
        text.trim().replace(Regex("""^(рублевый|валютный)\s+перевод\s*,\s*""", RegexOption.IGNORE_CASE), "").trim()
}
