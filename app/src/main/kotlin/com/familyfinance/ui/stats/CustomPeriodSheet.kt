package com.familyfinance.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.familyfinance.ui.common.BottomSheet
import com.familyfinance.ui.common.ChoiceChip
import com.familyfinance.ui.common.ColoredAmount
import com.familyfinance.ui.common.DateDialog
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors
import java.time.LocalDate
import java.time.ZoneId

/** "Свой период": quick presets, the two days, a preview of the totals, then "Показать". */
@Composable
fun CustomPeriodSheet(
    draft: CustomPeriodDraft,
    currency: String,
    onPreset: (CustomPreset) -> Unit,
    onFrom: (LocalDate) -> Unit,
    onTo: (LocalDate) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: "С", false: "По"
    val period = draft.period

    BottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Свой период", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))

            // Two per row instead of a FlowRow: see CategorySheet.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomPreset.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset -> ChoiceChip(preset.label, selected = false) { onPreset(preset) } }
                    }
                }
            }

            WhiteCard(Modifier.fillMaxWidth()) {
                DayRow("С", period.from) { picking = true }
                HorizontalDivider(color = FinanceColors.Hairline)
                DayRow("По", period.to) { picking = false }
            }

            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(Dates.plural(period.days.toInt(), "день", "дня", "дней"), style = MaterialTheme.typography.bodyMedium, color = FinanceColors.Muted)
                draft.totals?.let { totals ->
                    ColoredAmount("+" + MoneyFormatter.format(totals.income, currency), FinanceColors.Income)
                    ColoredAmount("−" + MoneyFormatter.format(totals.expense, currency), FinanceColors.Expense)
                }
            }

            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Показать", fontWeight = FontWeight.Medium) }
        }
    }

    picking?.let { from ->
        val zone = ZoneId.systemDefault()
        val initial = if (from) period.from else period.to
        DateDialog(
            initial = initial.atStartOfDay(zone).toInstant().toEpochMilli(),
            onDismiss = { picking = null },
            onPick = { day ->
                picking = null
                if (from) onFrom(day) else onTo(day)
            },
        )
    }
}

@Composable
private fun DayRow(label: String, day: LocalDate, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label ${Dates.dayMonthYear(day)}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = FinanceColors.Muted, modifier = Modifier.size(22.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
            Text(Dates.dayMonthYear(day), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
