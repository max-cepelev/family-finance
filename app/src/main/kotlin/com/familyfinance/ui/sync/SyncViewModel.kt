package com.familyfinance.ui.sync

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familyfinance.data.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceUi(val name: String, val isThisPhone: Boolean, val lastChangeAt: Long?)

data class SyncUiState(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val folderName: String? = null,
    val lastSuccessAt: Long? = null,
    /** Changes applied by the last round, when it ran in this session. */
    val lastApplied: Int? = null,
    val error: String? = null,
    val logCount: Int? = null,
    val logBytes: Long? = null,
    val devices: List<DeviceUi> = emptyList(),
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val manager: SyncManager,
) : ViewModel() {

    val state: StateFlow<SyncUiState> = combine(
        manager.status,
        manager.observeDevices(),
        manager.observeOwnLastChange(),
    ) { status, others, ownLastChange ->
        val report = status.lastReport
        SyncUiState(
            enabled = status.enabled,
            running = status.running,
            folderName = status.folderName,
            lastSuccessAt = status.lastSuccessAt,
            lastApplied = report?.applied,
            error = status.error,
            logCount = report?.logCount,
            logBytes = report?.logBytes,
            devices = listOf(DeviceUi(manager.deviceName, isThisPhone = true, lastChangeAt = ownLastChange)) +
                others.filter { it.deviceId != manager.ownDeviceId }
                    .map { DeviceUi(it.name, isThisPhone = false, lastChangeAt = it.lastChangeAt.takeIf { at -> at > 0 }) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState(enabled = manager.status.value.enabled))

    fun pickFolder(uri: Uri) {
        viewModelScope.launch { manager.setFolder(uri) }
    }

    fun syncNow() {
        viewModelScope.launch { manager.syncNow() }
    }

    fun disable() {
        viewModelScope.launch { manager.disable() }
    }
}
