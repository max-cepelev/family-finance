package com.familyfinance.data.di

import android.content.Context
import androidx.room.Room
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.device.DeviceIdStore
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.data.repository.RoomAccountRepository
import com.familyfinance.data.repository.RoomBankCardRepository
import com.familyfinance.data.repository.RoomCategoryRepository
import com.familyfinance.data.repository.RoomImportRepository
import com.familyfinance.data.repository.RoomTransactionRepository
import com.familyfinance.data.sync.RoomSyncStore
import com.familyfinance.domain.repository.AccountRepository
import com.familyfinance.domain.repository.BankCardRepository
import com.familyfinance.domain.repository.CategoryRepository
import com.familyfinance.domain.repository.ImportRepository
import com.familyfinance.domain.repository.TransactionRepository
import com.familyfinance.domain.sync.SyncStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FinanceDatabase =
        Room.databaseBuilder(context, FinanceDatabase::class.java, "finance.db").build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun accounts(impl: RoomAccountRepository): AccountRepository
    @Binds abstract fun transactions(impl: RoomTransactionRepository): TransactionRepository
    @Binds abstract fun categories(impl: RoomCategoryRepository): CategoryRepository
    @Binds abstract fun import(impl: RoomImportRepository): ImportRepository
    @Binds abstract fun deviceIdentity(impl: DeviceIdStore): DeviceIdentity
    @Binds abstract fun syncStore(impl: RoomSyncStore): SyncStore
    @Binds abstract fun bankCards(impl: RoomBankCardRepository): BankCardRepository
}
