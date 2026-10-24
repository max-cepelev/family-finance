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
    indices = [Index("date"), Index("accountUid"), Index("toAccountUid"), Index("categoryUid")],
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
