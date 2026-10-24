package com.familyfinance.domain.model

/**
 * Every record that travels between devices. Conflicts are resolved per record:
 * the version with the larger [updatedAt] wins (last-writer-wins).
 */
interface Syncable {
    val uid: String
    /** Epoch millis of the last change. */
    val updatedAt: Long
    /** Soft delete (tombstone) so the deletion itself can be synced. */
    val deleted: Boolean
    /** Device that made the last change. */
    val deviceId: String
}
