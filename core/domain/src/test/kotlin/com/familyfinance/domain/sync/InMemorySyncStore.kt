package com.familyfinance.domain.sync

import com.familyfinance.domain.model.Syncable

/** A device's local database for engine tests. */
class InMemorySyncStore : SyncStore {
    val records = LinkedHashMap<Pair<String, String>, Syncable>()
    private val outbox = ArrayList<Pair<Long, Pair<String, String>>>()
    private val offsets = HashMap<String, Long>()
    val deviceNames = HashMap<String, String>()
    private var seq = 0L

    private fun key(record: Syncable) = record::class.simpleName!! to record.uid

    /** A local edit: stored and queued for export. */
    fun put(record: Syncable) {
        records[key(record)] = record
        outbox += ++seq to key(record)
    }

    inline fun <reified T : Syncable> get(uid: String): T? = records.values.filterIsInstance<T>().firstOrNull { it.uid == uid }

    override suspend fun pendingChanges(limit: Int): List<PendingChange> =
        outbox.take(limit).map { (s, k) -> PendingChange(s, records.getValue(k)) }

    override suspend fun markExported(upToSeq: Long) {
        outbox.removeAll { it.first <= upToSeq }
    }

    override suspend fun enqueueAll() {
        records.keys.forEach { outbox += ++seq to it }
    }

    override suspend fun readOffset(fileName: String): Long = offsets[fileName] ?: 0L

    override suspend fun applyRemote(batch: RemoteBatch): Int {
        var applied = 0
        for (record in batch.records) {
            val local = records[key(record)]
            if (LastWriterWins.wins(record, local?.let { Version(it.updatedAt, it.deviceId) })) {
                records[key(record)] = record
                applied++
            }
        }
        offsets[batch.fileName] = batch.newOffset
        batch.deviceName?.let { deviceNames[batch.deviceId] = it }
        return applied
    }
}
