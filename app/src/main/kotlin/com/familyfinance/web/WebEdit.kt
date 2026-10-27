package com.familyfinance.web

import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.Split
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.receipt.ReceiptQr
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.CategoryNames
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.transaction.DraftError
import com.familyfinance.ui.transaction.DraftResult
import com.familyfinance.ui.transaction.PartDraft
import com.familyfinance.ui.transaction.TransactionDraft
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Form fields as the browser posts them: name to values, in page order. */
typealias WebFields = Map<String, List<String>>

data class WebOption(val value: String, val label: String)

/** A part of a split operation; the first one's [amount] is the remainder, shown and not typed. */
data class WebPart(val category: String, val amount: String)

/** The edit panel, every field as the form shows it. */
data class WebForm(
    /** Null for a new operation. */
    val uid: String?,
    val month: YearMonth,
    val type: TransactionType,
    val amount: String,
    val currency: String,
    /** "2026-10-26" and "14:42", as date and time inputs take them. */
    val date: String,
    val time: String,
    val account: String,
    val toAccount: String,
    /** Shown only for a transfer to an account in another currency. */
    val toAmount: String?,
    val toCurrency: String,
    val category: String,
    val note: String,
    val accounts: List<WebOption>,
    val categories: List<WebOption>,
    val parts: List<WebPart>,
    val remainder: String,
    /** "Чек прикреплён · касса •• 0001", null without a receipt. */
    val receipt: String?,
    val errors: Set<DraftError> = emptySet(),
)

/** Adding and editing operations from the browser, through the same draft and repository calls as the phone editor. */
interface WebEdits {
    /** The panel for operation [uid], or for a new one in [month]. */
    suspend fun open(uid: String?, month: YearMonth): WebForm

    /** The panel again after [action]: "type:INCOME", "split", "part-add", "part-remove:2"; anything else just recounts. */
    suspend fun change(fields: WebFields, action: String?): WebForm

    /** Null once saved; the form with its errors otherwise. */
    suspend fun save(fields: WebFields): WebForm?

    suspend fun delete(uid: String)
}

class RepositoryWebEdits @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
) : WebEdits {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    override suspend fun open(uid: String?, month: YearMonth): WebForm {
        val decimals = decimals()
        val existing = uid?.let { transactions.get(it) }
        val draft = if (existing != null) {
            val parts = existing.splitUid?.let { transactions.splitParts(it) }.orEmpty()
            if (parts.size >= 2) {
                TransactionDraft.from(Split.whole(parts), decimals).copy(
                    parts = parts.map { PartDraft(it.categoryUid, TransactionDraft.format(it.amount, decimals(it.currencyCode))) },
                    partUids = parts.map { it.uid },
                )
            } else {
                TransactionDraft.from(existing, decimals)
            }
        } else {
            // Now in the current month; noon of its first day when adding to another one.
            val date = if (month == YearMonth.now(zone)) System.currentTimeMillis()
            else month.atDay(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            TransactionDraft(date = date, accountUid = transactions.lastUsedAccountUid())
        }
        return form(draft, month)
    }

    override suspend fun change(fields: WebFields, action: String?): WebForm {
        var draft = draft(fields)
        val type = action?.removePrefix("type:")?.takeIf { action.startsWith("type:") }
            ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
        draft = when {
            // Categories are per type, so neither the category nor the parts fit the new one.
            type != null && type != draft.type -> draft.copy(type = type, categoryUid = null, parts = emptyList())
            action == "split" && draft.parts.isEmpty() -> draft.copy(parts = listOf(PartDraft(draft.categoryUid), PartDraft(null)))
            action == "part-add" -> draft.copy(parts = draft.parts + PartDraft(null))
            action?.startsWith("part-remove:") == true -> {
                val index = action.substringAfter(':').toIntOrNull()
                val parts = draft.parts.filterIndexed { i, _ -> i == 0 || i != index }
                // One part left is no split: it is just the category.
                if (parts.size == 1) draft.copy(categoryUid = parts[0].categoryUid, parts = emptyList()) else draft.copy(parts = parts)
            }
            else -> draft
        }
        return form(draft, month(fields))
    }

    override suspend fun save(fields: WebFields): WebForm? {
        val draft = draft(fields)
        val byUid = accounts.observeAccounts().first().associateBy { it.uid }
        val decimals = decimals()
        return when (val result = draft.build(byUid, decimals)) {
            is DraftResult.Invalid -> form(draft, month(fields), result.errors)
            is DraftResult.Valid -> {
                val writes = draft.writes(result.transaction, decimals(result.transaction.currencyCode))
                    ?: return form(draft, month(fields), setOf(DraftError.SPLIT))
                transactions.saveAll(writes.save, deleteUids = writes.delete)
                null
            }
        }
    }

    override suspend fun delete(uid: String) {
        val existing = transactions.get(uid) ?: return
        val parts = existing.splitUid?.let { transactions.splitParts(it) }.orEmpty()
        if (parts.size >= 2) transactions.saveAll(emptyList(), deleteUids = parts.map { it.uid }) else transactions.delete(uid)
    }

    /**
     * The draft the posted fields describe. What the browser cannot change (where the operation came from, its
     * receipt, the stored parts) is read from the phone, not from the form.
     */
    private suspend fun draft(fields: WebFields): TransactionDraft {
        fun one(name: String) = fields[name]?.firstOrNull().orEmpty()
        val decimals = decimals()
        val existing = one("uid").takeIf { it.isNotEmpty() }?.let { transactions.get(it) }
        val stored = existing?.splitUid?.let { transactions.splitParts(it) }.orEmpty().takeIf { it.size >= 2 }
        val base = when {
            stored != null -> TransactionDraft.from(Split.whole(stored), decimals).copy(partUids = stored.map { it.uid })
            existing != null -> TransactionDraft.from(existing, decimals)
            else -> TransactionDraft(date = System.currentTimeMillis())
        }
        val type = runCatching { TransactionType.valueOf(one("type")) }.getOrDefault(base.type)
        val partCategories = fields["partCategory"].orEmpty()
        val partAmounts = fields["partAmount"].orEmpty()
        val account = one("account").ifEmpty { null }
        return base.copy(
            type = type,
            amountText = one("amount"),
            // Another account brings its own currency; the stored one stays only on the same account.
            currencyCode = base.currencyCode.takeIf { account == base.accountUid },
            accountUid = account,
            toAccountUid = one("toAccount").ifEmpty { null }.takeIf { type == TransactionType.TRANSFER },
            toAmountText = one("toAmount"),
            categoryUid = one("category").ifEmpty { null },
            date = date(one("date"), one("time"), base),
            note = one("note"),
            // The first part's amount is the remainder; only the others are typed.
            parts = partCategories.mapIndexed { i, c -> PartDraft(c.ifEmpty { null }, if (i == 0) "" else partAmounts.getOrNull(i - 1).orEmpty()) },
        )
    }

    /** The typed date and time; the stored moment, seconds included, while they still show it. */
    private fun date(date: String, time: String, base: TransactionDraft): Long {
        val stored = Instant.ofEpochMilli(base.date).atZone(zone)
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: stored.toLocalDate()
        val clock = runCatching { LocalTime.parse(time) }.getOrNull() ?: stored.toLocalTime()
        if (day == stored.toLocalDate() && clock.withSecond(0).withNano(0) == stored.toLocalTime().withSecond(0).withNano(0)) return base.date
        return LocalDateTime.of(day, clock).atZone(zone).toInstant().toEpochMilli()
    }

    private fun month(fields: WebFields): YearMonth =
        fields["month"]?.firstOrNull()?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.now(zone)

    private suspend fun decimals(): (String) -> Int {
        val byCode = accounts.observeCurrencies().first().associate { it.code to it.decimals }
        return { code -> byCode[code] ?: 2 }
    }

    private suspend fun form(draft: TransactionDraft, month: YearMonth, errors: Set<DraftError> = emptySet()): WebForm {
        val all = accounts.observeAccounts().first()
        val byUid = all.associateBy { it.uid }
        val decimals = decimals()
        val currency = draft.currency(byUid) ?: "RUB"
        val stored = Instant.ofEpochMilli(draft.date).atZone(zone)
        val typed = draft.parts.drop(1).map { TransactionDraft.parseAmount(it.amountText, decimals(currency)) ?: 0L }
        val total = TransactionDraft.parseAmount(draft.amountText, decimals(currency)) ?: 0L
        return WebForm(
            uid = draft.original?.uid,
            month = month,
            type = draft.type,
            amount = draft.amountText,
            currency = MoneyFormatter.symbol(currency),
            date = stored.toLocalDate().toString(),
            time = stored.toLocalTime().format(TIME),
            account = draft.accountUid.orEmpty(),
            toAccount = draft.toAccountUid.orEmpty(),
            toAmount = draft.toAmountText.takeIf { draft.needsToAmount(byUid) },
            toCurrency = byUid[draft.toAccountUid]?.currencyCode?.let(MoneyFormatter::symbol).orEmpty(),
            category = draft.categoryUid.orEmpty(),
            note = draft.note,
            accounts = accountOptions(all, setOfNotNull(draft.accountUid, draft.toAccountUid)),
            categories = categoryOptions(categories.observeAll().first(), draft.type, draft.categoryUid, draft.parts),
            parts = draft.parts.mapIndexed { i, p -> WebPart(p.categoryUid.orEmpty(), if (i == 0) "" else p.amountText) },
            remainder = MoneyFormatter.format(Split.remainder(total, typed), currency, decimals(currency)),
            receipt = draft.receipt?.let { r ->
                val fn = ReceiptQr.parse(r)?.registerTail
                listOfNotNull("Чек прикреплён", fn?.let { "касса •• $it" }).joinToString(" · ")
            },
            errors = errors,
        )
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** Visible accounts in their order; a hidden one only when the operation is already on it. */
        fun accountOptions(all: List<Account>, used: Set<String>): List<WebOption> =
            all.filter { !it.deleted && (!it.hidden || it.uid in used) }
                .sortedBy { it.sortOrder }
                .map { WebOption(it.uid, it.name) }

        /** Categories of the type, each top one followed by its children; archived ones only when in use. */
        fun categoryOptions(all: List<Category>, type: TransactionType, current: String?, parts: List<PartDraft>): List<WebOption> {
            if (type == TransactionType.TRANSFER) return emptyList()
            val kind = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
            val used = setOfNotNull(current) + parts.mapNotNull { it.categoryUid }
            val shown = all.filter { it.type == kind && !it.deleted && (!it.archived || it.uid in used) }
            val names = CategoryNames(all)
            val children = shown.filter { it.parentUid != null }.groupBy { it.parentUid }
            return listOf(WebOption("", "Без категории")) + shown.filter { it.parentUid == null }.sortedBy { it.sortOrder }.flatMap { top ->
                listOf(top) + children[top.uid].orEmpty().sortedBy { it.sortOrder }
            }.map { c -> WebOption(c.uid, listOfNotNull(c.icon ?: names.top(c.uid)?.icon, names.label(c.uid)).joinToString(" ")) }
        }
    }
}

