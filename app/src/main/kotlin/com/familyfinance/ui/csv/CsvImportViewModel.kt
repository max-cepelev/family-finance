package com.familyfinance.ui.csv

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.domain.csv.AppCsv
import com.familyfinance.domain.csv.BankStatements
import com.familyfinance.domain.csv.Csv
import com.familyfinance.domain.csv.CsvLineError
import com.familyfinance.domain.csv.CsvOperation
import com.familyfinance.domain.csv.NameMatch
import com.familyfinance.domain.csv.Statement
import com.familyfinance.domain.csv.StatementRow
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.BankCardRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.ui.common.CategoryNames
import com.familyfinance.ui.common.Dates
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.Charset
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LineState { NEW, KNOWN, NO_CATEGORY }

/** A statement line as the preview shows it. */
data class StatementLine(
    val row: StatementRow,
    val state: LineState,
    val categoryUid: String?,
    /** Category path, or where an already known line came from. */
    val detail: String,
    /** The operation this line turned out to be (from a push), to mark it as imported. */
    val matchedUid: String? = null,
)

data class StatementUi(
    val statement: Statement,
    val period: String,
    val accountUid: String?,
    val accountName: String,
    /** How the account was chosen, under its name. */
    val accountNote: String,
    val lines: List<StatementLine> = emptyList(),
    /** Lines are being checked against the chosen account. */
    val checking: Boolean = false,
    /** The account's currency differs from the statement's: nothing can be imported into it. */
    val currencyMismatch: Boolean = false,
) {
    val newCount: Int get() = lines.count { it.state != LineState.KNOWN }
}

/** Where a name from the file goes: an existing account or category, or a new one made on import. */
data class Target(val uid: String?, val label: String, val exact: Boolean) {
    val isNew: Boolean get() = uid == null
}

data class NameMapping(val key: String, val source: String, val note: String?, val target: Target, val type: CategoryType? = null)

data class OwnUi(
    val fileName: String,
    val operations: List<CsvOperation>,
    val errors: List<CsvLineError>,
    val period: String,
    val accounts: List<NameMapping>,
    val categories: List<NameMapping>,
)

sealed interface CsvImportState {
    data object Loading : CsvImportState
    data class Failed(val message: String) : CsvImportState
    data class Bank(val ui: StatementUi) : CsvImportState
    data class Own(val ui: OwnUi) : CsvImportState
    data object Importing : CsvImportState
    data class Done(val added: Int, val skipped: Int) : CsvImportState
}

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val transactions: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val cards: BankCardRepository,
) : ViewModel() {

    private val uri: Uri = Uri.parse(savedState.get<String>(ARG_URI).orEmpty())
    private val _state = MutableStateFlow<CsvImportState>(CsvImportState.Loading)
    val state: StateFlow<CsvImportState> = _state.asStateFlow()

    /** Choices for the pickers. */
    var accounts: List<Account> = emptyList()
        private set
    var categories: List<Category> = emptyList()
        private set

    init {
        viewModelScope.launch {
            accounts = accountRepository.observeAccounts().first().filter { !it.hidden }
            categories = categoryRepository.observeAll().first().filter { !it.archived }
            _state.value = try {
                val rows = Csv.parse(read())
                val statement = BankStatements.parse(rows)
                when {
                    statement != null -> CsvImportState.Bank(statementUi(statement))
                    AppCsv.isAppFormat(rows) -> ownUi(rows)
                    else -> CsvImportState.Failed(
                        "Формат не распознан. Подходит выписка Райффайзена или таблица с колонками: " + AppCsv.HEADER.joinToString("; "),
                    )
                }
            } catch (e: Exception) {
                CsvImportState.Failed("Не удалось прочитать файл: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // ---- Bank statement ----

    private suspend fun statementUi(statement: Statement): StatementUi {
        val dates = statement.rows.map { it.dateTime.toLocalDate() }
        val period = if (dates.isEmpty()) "" else Dates.range(dates.min(), dates.max())
        // The card most lines were paid with, linked in "Уведомления банков", names the account.
        val card = statement.rows.mapNotNull { it.card }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val linked = cards.observeAll().first().filter { it.bank == statement.bank.code }
        val byCard = card?.let { c -> linked.firstOrNull { it.last4 == c } }
        val chosen = byCard ?: linked.firstOrNull { it.last4 == null }
        val account = accounts.firstOrNull { it.uid == chosen?.accountUid }
        val note = when {
            account == null -> "Выберите, на какой счёт записать"
            byCard != null -> "Найден по карте •• $card из «Уведомлений банков»"
            else -> "Счёт ${statement.bank.title} без номера карты из «Уведомлений банков»"
        }
        val ui = StatementUi(statement, period, account?.uid, account?.name ?: "Выберите счёт", note)
        return if (account == null) ui else withAccount(ui, account, note)
    }

    /** Lines checked against [account], or a currency warning when it cannot hold this statement. */
    private suspend fun withAccount(ui: StatementUi, account: Account, note: String): StatementUi {
        val currency = ui.statement.rows.firstOrNull()?.currency ?: account.currencyCode
        val base = ui.copy(accountUid = account.uid, accountName = account.name, checking = false, lines = emptyList())
        if (currency != account.currencyCode) {
            return base.copy(currencyMismatch = true, accountNote = "Валюта счёта ${account.currencyCode}, а выписки $currency — выберите счёт в $currency")
        }
        return base.copy(currencyMismatch = false, accountNote = note, lines = classify(ui.statement, account.uid))
    }

    fun pickAccount(uid: String) {
        val current = (_state.value as? CsvImportState.Bank)?.ui ?: return
        val account = accounts.firstOrNull { it.uid == uid } ?: return
        // Checking hundreds of lines takes a few seconds; the screen shows it is working.
        _state.value = CsvImportState.Bank(current.copy(accountUid = uid, accountName = account.name, checking = true, lines = emptyList()))
        viewModelScope.launch {
            _state.value = CsvImportState.Bank(withAccount(current, account, "Выбран вручную"))
        }
    }

    /** New, already imported, or already there from a push: decided against the chosen account. */
    private suspend fun classify(statement: Statement, accountUid: String): List<StatementLine> {
        val zone = ZoneId.systemDefault()
        val names = CategoryNames(categories)
        val known = transactions.knownExternalIds(statement.rows.map(statement::externalId))
        val used = HashSet<String>()
        return statement.rows.map { row ->
            if (statement.externalId(row) in known) return@map StatementLine(row, LineState.KNOWN, null, "импортирована раньше")
            val at = row.dateTime.atZone(zone).toInstant().toEpochMilli()
            val match = transactions.statementCandidates(accountUid, row.type, row.amount, at, at - MATCH_WINDOW, at + MATCH_WINDOW)
                .firstOrNull { it.uid !in used }
            if (match != null) {
                used += match.uid
                return@map StatementLine(row, LineState.KNOWN, match.categoryUid, if (match.source == Transaction.SOURCE_PUSH) "из пуша" else "уже записана", match.uid)
            }
            val category = transactions.categoryForNote(row.type, row.payee)
            StatementLine(row, if (category == null) LineState.NO_CATEGORY else LineState.NEW, category, names.label(category).orEmpty())
        }
    }

    // ---- App format ----

    private suspend fun ownUi(rows: List<List<String>>): CsvImportState {
        val (ops, errors) = AppCsv.parse(rows)
        if (ops.isEmpty()) {
            return CsvImportState.Failed("В файле нет операций, которые удалось прочитать" + errors.firstOrNull()?.let { ": строка ${it.line}, ${it.reason}" }.orEmpty())
        }
        val dates = ops.map { it.dateTime.toLocalDate() }
        val accountNames = (ops.map { it.account } + ops.mapNotNull { it.toAccount }).distinct()
        val accountMappings = accountNames.map { name ->
            val match = NameMatch.find(name, accounts) { it.name }
            NameMapping(name, name, if (match?.exact == false) "похожее название" else null, match?.let { Target(it.item.uid, it.item.name, it.exact) } ?: Target(null, name, false))
        }
        val names = CategoryNames(categories)
        val categoryKeys = ops.filter { it.category != null }
            .map { Triple(if (it.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE, it.category!!, it.subcategory) }
            .distinct()
        val categoryMappings = categoryKeys.map { (type, top, sub) ->
            val ofType = categories.filter { it.type == type }
            val topMatch = NameMatch.find(top, ofType.filter { it.parentUid == null }) { it.name }?.takeIf { it.exact }
            val found: Pair<Category, Boolean>? = if (sub == null) {
                topMatch?.let { it.item to true } ?: NameMatch.find(top, ofType) { it.name }?.let { it.item to false }
            } else {
                topMatch?.let { t -> NameMatch.find(sub, ofType.filter { it.parentUid == t.item.uid }) { it.name }?.takeIf { it.exact }?.let { it.item to true } }
                    ?: NameMatch.find(sub, ofType.filter { it.parentUid != null }) { it.name }?.let { it.item to false }
            }
            NameMapping(
                key = categoryKey(type, top, sub),
                source = sub ?: top,
                note = if (sub != null && found?.second != false) "подкатегория из файла" else if (found?.second == false) "похожее название" else null,
                target = found?.let { (c, exact) -> Target(c.uid, names.label(c.uid).orEmpty(), exact) }
                    ?: Target(null, if (sub != null) "$top › $sub" else top, false),
                type = type,
            )
        }
        return CsvImportState.Own(
            OwnUi(
                fileName = fileName(),
                operations = ops,
                errors = errors,
                period = Dates.range(dates.min(), dates.max()),
                accounts = accountMappings,
                categories = categoryMappings,
            ),
        )
    }

    /** [uid] null makes a new one with the name from the file. */
    fun mapAccount(key: String, uid: String?) = updateOwn { ui ->
        ui.copy(accounts = ui.accounts.map { m ->
            if (m.key != key) m else m.copy(target = uid?.let { u -> Target(u, accounts.first { it.uid == u }.name, true) } ?: Target(null, m.source, false), note = null)
        })
    }

    fun mapCategory(key: String, uid: String?) = updateOwn { ui ->
        val names = CategoryNames(categories)
        ui.copy(categories = ui.categories.map { m ->
            if (m.key != key) m else m.copy(
                target = uid?.let { Target(it, names.label(it).orEmpty(), true) } ?: Target(null, defaultNewLabel(m), false),
                note = null,
            )
        })
    }

    private fun defaultNewLabel(m: NameMapping): String = m.key.substringAfter('|').replace("|", " › ").removeSuffix(" › ")

    private fun updateOwn(change: (OwnUi) -> OwnUi) = _state.update { s -> if (s is CsvImportState.Own) CsvImportState.Own(change(s.ui)) else s }

    // ---- Import ----

    fun import() {
        when (val s = _state.value) {
            is CsvImportState.Bank -> importStatement(s.ui)
            is CsvImportState.Own -> importOwn(s.ui)
            else -> Unit
        }
    }

    private fun importStatement(ui: StatementUi) {
        val accountUid = ui.accountUid ?: return
        if (ui.currencyMismatch || ui.checking) return
        val account = accounts.first { it.uid == accountUid }
        _state.value = CsvImportState.Importing
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val written = ui.lines.mapNotNull { line ->
                val externalId = ui.statement.externalId(line.row)
                when {
                    line.state != LineState.KNOWN -> Transaction(
                        uid = UUID.randomUUID().toString(),
                        type = line.row.type,
                        date = line.row.dateTime.atZone(zone).toInstant().toEpochMilli(),
                        amount = line.row.amount,
                        currencyCode = account.currencyCode,
                        accountUid = accountUid,
                        accountAmount = line.row.amount,
                        categoryUid = line.categoryUid,
                        note = line.row.payee,
                        updatedAt = 0,
                        deviceId = "",
                        source = Transaction.SOURCE_STATEMENT,
                        externalId = externalId,
                    )
                    // The push operation is the same purchase: it keeps its category, the statement id marks it imported.
                    line.matchedUid != null -> transactions.get(line.matchedUid)?.copy(externalId = externalId)
                    else -> null
                }
            }
            transactions.saveAll(written)
            _state.value = CsvImportState.Done(added = ui.newCount, skipped = ui.lines.size - ui.newCount)
        }
    }

    private fun importOwn(ui: OwnUi) {
        _state.value = CsvImportState.Importing
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val accountUids = HashMap<String, String>()
            var order = accounts.maxOfOrNull { it.sortOrder } ?: 0
            ui.accounts.forEach { m ->
                accountUids[m.key] = m.target.uid ?: UUID.randomUUID().toString().also { uid ->
                    val currency = ui.operations.firstOrNull { it.account == m.key || it.toAccount == m.key }?.currency ?: "RUB"
                    accountRepository.saveAccount(Account(uid, null, m.source, currency, ++order, updatedAt = 0, deviceId = ""))
                }
            }
            val categoryUids = HashMap<String, String>()
            ui.categories.forEach { m -> categoryUids[m.key] = m.target.uid ?: createCategory(m) }

            var added = 0
            var skipped = 0
            val toSave = ArrayList<Transaction>()
            ui.operations.forEach { op ->
                val accountUid = accountUids.getValue(op.account)
                val at = op.dateTime.atZone(zone).toInstant().toEpochMilli()
                // The same file imported twice, or an exported file brought back: keep one copy.
                // The file keeps minutes only, so the stored operation may be up to a minute later.
                val twin = transactions.findSame(accountUid, op.type, op.amount, at, at + 59_999).any { it.note == op.note }
                if (twin) {
                    skipped++
                    return@forEach
                }
                val type = if (op.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                toSave += Transaction(
                    uid = UUID.randomUUID().toString(),
                    type = op.type,
                    date = at,
                    amount = op.amount,
                    currencyCode = op.currency,
                    accountUid = accountUid,
                    accountAmount = op.amount,
                    toAccountUid = op.toAccount?.let(accountUids::getValue),
                    toAccountAmount = op.toAccount?.let { op.amount },
                    categoryUid = op.category?.let { categoryUids[categoryKey(type, it, op.subcategory)] },
                    note = op.note,
                    updatedAt = 0,
                    deviceId = "",
                )
                added++
            }
            transactions.saveAll(toSave)
            _state.value = CsvImportState.Done(added, skipped + ui.errors.size)
        }
    }

    /** Makes "top › sub" (or just "top"), reusing a top-level category of that name. */
    private suspend fun createCategory(m: NameMapping): String {
        val type = m.type ?: CategoryType.EXPENSE
        val parts = m.key.split('|')
        val top = parts[1]
        val sub = parts.getOrNull(2)?.ifEmpty { null }
        val existingTop = categories.firstOrNull { it.type == type && it.parentUid == null && it.name.equals(top, ignoreCase = true) }
        val order = (categories.maxOfOrNull { it.sortOrder } ?: 0) + 1
        val topUid = existingTop?.uid ?: UUID.randomUUID().toString().also { uid ->
            val created = Category(uid, null, top, type, order, false, updatedAt = 0, deviceId = "")
            categoryRepository.save(created)
            categories = categories + created
        }
        if (sub == null) return topUid
        val uid = UUID.randomUUID().toString()
        val created = Category(uid, topUid, sub, type, order + 1, false, updatedAt = 0, deviceId = "")
        categoryRepository.save(created)
        categories = categories + created
        return uid
    }

    // ---- File ----

    /** UTF-8 as exported by banks and Excel; Windows-1251 when that does not decode. */
    private suspend fun read(): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("файл недоступен")
        val utf8 = bytes.toString(Charsets.UTF_8)
        if ('�' in utf8) bytes.toString(Charset.forName("windows-1251")) else utf8
    }

    private fun fileName(): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()

    companion object {
        const val ARG_URI = "uri"
        /** A push arrives within minutes of the operation the statement records. */
        private const val MATCH_WINDOW = 15 * 60 * 1000L

        fun categoryKey(type: CategoryType, top: String, sub: String?) = "${type.name}|$top|${sub.orEmpty()}"
    }
}
