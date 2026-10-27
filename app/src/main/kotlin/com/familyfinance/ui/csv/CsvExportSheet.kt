package com.familyfinance.ui.csv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.ui.common.BottomSheet
import com.familyfinance.ui.common.ChoiceDialog
import com.familyfinance.ui.common.DateDialog
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.SegmentedToggle
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private enum class ExportPick { MONTH, YEAR, FROM, TO, ACCOUNT }

private val PERIODS = listOf(ExportPeriod.MONTH, ExportPeriod.YEAR, ExportPeriod.ALL, ExportPeriod.CUSTOM)

/** "Экспорт в CSV": period and accounts, a sample of the file, then the system "save as". */
@Composable
fun CsvExportSheet(onDone: (String) -> Unit, onDismiss: () -> Unit, viewModel: CsvExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pick by remember { mutableStateOf<ExportPick?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.write(uri)
    }
    LaunchedEffect(state.message) {
        state.message?.let { viewModel.messageShown(); onDone(it) }
    }
    val c = state.choice

    BottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Экспорт в CSV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Период", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted, modifier = Modifier.padding(horizontal = 4.dp))
                SegmentedToggle(
                    options = listOf("Месяц", "Год", "Всё", "Свой"),
                    selected = PERIODS.indexOf(c.period),
                    onSelect = { viewModel.setPeriod(PERIODS[it]) },
                    selectedColor = { FinanceColors.OnPrimarySoft },
                )
            }
            WhiteCard(Modifier.fillMaxWidth()) {
                when (c.period) {
                    ExportPeriod.MONTH -> PickRow("Месяц", Dates.month(c.month)) { pick = ExportPick.MONTH }
                    ExportPeriod.YEAR -> PickRow("Год", c.year.toString()) { pick = ExportPick.YEAR }
                    ExportPeriod.ALL -> Unit
                    ExportPeriod.CUSTOM -> {
                        PickRow("С", Dates.relative(millis(c.from))) { pick = ExportPick.FROM }
                        HorizontalDivider(color = FinanceColors.Hairline)
                        PickRow("По", Dates.relative(millis(c.to))) { pick = ExportPick.TO }
                    }
                }
                if (c.period != ExportPeriod.ALL) HorizontalDivider(color = FinanceColors.Hairline)
                PickRow("Счета", state.accounts.firstOrNull { it.uid == c.accountUid }?.name ?: "Все счета") { pick = ExportPick.ACCOUNT }
            }
            if (state.sample.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FinanceColors.Card).padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    state.sample.forEach {
                        Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, color = FinanceColors.Ink)
                    }
                }
            }
            Text(
                "${Dates.operations(state.count)}. Файл в UTF-8, разделитель «;» — Excel откроет кириллицу без настроек. Куда сохранить, выберете дальше.",
                style = MaterialTheme.typography.bodySmall,
                color = FinanceColors.Muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Button(
                onClick = { saver.launch(viewModel.fileName()) },
                enabled = state.count > 0,
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Сохранить файл…", fontWeight = FontWeight.Medium) }
        }
    }

    when (pick) {
        ExportPick.MONTH -> ChoiceDialog(
            title = "Месяц",
            items = (0 until 36).map { YearMonth.now().minusMonths(it.toLong()) }.map { it.toString() to Dates.month(it) },
            selected = c.month.toString(),
            onPick = { pick = null; viewModel.setMonth(YearMonth.parse(it)) },
            onDismiss = { pick = null },
        )
        ExportPick.YEAR -> ChoiceDialog(
            title = "Год",
            items = (LocalDate.now().year downTo minOf(state.firstYear, LocalDate.now().year)).map { it.toString() to it.toString() },
            selected = c.year.toString(),
            onPick = { pick = null; viewModel.setYear(it.toInt()) },
            onDismiss = { pick = null },
        )
        ExportPick.FROM -> DateDialog(initial = millis(c.from), onDismiss = { pick = null }, onPick = { pick = null; viewModel.setFrom(it) })
        ExportPick.TO -> DateDialog(initial = millis(c.to), onDismiss = { pick = null }, onPick = { pick = null; viewModel.setTo(it) })
        ExportPick.ACCOUNT -> ChoiceDialog(
            title = "Счета",
            items = listOf("" to "Все счета") + state.accounts.map { it.uid to it.name },
            selected = c.accountUid ?: "",
            onPick = { pick = null; viewModel.setAccount(it.ifEmpty { null }) },
            onDismiss = { pick = null },
        )
        null -> Unit
    }
}

private fun millis(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
private fun PickRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = FinanceColors.Muted)
    }
}
