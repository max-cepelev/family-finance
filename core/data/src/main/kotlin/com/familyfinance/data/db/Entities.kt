package com.familyfinance.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.domain.model.TransactionType

// Every table carries the sync fields (uid, updatedAt, deleted, deviceId); see domain Syncable.

@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val isMain: Boolean,
    val rateToMain: Double,
    val updatedAt: Long,
    val deleted: Boolean,
    val deviceId: String,
)

@Entity(tableName = "account_groups")
data class AccountGroupEntity(
    @PrimaryKey val uid: String,
    val name: String,
    val sortOrder: Int,
    val archived: Boolean,
    val updatedAt: Long,
    val deleted: Boolean,
    val deviceId: String,
)

@Entity(tableName = "accounts", indices = [Index("groupUid")])
data class AccountEntity(
    @PrimaryKey val uid: String,
    val groupUid: String?,
    val name: String,
    val currencyCode: String,
    val sortOrder: Int,
    val hidden: Boolean,
    val note: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val deviceId: String,
)

@Entity(tableName = "categories", indices = [Index("parentUid")])
data class CategoryEntity(
    @PrimaryKey val uid: String,
    val parentUid: String?,
    val name: String,
    val type: CategoryType,
    val sortOrder: Int,
    val archived: Boolean,
    val updatedAt: Long,
    val deleted: Boolean,
    val deviceId: String,
    /** Emoji; added in schema version 3. */
    val icon: String? = null,
)

@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("accountUid"), Index("toAccountUid"), Index("categoryUid"), Index("splitUid")],
)
data class TransactionEntity(
    @PrimaryKey val uid: String,
    val type: TransactionType,
    val date: Long,
    val amount: Long,
    val currencyCode: String,
    val accountUid: String,
    val accountAmount: Long,
    val toAccountUid: String?,
    val toAccountAmount: Long?,
    val categoryUid: String?,
    val note: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val deviceId: String,
    /** "push" for operations from bank notifications; added in schema version 4. */
    val source: String? = null,
    /** Canonical QR text of the attached receipt; added in schema version 5. */
    val receipt: String? = null,
    /** Parts of an operation split across categories share it; added in schema version 6. */
    val splitUid: String? = null,
    @ColumnInfo(defaultValue = "0") val splitIndex: Int = 0,
)

/** A card of a bank linked to an account; last4 null is the bank's fallback. Synced like other records. */
@Entity(tableName = "bank_cards")
data class BankCardEntity(
    @PrimaryKey val uid: String,
    val bank: String,
    val last4: String?,
    val accountUid: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val deviceId: String,
)

/** Shop name and category remembered for a cash register (uid = fiscal storage number). Synced. */
@Entity(tableName = "cash_registers")
data class CashRegisterEntity(
    @PrimaryKey val uid: String,
    val payee: String,
    val categoryUid: String?,
    val updatedAt: Long,
    val deleted: Boolean,
    val deviceId: String,
)

/** Notifications already turned into operations, so a re-posted one is not added twice. Local only. */
@Entity(tableName = "push_seen")
data class PushSeenEntity(
    @PrimaryKey val key: String,
    val seenAt: Long,
)

/** A card seen in a notification but not linked yet; feeds the "Привязать" hint. Local only. */
@Entity(tableName = "push_unknown_cards", primaryKeys = ["bank", "last4"])
data class UnknownCardEntity(
    val bank: String,
    val last4: String,
    val lastSeenAt: Long,
    /** Account the operation went to through the bank fallback; null when it could not be recorded. */
    val recordedAccountUid: String?,
)

/** Local change waiting to be appended to this device's sync log (the outbox). */
@Entity(tableName = "pending_changes")
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val kind: String,
    val uid: String,
)

/** How far another device's log has been read. */
@Entity(tableName = "sync_offsets")
data class SyncOffsetEntity(
    @PrimaryKey val fileName: String,
    val offset: Long,
)

/** Another device seen in the sync folder. */
@Entity(tableName = "sync_devices")
data class SyncDeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    /** Newest change received from it (its own updatedAt), 0 if none yet. */
    val lastChangeAt: Long,
)

/** uid -> updatedAt projection used for last-writer-wins merges. */
data class VersionRow(
    @ColumnInfo(name = "uid") val uid: String,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
    @ColumnInfo(name = "deviceId") val deviceId: String,
)

data class UsageRow(
    @ColumnInfo(name = "categoryUid") val categoryUid: String,
    @ColumnInfo(name = "count") val count: Int,
)

data class BalanceRow(
    @ColumnInfo(name = "uid") val uid: String,
    @ColumnInfo(name = "balance") val balance: Long,
)
