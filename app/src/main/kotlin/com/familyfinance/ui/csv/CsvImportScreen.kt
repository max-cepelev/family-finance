package com.familyfinance.ui.csv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.ui.banks.genitive
import com.familyfinance.ui.common.CategoryNames
import com.familyfinance.ui.common.ChoiceDialog
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.TabularNumbers
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.common.typeColor
import com.familyfinance.ui.theme.FinanceColors
import java.time.format.DateTimeFormatter

private val NoCategoryBg = Color(0xFFFBF1DC)
private val NoCategoryInk = Color(0xFF6A4A0C)
private val TIME = DateTimeFormatter.ofPattern("H:mm")

/** Enough to check the file; the counts above cover all of it. */
private const val PREVIEW_LINES = 150

/** Preview of a CSV file before anything is written: a bank statement, or a table in the app's format. */
@Composable
fun CsvImportScreen(onClose: () -> Unit, viewModel: CsvImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text(
                if (state is CsvImportState.Bank) "Импорт выписки" else "Импорт CSV",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        when (val s = state) {
            CsvImportState.Loading, CsvImportState.Importing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is CsvImportState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            is CsvImportState.Bank -> BankPreview(s.ui, viewModel)
            is CsvImportState.Own -> OwnPreview(s.ui, viewModel)
            is CsvImportState.Done -> Unit
        }
    }
    (state as? CsvImportState.Done)?.let { done ->
        AlertDialog(
            onDismissRequest = onClose,
            confirmButton = { TextButton(onClick = onClose) { Text("Готово") } },
            title = { Text("Импорт завершён") },
            text = {
                Text(
                    "Добавлено: ${Dates.operations(done.added)}." +
                        if (done.skipped > 0) " Пропущено: ${done.skipped} — уже были или не прочитались." else "",
                )
            },
        )
    }
}

@Composable
private fun BankPreview(ui: StatementUi, viewModel: CsvImportViewModel) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Field("Файл", "Выписка ${ui.statement.bank.genitive()} · ${ui.period}")
                    HorizontalDivider(color = FinanceColors.Hairline)
                    Row(
                        Modifier.fillMaxWidth().clickable { picking = true }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Записать на счёт", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
                            Text(ui.accountName, style = MaterialTheme.typography.bodyLarge, color = if (ui.accountUid == null) FinanceColors.Expense else FinanceColors.Ink)
                            Text(ui.accountNote, style = MaterialTheme.typography.bodySmall, color = if (ui.currencyMismatch) FinanceColors.Expense else FinanceColors.Muted)
                        }
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = FinanceColors.Muted)
                    }
                }
            }
            if (ui.checking) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            if (ui.accountUid != null && ui.lines.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat(ui.lines.count { it.state == LineState.NEW } + ui.lines.count { it.state == LineState.NO_CATEGORY }, "новых", FinanceColors.PrimarySoft, FinanceColors.OnPrimarySoft, Modifier.weight(1f))
                        Stat(ui.lines.count { it.state == LineState.KNOWN }, "уже есть", FinanceColors.Card, FinanceColors.Ink, Modifier.weight(1f))
                        Stat(ui.lines.count { it.state == LineState.NO_CATEGORY }, "без категории", NoCategoryBg, NoCategoryInk, Modifier.weight(1f))
                    }
                }
                item {
                    Text(
                        "«Уже есть» — совпали по сумме и времени с операциями из пушей или с прошлым импортом этой выписки, их не добавим. Без категории попадут в «Разобрать».",
                        style = MaterialTheme.typography.bodySmall,
                        color = FinanceColors.Muted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                item {
                    WhiteCard(Modifier.fillMaxWidth()) {
                        ui.lines.take(PREVIEW_LINES).forEachIndexed { i, line ->
                            if (i > 0) HorizontalDivider(color = FinanceColors.Hairline)
                            LineRow(line)
                        }
                    }
                }
                if (ui.lines.size > PREVIEW_LINES) {
                    item {
                        Text(
                            "…и ещё ${Dates.operations(ui.lines.size - PREVIEW_LINES)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FinanceColors.Muted,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
        Button(
            onClick = viewModel::import,
            enabled = ui.accountUid != null && !ui.checking && !ui.currencyMismatch && ui.newCount > 0,
            colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        ) {
            Text(
                when {
                    ui.accountUid == null || ui.currencyMismatch -> "Выберите счёт"
                    ui.checking -> "Проверяю операции…"
                    ui.newCount > 0 -> "Импортировать ${Dates.operations(ui.newCount)}"
                    else -> "Новых операций нет"
                },
                fontWeight = FontWeight.Medium,
            )
        }
    }
    if (picking) {
        ChoiceDialog(
            title = "Счёт",
            items = viewModel.accounts.map { it.uid to it.name },
            selected = ui.accountUid,
            onPick = { picking = false; viewModel.pickAccount(it) },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Stat(value: Int, label: String, bg: Color, ink: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, color = ink)
        Text(label, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
    }
}

@Composable
private fun LineRow(line: StatementLine) {
    val (tagBg, tagInk, tag) = when (line.state) {
        LineState.NEW -> Triple(FinanceColors.PrimarySoft, FinanceColors.OnPrimarySoft, "новая")
        LineState.KNOWN -> Triple(FinanceColors.Track, FinanceColors.Muted, "уже есть")
        LineState.NO_CATEGORY -> Triple(NoCategoryBg, NoCategoryInk, "без категории")
    }
    val when_ = "${Dates.dayMonthShort(line.row.dateTime.toLocalDate())}, ${TIME.format(line.row.dateTime)}"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp).alpha(if (line.state == LineState.KNOWN) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(line.row.payee.ifEmpty { "Без описания" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = tagInk,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(tagBg).padding(horizontal = 8.dp, vertical = 1.dp),
                )
                Text(
                    listOf(line.detail, when_).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val sign = if (line.row.type == TransactionType.INCOME) "+ " else ""
        Text(
            sign + MoneyFormatter.format(line.row.amount, line.row.currency),
            style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
            color = typeColor(line.row.type),
        )
    }
}

@Composable
private fun OwnPreview(ui: OwnUi, viewModel: CsvImportViewModel) {
    var accountPick by remember { mutableStateOf<NameMapping?>(null) }
    var categoryPick by remember { mutableStateOf<NameMapping?>(null) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Field("Файл в формате приложения", "${ui.fileName} · ${Dates.operations(ui.operations.size)}, ${ui.period}")
                }
            }
            item { Section("Счета из файла") }
            item { MappingCard(ui.accounts) { accountPick = it } }
            if (ui.categories.isNotEmpty()) {
                item { Section("Категории из файла") }
                item { MappingCard(ui.categories) { categoryPick = it } }
            }
            if (ui.errors.isNotEmpty()) {
                item { Section("Не прочитаны") }
                items(ui.errors) { e ->
                    Text("Строка ${e.line}: ${e.reason}", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Expense, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            item {
                Text(
                    "Совпавшие названия подставлены сами. Строки, которые не удалось прочитать, пропустим, остальные импортируем.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FinanceColors.Muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        Button(
            onClick = viewModel::import,
            colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        ) { Text("Импортировать ${Dates.operations(ui.operations.size)}", fontWeight = FontWeight.Medium) }
    }
    accountPick?.let { m ->
        ChoiceDialog(
            title = "Счёт для «${m.source}»",
            items = listOf("" to "Создать «${m.source}»") + viewModel.accounts.map { it.uid to it.name },
            selected = m.target.uid ?: "",
            onPick = { accountPick = null; viewModel.mapAccount(m.key, it.ifEmpty { null }) },
            onDismiss = { accountPick = null },
        )
    }
    categoryPick?.let { m ->
        val names = CategoryNames(viewModel.categories)
        ChoiceDialog(
            title = "Категория для «${m.source}»",
            items = listOf("" to "Создать «${m.source}»") +
                viewModel.categories.filter { it.type == m.type }.map { it.uid to names.label(it.uid).orEmpty() }.sortedBy { it.second },
            selected = m.target.uid ?: "",
            onPick = { categoryPick = null; viewModel.mapCategory(m.key, it.ifEmpty { null }) },
            onDismiss = { categoryPick = null },
        )
    }
}

@Composable
private fun MappingCard(mappings: List<NameMapping>, onPick: (NameMapping) -> Unit) {
    WhiteCard(Modifier.fillMaxWidth()) {
        mappings.forEachIndexed { i, m ->
            if (i > 0) HorizontalDivider(color = FinanceColors.Hairline)
            Row(
                Modifier.fillMaxWidth().clickable { onPick(m) }.heightIn(min = 52.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(m.source, style = MaterialTheme.typography.bodyLarge, color = FinanceColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = FinanceColors.ChipBorder, modifier = Modifier.size(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (m.target.isNew) {
                        Text("+ Создать «${m.target.label}»", style = MaterialTheme.typography.bodyLarge, color = FinanceColors.Primary, fontWeight = FontWeight.Medium)
                    } else {
                        Text(m.target.label, style = MaterialTheme.typography.bodyLarge)
                    }
                    m.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted) }
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = FinanceColors.Muted)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Section(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = FinanceColors.Muted, modifier = Modifier.padding(horizontal = 4.dp))
