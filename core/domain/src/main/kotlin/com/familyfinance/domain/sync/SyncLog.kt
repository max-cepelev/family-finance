package com.familyfinance.domain.sync

import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.BankCard
import com.familyfinance.domain.model.Budget
import com.familyfinance.domain.model.CashRegister
import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.model.Syncable
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.model.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Change log format: one JSON object per line, `"t"` names the kind. Each device appends only to its
 * own file, so a file never has two writers. The shapes here are the wire format and stay stable even
 * when the domain model changes; add fields with defaults, never rename.
 */
object SyncLog {
    const val FORMAT = 1
    private const val PREFIX = "familyfinance-"
    private const val SUFFIX = ".jsonl"

    /** Exact name only: Syncthing's "*.sync-conflict-*" copies must not be read as logs. */
    private val NAME = Regex("""^familyfinance-([0-9a-fA-F-]{36})\.jsonl$""")

    fun fileName(deviceId: String) = "$PREFIX$deviceId$SUFFIX"

    /** Device id of a log file, or null when the file is not one of ours. */
    fun deviceIdOf(fileName: String): String? = NAME.matchEntire(fileName)?.groupValues?.get(1)

    private val json = Json {
        classDiscriminator = "t"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(entry: LogEntry): String = json.encodeToString(LogEntry.serializer(), entry)

    fun encode(record: Syncable): String = encode(LogEntry.of(record))

    /** Throws [UnknownEntryException] for a kind written by a newer app version. */
    fun decode(line: String): LogEntry = try {
        json.decodeFromString(LogEntry.serializer(), line)
    } catch (e: SerializationException) {
        throw UnknownEntryException(line.take(80), e)
    } catch (e: IllegalArgumentException) {
        throw UnknownEntryException(line.take(80), e)
    }
}

class UnknownEntryException(line: String, cause: Throwable) : Exception("Не удалось прочитать запись журнала: $line", cause)

@Serializable
sealed interface LogEntry {
    /** Who writes this log; first line of every file. */
    @Serializable
    @SerialName("device")
    data class Device(val deviceId: String, val name: String, val format: Int = SyncLog.FORMAT, val at: Long) : LogEntry

    @Serializable
    @SerialName("currency")
    data class CurrencyRecord(
        val uid: String, val name: String, val symbol: String, val decimals: Int, val isMain: Boolean,
        val rateToMain: Double, val updatedAt: Long, val deleted: Boolean, val deviceId: String,
    ) : LogEntry

    @Serializable
    @SerialName("group")
    data class GroupRecord(
        val uid: String, val name: String, val sortOrder: Int, val archived: Boolean,
        val updatedAt: Long, val deleted: Boolean, val deviceId: String,
    ) : LogEntry

    @Serializable
    @SerialName("account")
    data class AccountRecord(
        val uid: String, val groupUid: String? = null, val name: String, val currencyCode: String,
        val sortOrder: Int, val hidden: Boolean, val note: String = "",
        val updatedAt: Long, val deleted: Boolean, val deviceId: String,
        val isCredit: Boolean = false, val creditLimit: Long = 0, val paymentDay: Int? = null,
    ) : LogEntry

    @Serializable
    @SerialName("category")
    data class CategoryRecord(
        val uid: String, val parentUid: String? = null, val name: String, val type: CategoryType,
        val sortOrder: Int, val archived: Boolean, val updatedAt: Long, val deleted: Boolean, val deviceId: String,
        val icon: String? = null, val color: String? = null,
    ) : LogEntry

    @Serializable
    @SerialName("tx")
    data class TransactionRecord(
        val uid: String, val type: TransactionType, val date: Long, val amount: Long, val currencyCode: String,
        val accountUid: String, val accountAmount: Long, val toAccountUid: String? = null,
        val toAccountAmount: Long? = null, val categoryUid: String? = null, val note: String = "",
        val updatedAt: Long, val deleted: Boolean, val deviceId: String,
        val source: String? = null, val receipt: String? = null,
        val splitUid: String? = null, val splitIndex: Int = 0, val externalId: String? = null,
    ) : LogEntry

    @Serializable
    @SerialName("card")
    data class BankCardRecord(
        val uid: String, val bank: String, val last4: String? = null, val accountUid: String,
        val updatedAt: Long, val deleted: Boolean, val deviceId: String,
    ) : LogEntry

    @Serializable
    @SerialName("register")
    data class CashRegisterRecord(
        val uid: String, val payee: String, val categoryUid: String? = null,
        val updatedAt: Long, val deleted: Boolean, val deviceId: String,
    ) : LogEntry

    @Serializable
    @SerialName("budget")
    data class BudgetRecord(
        val uid: String, val categoryUid: String? = null, val amount: Long, val warn: Boolean = true,
        val updatedAt: Long, val deleted: Boolean, val deviceId: String,
    ) : LogEntry

    /** The domain record this entry carries, null for [Device]. */
    fun toRecord(): Syncable? = when (this) {
        is Device -> null
        is CurrencyRecord -> Currency(uid, name, symbol, decimals, isMain, rateToMain, updatedAt, deleted, deviceId)
        is GroupRecord -> AccountGroup(uid, name, sortOrder, archived, updatedAt, deleted, deviceId)
        is AccountRecord -> Account(
            uid, groupUid, name, currencyCode, sortOrder, hidden, note, updatedAt, deleted, deviceId, isCredit, creditLimit, paymentDay,
        )
        is CategoryRecord -> Category(uid, parentUid, name, type, sortOrder, archived, updatedAt, deleted, deviceId, icon, color)
        is TransactionRecord -> Transaction(
            uid, type, date, amount, currencyCode, accountUid, accountAmount, toAccountUid, toAccountAmount,
            categoryUid, note, updatedAt, deleted, deviceId, source, receipt, splitUid, splitIndex, externalId,
        )
        is BankCardRecord -> BankCard(uid, bank, last4, accountUid, updatedAt, deleted, deviceId)
        is CashRegisterRecord -> CashRegister(uid, payee, categoryUid, updatedAt, deleted, deviceId)
        is BudgetRecord -> Budget(uid, categoryUid, amount, warn, updatedAt, deleted, deviceId)
    }

    companion object {
        fun of(record: Syncable): LogEntry = when (record) {
            is Currency -> with(record) { CurrencyRecord(uid, name, symbol, decimals, isMain, rateToMain, updatedAt, deleted, deviceId) }
            is AccountGroup -> with(record) { GroupRecord(uid, name, sortOrder, archived, updatedAt, deleted, deviceId) }
            is Account -> with(record) {
                AccountRecord(uid, groupUid, name, currencyCode, sortOrder, hidden, note, updatedAt, deleted, deviceId, isCredit, creditLimit, paymentDay)
            }
            is Category -> with(record) { CategoryRecord(uid, parentUid, name, type, sortOrder, archived, updatedAt, deleted, deviceId, icon, color) }
            is Transaction -> with(record) {
                TransactionRecord(
                    uid, type, date, amount, currencyCode, accountUid, accountAmount, toAccountUid, toAccountAmount,
                    categoryUid, note, updatedAt, deleted, deviceId, source, receipt, splitUid, splitIndex, externalId,
                )
            }
            is BankCard -> with(record) { BankCardRecord(uid, bank, last4, accountUid, updatedAt, deleted, deviceId) }
            is CashRegister -> with(record) { CashRegisterRecord(uid, payee, categoryUid, updatedAt, deleted, deviceId) }
            is Budget -> with(record) { BudgetRecord(uid, categoryUid, amount, warn, updatedAt, deleted, deviceId) }
            else -> error("Unknown record type ${record::class.simpleName}")
        }
    }
}
