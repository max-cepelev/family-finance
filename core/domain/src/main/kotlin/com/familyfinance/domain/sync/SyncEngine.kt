package com.familyfinance.domain.sync

import com.familyfinance.domain.model.Syncable

data class SyncReport(
    /** Own changes written to this device's log. */
    val exported: Int,
    /** Records read from other devices' logs. */
    val received: Int,
    /** Of [received], how many changed local data. */
    val applied: Int,
    /** Other devices' logs seen in the folder. */
    val remoteLogs: Int,
    /** Logs that stopped at an entry this app version cannot read. */
    val blockedLogs: List<String> = emptyList(),
    /** Log files in the folder after this round (own included) and their total size. */
    val logCount: Int = 0,
    val logBytes: Long = 0,
)

/**
 * One sync round: append own pending changes to this device's log, then read what other devices
 * appended since the last round and apply it by last-writer-wins.
 */
class SyncEngine(
    private val deviceId: String,
    private val deviceName: String,
    private val folder: SyncFolder,
    private val store: SyncStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val ownFile = SyncLog.fileName(deviceId)

    suspend fun sync(): SyncReport {
        val files = folder.list()
        val ownExisted = files.any { it.name == ownFile }
        val (exported, written) = export(ownLogExists = ownExisted)
        var received = 0
        var applied = 0
        var remote = 0
        val blocked = ArrayList<String>()
        for (file in files) {
            val remoteDevice = SyncLog.deviceIdOf(file.name) ?: continue
            if (file.name == ownFile) continue
            remote++
            val result = import(file, remoteDevice)
            received += result.received
            applied += result.applied
            if (result.blocked) blocked += file.name
        }
        val logs = files.filter { SyncLog.deviceIdOf(it.name) != null }
        return SyncReport(
            exported, received, applied, remote, blocked,
            logCount = logs.size + if (ownExisted) 0 else 1,
            logBytes = logs.sumOf { it.size } + written,
        )
    }

    /** Returns (records exported, bytes appended). */
    private suspend fun export(ownLogExists: Boolean): Pair<Int, Long> {
        var bytes = 0L
        if (!ownLogExists) {
            // New folder (or our log was removed): publish the whole local state, not just recent edits.
            store.enqueueAll()
            val header = line(SyncLog.encode(LogEntry.Device(deviceId, deviceName, at = clock())))
            folder.append(ownFile, header)
            bytes += header.size
        }
        var total = 0
        while (true) {
            val batch = store.pendingChanges(BATCH)
            if (batch.isEmpty()) return total to bytes
            // A record edited twice before export is written once, in its current state.
            val latest = batch.associateBy { it.record::class to it.record.uid }.values.sortedBy { it.seq }
            val text = buildString { latest.forEach { append(SyncLog.encode(it.record)).append('\n') } }
                .toByteArray(Charsets.UTF_8)
            folder.append(ownFile, text)
            bytes += text.size
            store.markExported(batch.last().seq)
            total += latest.size
        }
    }

    private class ImportResult(val received: Int, val applied: Int, val blocked: Boolean)

    private suspend fun import(file: LogFile, remoteDevice: String): ImportResult {
        var offset = store.readOffset(file.name)
        // A log never shrinks while appended; a smaller file was rewritten, so read it again (idempotent).
        if (file.size < offset) offset = 0
        if (file.size == offset) return ImportResult(0, 0, false)

        val bytes = folder.read(file.name, offset)
        // Only complete lines: a line still being transferred ends without '\n' and waits for next time.
        val end = bytes.lastIndexOf(NEWLINE) + 1
        if (end == 0) return ImportResult(0, 0, false)

        val records = ArrayList<Syncable>()
        var deviceName: String? = null
        var pos = 0
        var blocked = false
        while (pos < end) {
            var newline = pos
            while (bytes[newline] != NEWLINE) newline++
            val text = String(bytes, pos, newline - pos, Charsets.UTF_8)
            if (text.isNotBlank()) {
                val entry = try {
                    SyncLog.decode(text)
                } catch (e: UnknownEntryException) {
                    // Written by a newer app version: stop before it and retry after the app is updated.
                    blocked = true
                    break
                }
                when (entry) {
                    is LogEntry.Device -> deviceName = entry.name
                    else -> entry.toRecord()?.let(records::add)
                }
            }
            pos = newline + 1
        }
        val newOffset = offset + pos
        val applied = store.applyRemote(RemoteBatch(file.name, remoteDevice, deviceName, records, newOffset))
        return ImportResult(records.size, applied, blocked)
    }

    private fun line(text: String) = (text + "\n").toByteArray(Charsets.UTF_8)

    private companion object {
        const val BATCH = 500
        const val NEWLINE = '\n'.code.toByte()
    }
}
