package com.familyfinance.domain.csv

import com.familyfinance.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** A line of the app's own CSV format: names, not uids, so a person can write it in a spreadsheet. */
data class CsvOperation(
    val dateTime: LocalDateTime,
    val type: TransactionType,
    /** Minor units; negative values reverse the operation, e.g. a refund booked as an expense. */
    val amount: Long,
    val currency: String,
    val account: String,
    /** Transfers only. */
    val toAccount: String? = null,
    /** Top-level category name. */
    val category: String? = null,
    val subcategory: String? = null,
    val note: String = "",
    /** Line in the file, for messages; 0 when written by the app. */
    val line: Int = 0,
)

data class CsvLineError(val line: Int, val reason: String)

/**
 * `дата;тип;сумма;валюта;счёт;на счёт;категория;подкатегория;заметка` — what export writes and import reads back.
 * Dates "26.10.2026 07:48" or "26.10.2026", amounts "1330,78", types "расход", "доход", "перевод".
 */
object AppCsv {
    val HEADER = listOf("дата", "тип", "сумма", "валюта", "счёт", "на счёт", "категория", "подкатегория", "заметка")

    private val TYPES = mapOf(
        TransactionType.EXPENSE to "расход",
        TransactionType.INCOME to "доход",
        TransactionType.TRANSFER to "перевод",
    )
    // Explicit labels distinguish exported negative operations from legacy sheets where a minus just meant expense.
    private val REVERSED_TYPES = mapOf(
        TransactionType.EXPENSE to "возврат расхода",
        TransactionType.INCOME to "возврат дохода",
        TransactionType.TRANSFER to "обратный перевод",
    )
    private val DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun isAppFormat(rows: List<List<String>>): Boolean =
        rows.firstOrNull()?.map { it.trim().lowercase().replace('ё', 'е') } == HEADER.map { it.replace('ё', 'е') }

    fun parse(rows: List<List<String>>): Pair<List<CsvOperation>, List<CsvLineError>> {
        val ops = ArrayList<CsvOperation>()
        val errors = ArrayList<CsvLineError>()
        rows.drop(1).forEachIndexed { i, r ->
            val line = i + 2
            when (val result = operation(r, line)) {
                is CsvOperation -> ops.add(result)
                is CsvLineError -> errors.add(result)
            }
        }
        return ops to errors
    }

    private fun operation(r: List<String>, line: Int): Any {
        fun cell(i: Int) = r.getOrNull(i)?.trim().orEmpty()
        val dateTime = Csv.dateTime(cell(0)) ?: return CsvLineError(line, "дата «${cell(0)}» не прочитана")
        val label = cell(1).lowercase()
        val reversed = label in REVERSED_TYPES.values
        val type = (if (reversed) REVERSED_TYPES else TYPES).entries.firstOrNull { it.value == label }?.key
            ?: return CsvLineError(line, "тип «${cell(1)}»: нужен расход, доход или перевод")
        val signed = Csv.amount(cell(2)) ?: return CsvLineError(line, "сумма «${cell(2)}» не прочитана")
        if (signed == 0L) return CsvLineError(line, "сумма 0")
        val account = cell(4).ifEmpty { return CsvLineError(line, "не указан счёт") }
        val toAccount = cell(5).ifEmpty { null }
        if (type == TransactionType.TRANSFER && toAccount == null) return CsvLineError(line, "у перевода нет счёта «на счёт»")
        return CsvOperation(
            dateTime = dateTime,
            type = type,
            // Normal legacy labels keep their old meaning; explicit reversal labels preserve refunds on round-trip.
            amount = if (reversed) -kotlin.math.abs(signed) else kotlin.math.abs(signed),
            currency = cell(3).uppercase().ifEmpty { "RUB" },
            account = account,
            toAccount = toAccount.takeIf { type == TransactionType.TRANSFER },
            category = cell(6).ifEmpty { null }.takeIf { type != TransactionType.TRANSFER },
            subcategory = cell(7).ifEmpty { null }.takeIf { type != TransactionType.TRANSFER },
            note = cell(8),
            line = line,
        )
    }

    /** The whole file, with a BOM so Excel takes it as UTF-8. */
    fun write(ops: List<CsvOperation>): String = buildString {
        append(Csv.BOM)
        append(Csv.line(HEADER)).append("\r\n")
        ops.forEach { op ->
            append(
                Csv.line(
                    listOf(
                        DATE_TIME.format(op.dateTime),
                        (if (op.amount < 0) REVERSED_TYPES else TYPES).getValue(op.type),
                        BigDecimal.valueOf(op.amount, 2).toPlainString().replace('.', ','),
                        op.currency,
                        op.account,
                        op.toAccount.orEmpty(),
                        op.category.orEmpty(),
                        op.subcategory.orEmpty(),
                        op.note,
                    ),
                ),
            ).append("\r\n")
        }
    }
}

/** Finding the app's account or category for a name from a file. */
object NameMatch {
    data class Match<T>(val item: T, /** False when only similar: "Сбер" for "Сбербанк". */ val exact: Boolean)

    fun <T> find(name: String, items: List<T>, nameOf: (T) -> String): Match<T>? {
        val key = norm(name)
        if (key.isEmpty()) return null
        items.firstOrNull { norm(nameOf(it)) == key }?.let { return Match(it, true) }
        // One name begins the other: short and long forms of the same bank or wallet.
        return items.filter { val other = norm(nameOf(it)); other.length >= 3 && key.length >= 3 && (other.startsWith(key) || key.startsWith(other)) }
            .minByOrNull { kotlin.math.abs(norm(nameOf(it)).length - key.length) }
            ?.let { Match(it, false) }
    }

    private fun norm(s: String) = s.lowercase().replace('ё', 'е').filter { it.isLetterOrDigit() }
}
