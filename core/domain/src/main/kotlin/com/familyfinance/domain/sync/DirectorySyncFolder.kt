package com.familyfinance.domain.sync

import java.io.File
import java.io.RandomAccessFile

/** [SyncFolder] on a plain directory: for tests and a future desktop build. */
class DirectorySyncFolder(private val dir: File) : SyncFolder {

    override suspend fun list(): List<LogFile> =
        dir.listFiles().orEmpty().filter { it.isFile }.map { LogFile(it.name, it.length()) }

    override suspend fun read(name: String, offset: Long): ByteArray =
        RandomAccessFile(File(dir, name), "r").use { file ->
            file.seek(offset)
            ByteArray((file.length() - offset).toInt()).also(file::readFully)
        }

    override suspend fun append(name: String, bytes: ByteArray) {
        File(dir, name).appendBytes(bytes)
    }
}
