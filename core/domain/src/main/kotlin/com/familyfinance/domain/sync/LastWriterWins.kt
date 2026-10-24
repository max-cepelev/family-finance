package com.familyfinance.domain.sync

import com.familyfinance.domain.model.Syncable

/** The stored version of a record, enough to decide a conflict. */
data class Version(val updatedAt: Long, val deviceId: String)

object LastWriterWins {

    /**
     * True when [incoming] should replace [local]: unknown locally, or newer. Equal timestamps are
     * broken by device id, so every device picks the same winner and the copies converge.
     */
    fun wins(incoming: Syncable, local: Version?): Boolean = when {
        local == null -> true
        incoming.updatedAt != local.updatedAt -> incoming.updatedAt > local.updatedAt
        else -> incoming.deviceId > local.deviceId
    }

    /** Keeps the incoming records that should overwrite local state. */
    fun <T : Syncable> newer(incoming: Iterable<T>, local: Map<String, Version>): List<T> =
        incoming.filter { wins(it, local[it.uid]) }
}
