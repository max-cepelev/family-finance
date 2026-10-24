package com.familyfinance.data.repository

/**
 * New updatedAt for a local edit: now, but strictly newer than the stored version even when this
 * device's clock is behind, so last-writer-wins keeps the edit.
 */
internal fun nextVersion(previous: Long?): Long = maxOf(System.currentTimeMillis(), (previous ?: 0L) + 1)
