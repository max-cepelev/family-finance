package com.familyfinance.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.familyfinance.data.db.FinanceDatabase
import com.familyfinance.data.db.SyncDeviceEntity
import com.familyfinance.data.device.DeviceIdentity
import com.familyfinance.domain.sync.SyncEngine
import com.familyfinance.domain.sync.SyncReport
import com.familyfinance.domain.sync.SyncStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatus(
    val folder: Uri? = null,
    /** Human-readable folder path, e.g. "Syncthing/Семейный бюджет". */
    val folderName: String? = null,
    val running: Boolean = false,
    val lastSuccessAt: Long? = null,
    val lastReport: SyncReport? = null,
    val error: String? = null,
) {
    val enabled: Boolean get() = folder != null
}

/** Runs sync rounds against the folder chosen by the user and publishes the status for the UI. */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FinanceDatabase,
    private val store: SyncStore,
    private val device: DeviceIdentity,
    private val changes: LocalChanges,
) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _status = MutableStateFlow(loadStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    val deviceName: String get() = prefs.getString(KEY_NAME, null) ?: "${Build.MANUFACTURER} ${Build.MODEL}"
    val ownDeviceId: String get() = device.deviceId

    fun observeDevices(): Flow<List<SyncDeviceEntity>> = db.syncDao().observeDevices()

    /** When this phone last changed an operation, for the device list. */
    fun observeOwnLastChange(): Flow<Long?> = db.transactionDao().observeLastChangeBy(device.deviceId)

    /** Called with the result of ACTION_OPEN_DOCUMENT_TREE. */
    suspend fun setFolder(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val previous = _status.value.folder
        if (previous != null && previous != uri) release(previous)
        // Read positions belong to the old folder's files.
        if (previous != uri) db.syncDao().clearOffsets()
        prefs.edit().putString(KEY_FOLDER, uri.toString()).remove(KEY_LAST_SUCCESS).apply()
        _status.value = SyncStatus(folder = uri, folderName = displayName(uri))
        syncNow()
    }

    suspend fun disable() {
        mutex.withLock {
            _status.value.folder?.let(::release)
            prefs.edit().remove(KEY_FOLDER).remove(KEY_LAST_SUCCESS).apply()
            db.syncDao().clearOffsets()
            _status.value = SyncStatus()
        }
    }

    /** One round now; no-op without a folder. Never throws: errors go to [status]. */
    suspend fun syncNow(): SyncReport? = mutex.withLock {
        val folder = _status.value.folder ?: return@withLock null
        _status.update { it.copy(running = true) }
        try {
            val engine = SyncEngine(device.deviceId, deviceName, SafSyncFolder(context.contentResolver, folder), store)
            val report = engine.sync()
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SUCCESS, now).apply()
            _status.update {
                it.copy(
                    running = false,
                    lastSuccessAt = now,
                    lastReport = report,
                    error = if (report.blockedLogs.isEmpty()) null
                    else "Второй телефон пишет в формате новее этого приложения — обновите приложение",
                )
            }
            report
        } catch (e: SecurityException) {
            fail("Нет доступа к папке — выберите её заново")
        } catch (e: FileNotFoundException) {
            fail("Папка недоступна: ${e.message}")
        } catch (e: Exception) {
            fail("Ошибка синхронизации: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Keeps syncing while the app is on screen and a folder is set: right away, shortly after local
     * edits, and every minute to pick up the other phone's changes. Idle (no timers) without a folder.
     * Cancel the caller's scope to stop.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    suspend fun runWhileVisible() {
        status.map { it.enabled }.distinctUntilChanged().collectLatest { enabled ->
            if (!enabled) return@collectLatest
            coroutineScope {
                launch { changes.signal.debounce(EDIT_DEBOUNCE_MS).collect { syncNow() } }
                while (isActive) {
                    syncNow()
                    delay(POLL_MS)
                }
            }
        }
    }

    private fun fail(message: String): SyncReport? {
        _status.update { it.copy(running = false, error = message) }
        return null
    }

    private fun release(uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    private fun loadStatus(): SyncStatus {
        val folder = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse) ?: return SyncStatus()
        return SyncStatus(
            folder = folder,
            folderName = displayName(folder),
            lastSuccessAt = prefs.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0 },
        )
    }

    /** "primary:Syncthing/Семья" -> "Syncthing/Семья". */
    private fun displayName(uri: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfter(':') }.getOrNull()
            ?.ifEmpty { "Внутренняя память" } ?: uri.toString()

    private companion object {
        const val KEY_FOLDER = "folder"
        const val KEY_NAME = "device_name"
        const val KEY_LAST_SUCCESS = "last_success"
        const val EDIT_DEBOUNCE_MS = 2_000L
        const val POLL_MS = 60_000L
    }
}
