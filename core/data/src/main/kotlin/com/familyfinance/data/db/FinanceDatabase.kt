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
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun accountGroupDao(): AccountGroupDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun syncDao(): SyncDao
}
