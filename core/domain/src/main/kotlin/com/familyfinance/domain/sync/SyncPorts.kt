package com.familyfinance.domain.sync

import com.familyfinance.domain.model.Syncable

/** A log file in the shared folder. */
data class LogFile(val name: String, val size: Long)

/**
 * The shared folder (Syncthing via SAF on Android; any other transport later). Only file-level
 * operations, so a WebDAV or cloud-drive transport fits the same interface.
 */
interface SyncFolder {
    suspend fun list(): List<LogFile>

    /** Bytes of [name] from [offset] to the end. */
    suspend fun read(name: String, offset: Long): ByteArray

    /** Appends to [name], creating it when missing. */
    suspend fun append(name: String, bytes: ByteArray)
}

/** A local change waiting to be written to this device's log. */
data class PendingChange(val seq: Long, val record: Syncable)

/** What was read from another device's log, applied atomically together with the new offset. */
data class RemoteBatch(
    val fileName: String,
    val deviceId: String,
    val deviceName: String?,
    val records: List<Syncable>,
    val newOffset: Long,
)

/** Local side of sync: the outbox of own changes, applying remote ones, read positions. */
interface SyncStore {
    /** Oldest first; each record in its current stored state. */
    suspend fun pendingChanges(limit: Int): List<PendingChange>

    suspend fun markExported(upToSeq: Long)

    /** Puts every stored record into the outbox, for a first export to a new folder. */
    suspend fun enqueueAll()

    suspend fun readOffset(fileName: String): Long

    /** Applies by last-writer-wins without queueing them again; returns how many records changed. */
    suspend fun applyRemote(batch: RemoteBatch): Int
}
