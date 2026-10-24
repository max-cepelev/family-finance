package com.familyfinance.data.repository

import androidx.room.withTransaction
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.VersionRow
import com.familyfinance.data.db.toEntity
import com.familyfinance.data.sync.LocalChanges
import com.familyfinance.domain.importer.ImportBundle
import com.familyfinance.domain.importer.ImportCounts
import com.familyfinance.domain.importer.ImportResult
import com.familyfinance.domain.repository.ImportRepository
import com.familyfinance.domain.sync.LastWriterWins
import com.familyfinance.domain.sync.Version
import javax.inject.Inject

class RoomImportRepository @Inject constructor(
    private val db: FinanceDatabase,
    private val changes: LocalChanges,
) : ImportRepository {

    override suspend fun apply(bundle: ImportBundle): ImportResult = db.withTransaction {
        val currencies = LastWriterWins.newer(bundle.currencies, db.currencyDao().versions().toMap())
        val groups = LastWriterWins.newer(bundle.groups, db.accountGroupDao().versions().toMap())
        val accounts = LastWriterWins.newer(bundle.accounts, db.accountDao().versions().toMap())
        val categories = LastWriterWins.newer(bundle.categories, db.categoryDao().versions().toMap())
        val transactions = LastWriterWins.newer(bundle.transactions, db.transactionDao().versions().toMap())

        db.currencyDao().upsert(currencies.map { it.toEntity() })
        db.accountGroupDao().upsert(groups.map { it.toEntity() })
        db.accountDao().upsert(accounts.map { it.toEntity() })
        db.categoryDao().upsert(categories.map { it.toEntity() })
        transactions.chunked(BATCH).forEach { chunk -> db.transactionDao().upsert(chunk.map { it.toEntity() }) }
        // Imported data reaches the other phone through the sync log like any other edit.
        changes.record(currencies + groups + accounts + categories + transactions)

        ImportResult(
            report = bundle.report,
            received = bundle.counts(),
            applied = ImportCounts(currencies.size, groups.size, accounts.size, categories.size, transactions.size),
        )
    }

    private fun List<VersionRow>.toMap() = associate { it.uid to Version(it.updatedAt, it.deviceId) }

    private companion object {
        const val BATCH = 500
    }
}
