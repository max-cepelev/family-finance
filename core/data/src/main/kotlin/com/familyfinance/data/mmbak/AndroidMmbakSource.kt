package com.familyfinance.data.mmbak

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.familyfinance.domain.importer.mmbak.MmbakRow
import com.familyfinance.domain.importer.mmbak.MmbakSource
import java.io.File

class AndroidMmbakSource(file: File) : MmbakSource {
    private val db = SQLiteDatabase.openDatabase(
        file.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
    )

    override val userVersion: Int get() = db.version

    override fun tableNames(): Set<String> =
        query("SELECT name FROM sqlite_master WHERE type = 'table'") { it.string("name").orEmpty() }.toSet()

    override fun <T> query(sql: String, map: (MmbakRow) -> T): List<T> =
        db.rawQuery(sql, null).use { cursor ->
            val row = CursorRow(cursor)
            buildList(cursor.count) { while (cursor.moveToNext()) add(map(row)) }
        }

    override fun close() = db.close()

    private class CursorRow(private val cursor: Cursor) : MmbakRow {
        override fun string(column: String): String? {
            val index = cursor.getColumnIndexOrThrow(column)
            return when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> null
                // CursorWindow turns REAL into text with "%g", i.e. 6 significant digits: 222699.08 -> "222699".
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toString()
                else -> cursor.getString(index)
            }
        }
    }
}
