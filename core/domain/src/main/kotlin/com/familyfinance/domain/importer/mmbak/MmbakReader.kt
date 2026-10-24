package com.familyfinance.domain.importer.mmbak

import com.familyfinance.domain.importer.ImportBundle
import com.familyfinance.domain.importer.ImportFormatException
import com.familyfinance.domain.importer.ImportReport
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.model.Money
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import java.util.UUID

/**
 * Maps a Money Manager backup (.mmbak, SQLite, tested with user_version 19) to our model.
 *
 * Source uids are turned into name-based UUIDs, so importing the same backup again (or on the
 * second phone) produces the same records instead of duplicates. Source modification times
 * become [com.familyfinance.domain.model.Syncable.updatedAt], so a re-import never overwrites
 * records edited after the first import.
 */
class MmbakReader(private val deviceId: String) {

    fun read(source: MmbakSource): ImportBundle {
        val tables = source.tableNames()
        val missing = REQUIRED_TABLES - tables
        if (missing.isNotEmpty()) {
            throw ImportFormatException("Не похоже на бэкап Money Manager: нет таблиц ${missing.joinToString()}")
        }
        return try {
            Mapping(source).run()
        } catch (e: ImportFormatException) {
            throw e
        } catch (e: RuntimeException) {
            throw ImportFormatException("Не удалось прочитать бэкап (версия ${source.userVersion}): ${e.message}", e)
        }
    }

    private inner class Mapping(private val source: MmbakSource) {
        private val warnings = Warnings()
        private var skippedDeleted = 0

        fun run(): ImportBundle {
            val rawCurrencies = readCurrencies()
            val mainIso = rawCurrencies.firstOrNull { it.isMain }?.iso ?: rawCurrencies.firstOrNull()?.iso ?: "RUB"
            val isoByUid = rawCurrencies.associate { it.uid to it.iso }
            val decimalsByIso = rawCurrencies.associate { it.iso to it.decimals }
            fun iso(currencyUid: String?, context: String): String {
                if (currencyUid.isNullOrEmpty()) return mainIso
                return isoByUid[currencyUid] ?: run {
                    warnings += "$context: неизвестная валюта $currencyUid, взята $mainIso"
                    mainIso
                }
            }
            fun decimals(iso: String) = decimalsByIso[iso] ?: 2

            val groups = readGroups()
            val groupIds = groups.map { it.uid }.toSet()
            val sourceAccounts = readAccounts(groupIds, ::iso)
            val (categories, sourceCategoryIds) = readCategories()
            val tx = readTransactions(sourceAccounts.associateBy { it.uid }, sourceCategoryIds, ::iso, ::decimals)
            val accounts = fixAccountCurrencies(sourceAccounts, tx.items)

            val usedIso = accounts.map { it.currencyCode }.toSet() + tx.items.map { it.currencyCode }
            val currencies = rawCurrencies
                .filter { !it.deletedInSource || it.iso in usedIso }
                .distinctBy { it.iso }
                .map {
                    Currency(
                        uid = it.iso, name = it.name, symbol = it.symbol, decimals = it.decimals,
                        isMain = it.iso == mainIso, rateToMain = it.rate,
                        updatedAt = it.updatedAt, deviceId = deviceId,
                    )
                }

            return ImportBundle(
                currencies = currencies,
                groups = groups,
                accounts = accounts,
                categories = categories,
                transactions = tx.items,
                report = ImportReport(
                    sourceVersion = source.userVersion,
                    skippedDeleted = skippedDeleted,
                    transfersMerged = tx.transfersMerged,
                    balanceAdjustments = tx.balanceAdjustments,
                    warningCount = warnings.count,
                    warnings = warnings.first,
                ),
            )
        }

        /**
         * Money Manager lets an account keep a wrong currency: e.g. marked USD while every operation on it
         * is in rubles with the account amount equal to the operation amount (no conversion ever happened).
         * Such an account takes the currency of its operations.
         */
        private fun fixAccountCurrencies(accounts: List<Account>, transactions: List<Transaction>): List<Account> {
            // Per account: (operation currency, operation amount, amount booked on the account).
            val sides = HashMap<String, MutableList<Triple<String, Long, Long>>>()
            for (tx in transactions) {
                sides.getOrPut(tx.accountUid) { ArrayList() } += Triple(tx.currencyCode, tx.amount, tx.accountAmount)
                val to = tx.toAccountUid ?: continue
                sides.getOrPut(to) { ArrayList() } += Triple(tx.currencyCode, tx.amount, tx.toAccountAmount ?: tx.amount)
            }
            return accounts.map { account ->
                val own = sides[account.uid].orEmpty()
                val currency = own.firstOrNull()?.first
                val wrong = currency != null && currency != account.currencyCode &&
                    own.all { (c, amount, booked) -> c == currency && amount == booked }
                if (!wrong) return@map account
                warnings += "Счёт «${account.name}»: в Money Manager валюта ${account.currencyCode}, " +
                    "но все ${own.size} операций в $currency без пересчёта — счёт переведён в $currency"
                account.copy(currencyCode = currency!!)
            }
        }

        private fun readCurrencies(): List<RawCurrency> = source.query(
            "SELECT uid, NAME, ISO, IS_DEL, RATE, SYMBOL, IS_MAIN_CURRENCY, DECIMAL_POINT, MODIFY_DATE FROM CURRENCY",
        ) { r ->
            RawCurrency(
                uid = r.string("uid").orEmpty(),
                iso = r.string("ISO").orEmpty().ifEmpty { r.string("uid").orEmpty().substringAfter('_') },
                name = r.string("NAME").orEmpty(),
                symbol = r.string("SYMBOL").orEmpty(),
                decimals = r.int("DECIMAL_POINT") ?: 2,
                isMain = r.int("IS_MAIN_CURRENCY") == 1,
                rate = r.string("RATE")?.toDoubleOrNull() ?: 1.0,
                deletedInSource = r.int("IS_DEL") == 1,
                updatedAt = r.long("MODIFY_DATE") ?: 0L,
            )
        }.filter { it.iso.isNotEmpty() }

        private fun readGroups(): List<AccountGroup> = source.query(
            "SELECT uid, ACC_GROUP_NAME, ORDERSEQ, IS_DEL, USETIME FROM ASSETGROUP",
        ) { r ->
            AccountGroup(
                uid = stableUid("group", r.string("uid")),
                name = r.string("ACC_GROUP_NAME").orEmpty(),
                sortOrder = r.int("ORDERSEQ") ?: 0,
                archived = r.int("IS_DEL") == 1,
                updatedAt = r.long("USETIME") ?: 0L,
                deviceId = deviceId,
            )
        }

        private fun readAccounts(groupIds: Set<String>, iso: (String?, String) -> String): List<Account> =
            source.query(
                "SELECT uid, NIC_NAME, groupUid, currencyUid, ZDATA, ORDERSEQ, A_UTIME FROM ASSETS",
            ) { r ->
                val name = r.string("NIC_NAME").orEmpty()
                val groupUid = r.string("groupUid")?.takeIf { it.isNotEmpty() }?.let { stableUid("group", it) }
                if (groupUid != null && groupUid !in groupIds) warnings += "Счёт «$name»: группа не найдена"
                Account(
                    uid = stableUid("account", r.string("uid")),
                    groupUid = groupUid?.takeIf { it in groupIds },
                    name = name,
                    currencyCode = iso(r.string("currencyUid"), "Счёт «$name»"),
                    sortOrder = r.int("ORDERSEQ") ?: 0,
                    // ZDATA = 1 marks an account hidden in Money Manager.
                    hidden = r.string("ZDATA") == "1",
                    updatedAt = r.long("A_UTIME") ?: 0L,
                    deviceId = deviceId,
                )
            }

        /** Our categories plus the source uids, which transactions reference. */
        private fun readCategories(): Pair<List<Category>, Set<String>> {
            val rows = source.query(
                "SELECT uid, NAME, TYPE, STATUS, pUid, ORDERSEQ, C_IS_DEL, C_UTIME FROM ZCATEGORY",
            ) { r ->
                RawCategory(
                    uid = r.string("uid").orEmpty(),
                    name = r.string("NAME").orEmpty(),
                    type = if (r.int("TYPE") == 0) CategoryType.INCOME else CategoryType.EXPENSE,
                    isSub = r.int("STATUS") == 2,
                    parentUid = r.string("pUid"),
                    sortOrder = r.int("ORDERSEQ") ?: 0,
                    deletedInSource = r.int("C_IS_DEL") == 1,
                    updatedAt = r.long("C_UTIME") ?: 0L,
                )
            }.filter { it.uid.isNotEmpty() }
            val sourceIds = rows.map { it.uid }.toSet()
            return rows.map { c ->
                // Top-level categories carry an empty or "0" parent; pUid points to the parent's uid.
                val parent = c.parentUid?.takeIf { c.isSub && it.isNotEmpty() && it != "0" }
                if (parent != null && parent !in sourceIds) {
                    warnings += "Подкатегория «${c.name}»: родитель не найден, перенесена на верхний уровень"
                }
                Category(
                    uid = stableUid("category", c.uid),
                    parentUid = parent?.takeIf { it in sourceIds }?.let { stableUid("category", it) },
                    name = c.name,
                    type = c.type,
                    sortOrder = c.sortOrder,
                    // Deleted categories are still referenced by old transactions: keep them, hidden.
                    archived = c.deletedInSource,
                    updatedAt = c.updatedAt,
                    deviceId = deviceId,
                )
            } to sourceIds
        }

        private fun readTransactions(
            accounts: Map<String, Account>,
            sourceCategoryIds: Set<String>,
            iso: (String?, String) -> String,
            decimals: (String) -> Int,
        ): TxResult {
            val rows = source.query(
                "SELECT uid, DO_TYPE, ZMONEY, AMOUNT_ACCOUNT, ZDATE, assetUid, toAssetUid, ctgUid, " +
                    "ZCONTENT, ZDATA, txUidTrans, IS_DEL, currencyUid, UTIME FROM INOUTCOME",
            ) { r ->
                RawTx(
                    uid = r.string("uid").orEmpty(),
                    doType = r.string("DO_TYPE")?.trim().orEmpty(),
                    money = r.string("ZMONEY"),
                    amountAccount = r.string("AMOUNT_ACCOUNT"),
                    date = r.long("ZDATE") ?: 0L,
                    assetUid = r.string("assetUid").orEmpty(),
                    toAssetUid = r.string("toAssetUid").orEmpty(),
                    ctgUid = r.string("ctgUid").orEmpty(),
                    note = joinNotes(r.string("ZCONTENT"), r.string("ZDATA")),
                    transferUid = r.string("txUidTrans").orEmpty(),
                    deletedInSource = r.int("IS_DEL") == 1,
                    currencyUid = r.string("currencyUid"),
                    updatedAt = r.long("UTIME"),
                )
            }

            val items = ArrayList<Transaction>(rows.size)
            var balanceAdjustments = 0
            var transfersMerged = 0

            fun accountOf(raw: RawTx, assetUid: String): Account? =
                accounts[stableUid("account", assetUid)].also {
                    if (it == null) warnings += "Операция ${raw.describe()}: счёт не найден, пропущена"
                }

            /** (tx currency, amount in it, amount in the account currency); amounts in minor units. */
            fun amounts(raw: RawTx, account: Account): Triple<String, Long, Long> {
                val currency = iso(raw.currencyUid, "Операция ${raw.describe()}")
                val amount = Money.parseMinor(raw.money ?: "0", decimals(currency))
                val accountAmount = raw.amountAccount?.takeIf { it.isNotBlank() }
                    ?.let { Money.parseMinor(it, decimals(account.currencyCode)) }
                    ?: amount
                return Triple(currency, amount, accountAmount)
            }

            val transferHalves = HashMap<String, MutableList<RawTx>>()
            for (raw in rows) {
                if (raw.deletedInSource) { skippedDeleted++; continue }
                when (raw.doType) {
                    DO_INCOME, DO_EXPENSE -> {
                        val account = accountOf(raw, raw.assetUid) ?: continue
                        val (currency, amount, accountAmount) = amounts(raw, account)
                        val categoryUid = when {
                            raw.ctgUid in BALANCE_ADJUSTMENT_CATEGORIES -> { balanceAdjustments++; null }
                            raw.ctgUid.isEmpty() -> null
                            raw.ctgUid in sourceCategoryIds -> stableUid("category", raw.ctgUid)
                            else -> { warnings += "Операция ${raw.describe()}: категория ${raw.ctgUid} не найдена"; null }
                        }
                        items += Transaction(
                            uid = stableUid("tx", raw.uid),
                            type = if (raw.doType == DO_INCOME) TransactionType.INCOME else TransactionType.EXPENSE,
                            date = raw.date,
                            amount = amount,
                            currencyCode = currency,
                            accountUid = account.uid,
                            accountAmount = accountAmount,
                            categoryUid = categoryUid,
                            note = raw.note,
                            updatedAt = raw.updatedAt ?: raw.date,
                            deviceId = deviceId,
                        )
                    }
                    DO_TRANSFER_OUT, DO_TRANSFER_IN -> {
                        val key = raw.transferUid.ifEmpty { "single:" + raw.uid }
                        transferHalves.getOrPut(key) { ArrayList(2) } += raw
                    }
                    else -> warnings += "Операция ${raw.describe()}: неизвестный тип ${raw.doType}, пропущена"
                }
            }

            for ((key, halves) in transferHalves) {
                val out = halves.firstOrNull { it.doType == DO_TRANSFER_OUT }
                val inc = halves.firstOrNull { it.doType == DO_TRANSFER_IN }
                if (halves.size != 2 || out == null || inc == null) {
                    warnings += "Перевод ${halves.first().describe()}: нет второй половины, восстановлен по одной строке"
                }
                val main = out ?: inc!!
                val fromAssetUid = out?.assetUid ?: inc!!.toAssetUid
                val toAssetUid = inc?.assetUid ?: out!!.toAssetUid
                val from = accountOf(main, fromAssetUid) ?: continue
                val to = accountOf(main, toAssetUid) ?: continue
                val (currency, amount, fromAmount) = amounts(main, from)
                val toAmount = when {
                    inc != null -> amounts(inc, to).third
                    to.currencyCode == currency -> amount
                    else -> fromAmount.also {
                        warnings += "Перевод ${main.describe()}: сумма зачисления в другой валюте неизвестна"
                    }
                }
                if (out != null && inc != null) transfersMerged++
                items += Transaction(
                    uid = stableUid("transfer", key),
                    type = TransactionType.TRANSFER,
                    date = main.date,
                    amount = amount,
                    currencyCode = currency,
                    accountUid = from.uid,
                    accountAmount = fromAmount,
                    toAccountUid = to.uid,
                    toAccountAmount = toAmount,
                    note = main.note,
                    updatedAt = halves.maxOf { it.updatedAt ?: it.date },
                    deviceId = deviceId,
                )
            }

            items.sortBy { it.date }
            return TxResult(items, transfersMerged, balanceAdjustments)
        }
    }

    private class Warnings {
        val first = ArrayList<String>()
        var count = 0
        operator fun plusAssign(message: String) {
            count++
            if (first.size < MAX_WARNINGS) first += message
        }
    }

    private data class RawCurrency(
        val uid: String, val iso: String, val name: String, val symbol: String, val decimals: Int,
        val isMain: Boolean, val rate: Double, val deletedInSource: Boolean, val updatedAt: Long,
    )

    private data class RawCategory(
        val uid: String, val name: String, val type: CategoryType, val isSub: Boolean, val parentUid: String?,
        val sortOrder: Int, val deletedInSource: Boolean, val updatedAt: Long,
    )

    private data class RawTx(
        val uid: String, val doType: String, val money: String?, val amountAccount: String?, val date: Long,
        val assetUid: String, val toAssetUid: String, val ctgUid: String, val note: String,
        val transferUid: String, val deletedInSource: Boolean, val currencyUid: String?, val updatedAt: Long?,
    ) {
        fun describe() = "${java.time.Instant.ofEpochMilli(date).toString().take(10)} ${money.orEmpty()}"
    }

    private class TxResult(val items: List<Transaction>, val transfersMerged: Int, val balanceAdjustments: Int)

    private companion object {
        val REQUIRED_TABLES = setOf("CURRENCY", "ASSETGROUP", "ASSETS", "ZCATEGORY", "INOUTCOME")

        const val DO_INCOME = "0"
        const val DO_EXPENSE = "1"
        const val DO_TRANSFER_OUT = "3"
        const val DO_TRANSFER_IN = "4"

        /** Pseudo categories Money Manager uses for "modify balance" entries. */
        val BALANCE_ADJUSTMENT_CATEGORIES = setOf("-3", "-4")

        const val MAX_WARNINGS = 100

        fun stableUid(kind: String, sourceUid: String?): String = MmbakUids.of(kind, sourceUid)

        fun joinNotes(content: String?, memo: String?): String {
            val a = content?.trim().orEmpty()
            val b = memo?.trim().orEmpty()
            return when {
                b.isEmpty() || b == a -> a
                a.isEmpty() -> b
                else -> "$a\n$b"
            }
        }

        fun MmbakRow.long(column: String): Long? {
            val text = string(column)?.trim() ?: return null
            return text.toLongOrNull() ?: text.toDoubleOrNull()?.toLong()
        }

        fun MmbakRow.int(column: String): Int? = long(column)?.toInt()
    }
}

/** Our uid for a Money Manager record, the same on every import. */
object MmbakUids {
    fun of(kind: String, sourceUid: String?): String =
        UUID.nameUUIDFromBytes("mmbak:$kind:${sourceUid.orEmpty()}".toByteArray(Charsets.UTF_8)).toString()
}
