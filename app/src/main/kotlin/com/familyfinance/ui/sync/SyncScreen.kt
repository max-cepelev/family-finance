package com.familyfinance.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.rememberNow
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors

@Composable
fun SyncScreen(onClose: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.pickFolder(uri)
    }
    SyncContent(
        state = state,
        now = rememberNow(state),
        onClose = onClose,
        onSyncNow = viewModel::syncNow,
        onChangeFolder = { picker.launch(null) },
        onDisable = { viewModel.disable(); onClose() },
    )
}

/** Stateless body, also rendered directly in screenshot tests. */
@Composable
fun SyncContent(
    state: SyncUiState,
    now: Long,
    onClose: () -> Unit,
    onSyncNow: () -> Unit,
    onChangeFolder: () -> Unit,
    onDisable: () -> Unit,
) {
    var confirmDisable by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(FinanceColors.Ground).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Синхронизация", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state, now, onSyncNow)

            Section("Папка") {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = FinanceColors.Muted)
                        Column(Modifier.weight(1f)) {
                            Text(state.folderName.orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            SyncTexts.folderDetails(state)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
                            }
                        }
                        TextButton(onClick = onChangeFolder) { Text("Изменить", color = FinanceColors.Primary, fontWeight = FontWeight.Medium) }
                    }
                }
            }

            Section("Устройства") {
                WhiteCard(Modifier.fillMaxWidth()) {
                    state.devices.forEachIndexed { index, device ->
                        if (index > 0) HorizontalDivider(color = FinanceColors.Hairline)
                        DeviceRow(device, now)
                    }
                    if (state.devices.size < 2) {
                        HorizontalDivider(color = FinanceColors.Hairline)
                        Text(
                            "Второй телефон появится здесь после его первой синхронизации с этой папкой",
                            style = MaterialTheme.typography.bodySmall,
                            color = FinanceColors.Muted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            Text(
                "Каждый телефон пишет изменения в свой файл в этой папке, Syncthing переносит файлы между телефонами. " +
                    "Если одну запись изменили на обоих, остаётся более поздняя правка.",
                style = MaterialTheme.typography.bodySmall,
                color = FinanceColors.Muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        TextButton(
            onClick = { confirmDisable = true },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp).heightIn(min = 44.dp),
        ) { Text("Отключить синхронизацию", color = FinanceColors.Expense, fontWeight = FontWeight.Medium) }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Отключить синхронизацию?") },
            text = { Text("Файлы в папке останутся. Этот телефон перестанет отправлять и получать изменения, пока папку не выберут снова.") },
            confirmButton = {
                TextButton(onClick = { confirmDisable = false; onDisable() }) { Text("Отключить", color = FinanceColors.Expense) }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun StatusCard(state: SyncUiState, now: Long, onSyncNow: () -> Unit) {
    WhiteCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val failed = state.error != null
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (failed) Color(0xFFFBE3E1) else FinanceColors.PrimarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.running -> CircularProgressIndicator(Modifier.size(20.dp), color = FinanceColors.OnPrimarySoft, strokeWidth = 2.dp)
                        failed -> Icon(Icons.Filled.PriorityHigh, contentDescription = null, tint = FinanceColors.Expense)
                        else -> Icon(Icons.Filled.Check, contentDescription = null, tint = FinanceColors.OnPrimarySoft)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(SyncTexts.title(state), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    SyncTexts.details(state, now)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = if (state.error != null) FinanceColors.Expense else FinanceColors.Muted)
                    }
                }
            }
            OutlinedButton(
                onClick = onSyncNow,
                enabled = !state.running,
                border = BorderStroke(1.dp, if (state.running) FinanceColors.ChipBorder else FinanceColors.Primary),
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null, tint = FinanceColors.Primary, modifier = Modifier.size(18.dp))
                Text(
                    "Синхронизировать сейчас",
                    color = if (state.running) FinanceColors.Muted else FinanceColors.Primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceUi, now: Long) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = FinanceColors.Muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                buildAnnotatedString {
                    append(device.name)
                    if (device.isThisPhone) withStyle(SpanStyle(color = FinanceColors.Muted)) { append(" · этот телефон") }
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                device.lastChangeAt?.let { "Изменение " + Dates.moment(it, now) } ?: "Изменений пока нет",
                style = MaterialTheme.typography.bodySmall,
                color = FinanceColors.Muted,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = FinanceColors.Muted, modifier = Modifier.padding(horizontal = 4.dp))
        content()
    }
}
