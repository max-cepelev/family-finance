package com.familyfinance.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CurrencyEntity::class,
        AccountGroupEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        PendingChangeEntity::class,
        SyncOffsetEntity::class,
        SyncDeviceEntity::class,
        BankCardEntity::class,
        PushSeenEntity::class,
        UnknownCardEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun accountGroupDao(): AccountGroupDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun syncDao(): SyncDao
    abstract fun bankDao(): BankDao
}
