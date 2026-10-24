package com.familyfinance.domain.importer.mmbak

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class JdbcMmbakSource(file: File) : MmbakSource {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")

    override val userVersion: Int
        get() = query("PRAGMA user_version") { it.string("user_version")!!.toInt() }.single()

    override fun tableNames(): Set<String> =
        query("SELECT name FROM sqlite_master WHERE type = 'table'") { it.string("name")!! }.toSet()

    override fun <T> query(sql: String, map: (MmbakRow) -> T): List<T> =
        connection.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                val row = object : MmbakRow {
                    override fun string(column: String): String? = rs.getString(column)
                }
                buildList { while (rs.next()) add(map(row)) }
            }
        }

    override fun close() = connection.close()

}
