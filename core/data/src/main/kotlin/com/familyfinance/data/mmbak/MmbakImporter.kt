package com.familyfinance.data.mmbak

import android.content.Context
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.domain.importer.ImportFormatException
import com.familyfinance.domain.importer.ImportResult
import com.familyfinance.domain.importer.mmbak.MmbakFormat
import com.familyfinance.domain.importer.mmbak.MmbakReader
import com.familyfinance.domain.repository.ImportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/** One-off import of a Money Manager backup picked by the user. */
class MmbakImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val device: DeviceIdentity,
    private val importRepository: ImportRepository,
) {

    /** [open] gives the backup content, e.g. from a SAF Uri. */
    suspend fun import(open: () -> InputStream): ImportResult {
        // SQLite needs a real file, so the picked document is copied to the cache first.
        val copy = File.createTempFile("import", ".mmbak", context.cacheDir)
        try {
            withContext(Dispatchers.IO) {
                open().use { input -> copy.outputStream().use { input.copyTo(it) } }
                val header = copy.inputStream().use { it.readNBytesCompat(MmbakFormat.HEADER_SIZE) }
                if (!MmbakFormat.hasSqliteHeader(header)) {
                    throw ImportFormatException("Файл не является бэкапом Money Manager (.mmbak)")
                }
            }
            val bundle = withContext(Dispatchers.IO) {
                AndroidMmbakSource(copy).use { MmbakReader(device.deviceId).read(it) }
            }
            return importRepository.apply(bundle)
        } finally {
            copy.delete()
            File(copy.path + "-journal").delete()
        }
    }

    private fun InputStream.readNBytesCompat(n: Int): ByteArray {
        val buffer = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = read(buffer, read, n - read)
            if (r < 0) break
            read += r
        }
        return buffer.copyOf(read)
    }
}
