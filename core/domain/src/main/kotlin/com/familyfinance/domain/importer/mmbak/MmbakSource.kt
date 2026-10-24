package com.familyfinance.domain.importer.mmbak

import java.io.Closeable

/**
 * Read-only access to an opened .mmbak (SQLite) file. Implemented with android.database.sqlite
 * on the device and with JDBC in tests, so the mapping itself stays platform independent.
 */
interface MmbakSource : Closeable {
    val userVersion: Int
    fun tableNames(): Set<String>
    fun <T> query(sql: String, map: (MmbakRow) -> T): List<T>
}

interface MmbakRow {
    /** Column value as text (SQLite converts numbers), or null for NULL. */
    fun string(column: String): String?
}

object MmbakFormat {
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    const val HEADER_SIZE = 16

    fun hasSqliteHeader(header: ByteArray): Boolean =
        header.size >= HEADER_SIZE && SQLITE_MAGIC.indices.all { header[it] == SQLITE_MAGIC[it] }
}
