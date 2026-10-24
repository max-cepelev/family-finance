package com.familyfinance.data.sync

import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.PendingChangeEntity
import com.familyfinance.domain.model.Syncable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records local edits in the outbox (call inside the same Room transaction as the write) and tells
 * the sync manager something changed. Changes applied from other devices must not come through here.
 */
@Singleton
class LocalChanges @Inject constructor(private val db: FinanceDatabase) {
    private val _signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val signal: SharedFlow<Unit> = _signal.asSharedFlow()

    suspend fun record(records: List<Syncable>) {
        if (records.isEmpty()) return
        db.syncDao().enqueue(records.map { PendingChangeEntity(kind = RecordKind.of(it), uid = it.uid) })
        _signal.tryEmit(Unit)
    }

    suspend fun record(record: Syncable) = record(listOf(record))
}
