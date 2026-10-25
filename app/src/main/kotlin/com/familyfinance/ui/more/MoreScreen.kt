package com.familyfinance.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.importer.ImportResult
import com.familyfinance.ui.common.ScreenTitle
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.common.rememberNow
import com.familyfinance.ui.sync.SyncTexts
import com.familyfinance.ui.sync.SyncUiState
import com.familyfinance.ui.sync.SyncViewModel
import com.familyfinance.ui.theme.FinanceColors

@Composable
fun MoreScreen(
    onOpenSync: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBanks: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
    syncViewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sync by syncViewModel.state.collectAsStateWithLifecycle()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) syncViewModel.pickFolder(uri)
    }
    // .mmbak has no registered MIME type, so the picker shows every file.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Ещё")
        // The bank notifications section from the design arrives with that feature.
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Синхронизация")
            SyncCard(
                state = sync,
                now = rememberNow(sync),
                onOpen = onOpenSync,
                onPickFolder = { folderPicker.launch(null) },
            )
            Spacer(Modifier.height(8.dp))
            SectionTitle("Уведомления банков")
            WhiteCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenBanks).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = FinanceColors.Muted)
                    Column(Modifier.weight(1f)) {
                        Text("Операции из пушей", style = MaterialTheme.typography.bodyLarge)
                        Text("Сбер, Т-Банк, Райффайзен: карты и счета", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FinanceColors.Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionTitle("Справочники")
            WhiteCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenCategories).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Category, contentDescription = null, tint = FinanceColors.Muted)
                    Text("Категории", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FinanceColors.Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Данные",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = FinanceColors.Muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            WhiteCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = state != ImportState.Running) { picker.launch(arrayOf("*/*")) }
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = FinanceColors.Muted)
                    Column(Modifier.weight(1f)) {
                        Text("Импорт из Money Manager", style = MaterialTheme.typography.bodyLarge)
                        Text("Файл бэкапа .mmbak: счета, категории и все операции", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FinanceColors.Muted)
                }
            }
        }
    }

    when (val s = state) {
        ImportState.Idle -> Unit
        ImportState.Running -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Импорт") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Читаю бэкап…", Modifier.padding(start = 16.dp))
                }
            },
        )
        is ImportState.Done -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("Готово") } },
            title = { Text("Импорт завершён") },
            text = { ImportSummary(s.result) },
        )
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("OK") } },
            title = { Text("Не удалось импортировать") },
            text = { Text(s.message) },
        )
    }
}

@Composable
private fun ImportSummary(result: ImportResult) {
    val r = result.received
    val a = result.applied
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Line("Счета", a.accounts, r.accounts)
        Line("Группы счетов", a.groups, r.groups)
        Line("Категории", a.categories, r.categories)
        Line("Операции", a.transactions, r.transactions)
        Line("Валюты", a.currencies, r.currencies)
        Text(
            "Переводов собрано из пар: ${result.report.transfersMerged}. " +
                "Корректировок баланса: ${result.report.balanceAdjustments}.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (a != r) {
            Text(
                "Записано меньше, чем в файле: такие записи уже есть и не новее локальных.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (result.report.warningCount > 0) {
            Text(
                "Предупреждений: ${result.report.warningCount}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            result.report.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Line(label: String, applied: Int, received: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(if (applied == received) "$applied" else "$applied из $received")
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = FinanceColors.Muted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Not set up: explanation and "Выбрать папку". Set up: status, tap opens the sync screen. */
@Composable
private fun SyncCard(state: SyncUiState, now: Long, onOpen: () -> Unit, onPickFolder: () -> Unit) {
    WhiteCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (state.enabled) Modifier.clickable(onClick = onOpen) else Modifier)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(FinanceColors.PrimarySoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Sync, contentDescription = null, tint = FinanceColors.OnPrimarySoft, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Папка Syncthing", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        SyncTexts.cardSubtitle(state, now),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.error != null) FinanceColors.Expense else FinanceColors.Muted,
                    )
                }
                if (state.enabled) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FinanceColors.Muted)
                }
            }
            if (!state.enabled) {
                Text(
                    "Оба телефона пишут изменения в общую папку, каждый — в свой файл. Изменения второго телефона подтягиваются сами.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceColors.Muted,
                )
                Button(onClick = onPickFolder, colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)) {
                    Text("Выбрать папку", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
