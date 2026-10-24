package com.familyfinance.data.sync

import androidx.room.withTransaction
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.SyncDeviceEntity
import com.familyfinance.data.db.SyncOffsetEntity
import com.familyfinance.data.db.toDomain
import com.familyfinance.data.db.toEntity
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.model.Syncable
import com.familyfinance.domain.model.Transaction
import com.familyfinance.domain.sync.LastWriterWins
import com.familyfinance.domain.sync.PendingChange
import com.familyfinance.domain.sync.RemoteBatch
import com.familyfinance.domain.sync.SyncStore
import com.familyfinance.domain.sync.Version
import javax.inject.Inject

class RoomSyncStore @Inject constructor(private val db: FinanceDatabase) : SyncStore {
    private val dao get() = db.syncDao()

    override suspend fun pendingChanges(limit: Int): List<PendingChange> = db.withTransaction {
        var batch: List<PendingChange>? = null
        while (batch == null) {
            val rows = dao.pending(limit)
            if (rows.isEmpty()) {
                batch = emptyList()
                continue
            }
            val found = HashMap<Pair<String, String>, Syncable>()
            rows.groupBy { it.kind }.forEach { (kind, group) ->
                load(kind, group.map { it.uid }.distinct()).forEach { found[kind to it.uid] = it }
            }
            val (present, orphans) = rows.partition { (it.kind to it.uid) in found }
            // A queued record that no longer exists cannot be exported; drop it so the queue can advance.
            if (orphans.isNotEmpty()) dao.deletePending(orphans.map { it.seq })
            if (present.isNotEmpty()) batch = present.map { PendingChange(it.seq, found.getValue(it.kind to it.uid)) }
        }
        batch
    }

    override suspend fun markExported(upToSeq: Long) = dao.deletePendingUpTo(upToSeq)

    override suspend fun enqueueAll() = db.withTransaction {
        // Everything goes out anyway, so earlier queued entries would only be duplicates.
        dao.clearPending()
        dao.enqueueAllCurrencies()
        dao.enqueueAllGroups()
        dao.enqueueAllAccounts()
        dao.enqueueAllCategories()
        dao.enqueueAllTransactions()
    }

    override suspend fun readOffset(fileName: String): Long = dao.offset(fileName) ?: 0L

    override suspend fun applyRemote(batch: RemoteBatch): Int = db.withTransaction {
        var applied = 0
        applied += apply(batch.records.filterIsInstance<Currency>(), RecordKind.CURRENCY) { list ->
            db.currencyDao().upsert(list.map { it.toEntity() })
        }
        applied += apply(batch.records.filterIsInstance<AccountGroup>(), RecordKind.GROUP) { list ->
            db.accountGroupDao().upsert(list.map { it.toEntity() })
        }
        applied += apply(batch.records.filterIsInstance<Account>(), RecordKind.ACCOUNT) { list ->
            db.accountDao().upsert(list.map { it.toEntity() })
        }
        applied += apply(batch.records.filterIsInstance<Category>(), RecordKind.CATEGORY) { list ->
            db.categoryDao().upsert(list.map { it.toEntity() })
        }
        applied += apply(batch.records.filterIsInstance<Transaction>(), RecordKind.TRANSACTION) { list ->
            db.transactionDao().upsert(list.map { it.toEntity() })
        }
        dao.setOffset(SyncOffsetEntity(batch.fileName, batch.newOffset))

        val previous = dao.device(batch.deviceId)
        val newest = batch.records.filter { it.deviceId == batch.deviceId }.maxOfOrNull { it.updatedAt } ?: 0L
        val name = batch.deviceName ?: previous?.name
        if (name != null) {
            dao.upsertDevice(SyncDeviceEntity(batch.deviceId, name, maxOf(newest, previous?.lastChangeAt ?: 0L)))
        }
        applied
    }

    /** Last-writer-wins against the stored versions, in chunks below SQLite's variable limit. */
    private suspend fun <T : Syncable> apply(records: List<T>, kind: String, upsert: suspend (List<T>) -> Unit): Int {
        if (records.isEmpty()) return 0
        // The same record twice in one batch: the later line is the newer state.
        val latest = records.associateBy { it.uid }.values.toList()
        var applied = 0
        for (chunk in latest.chunked(CHUNK)) {
            val local = load(kind, chunk.map { it.uid }).associate { it.uid to Version(it.updatedAt, it.deviceId) }
            val winners = LastWriterWins.newer(chunk, local)
            if (winners.isNotEmpty()) upsert(winners)
            applied += winners.size
        }
        return applied
    }

    private suspend fun load(kind: String, uids: List<String>): List<Syncable> =
        uids.chunked(CHUNK).flatMap { chunk ->
            when (kind) {
                RecordKind.CURRENCY -> dao.currencies(chunk).map { it.toDomain() }
                RecordKind.GROUP -> dao.groups(chunk).map { it.toDomain() }
                RecordKind.ACCOUNT -> dao.accounts(chunk).map { it.toDomain() }
                RecordKind.CATEGORY -> dao.categories(chunk).map { it.toDomain() }
                RecordKind.TRANSACTION -> dao.transactions(chunk).map { it.toDomain() }
                else -> emptyList()
            }
        }

    private companion object {
        const val CHUNK = 500
    }
}
