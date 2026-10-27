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
        CashRegisterEntity::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
    ],
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun accountGroupDao(): AccountGroupDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun syncDao(): SyncDao
    abstract fun bankDao(): BankDao
    abstract fun cashRegisterDao(): CashRegisterDao
}
