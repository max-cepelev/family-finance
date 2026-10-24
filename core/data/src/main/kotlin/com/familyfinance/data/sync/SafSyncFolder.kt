package com.familyfinance.data.sync

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.familyfinance.domain.sync.LogFile
import com.familyfinance.domain.sync.SyncFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * The folder the user picked with the system folder picker (Storage Access Framework), e.g. a
 * Syncthing folder. No storage permission or root needed; access persists across restarts.
 */
class SafSyncFolder(private val resolver: ContentResolver, private val tree: Uri) : SyncFolder {

    private val treeDocument: Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** name -> document uri, refreshed by [list]. */
    private val documents = HashMap<String, Uri>()

    override suspend fun list(): List<LogFile> = withContext(Dispatchers.IO) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val files = ArrayList<LogFile>()
        documents.clear()
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(3) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                val name = cursor.getString(1) ?: continue
                documents[name] = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                files += LogFile(name, if (cursor.isNull(2)) 0L else cursor.getLong(2))
            }
        } ?: throw FileNotFoundException("Папка недоступна")
        files
    }

    override suspend fun read(name: String, offset: Long): ByteArray = withContext(Dispatchers.IO) {
        val uri = documents[name] ?: throw FileNotFoundException(name)
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(name)
        input.use {
            var skipped = 0L
            while (skipped < offset) {
                val step = it.skip(offset - skipped)
                if (step <= 0) break
                skipped += step
            }
            it.readBytes()
        }
    }

    override suspend fun append(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val uri = documents[name] ?: create(name)
        // "wa": append, supported by the local storage provider that backs Syncthing folders.
        val output = resolver.openOutputStream(uri, "wa") ?: throw FileNotFoundException(name)
        output.use { it.write(bytes) }
    }

    private fun create(name: String): Uri {
        // octet-stream keeps the name as is; a text MIME type could get ".txt" appended by the provider.
        val uri = DocumentsContract.createDocument(resolver, treeDocument, "application/octet-stream", name)
            ?: throw FileNotFoundException("Не удалось создать $name")
        documents[name] = uri
        return uri
    }
}
