package com.familyfinance.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.model.Period
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.ui.common.ColoredAmount
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.MonthHeader
import com.familyfinance.ui.common.PeriodHeader
import com.familyfinance.ui.common.TabularNumbers
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors
import java.util.Locale

private val TYPES = listOf(TransactionType.INCOME, TransactionType.EXPENSE)

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val customDraft by viewModel.customDraft.collectAsStateWithLifecycle()
    BackHandler(enabled = state.parent != null, onBack = viewModel::back)

    Column(Modifier.fillMaxSize()) {
        when (val period = state.period) {
            is Period.Month -> MonthHeader(period.month, viewModel::showMonth)
            else -> PeriodHeader(
                title = Dates.period(period),
                onPrevious = if (period is Period.Custom) null else viewModel::previous,
                onNext = if (period is Period.Custom) null else viewModel::next,
                onTitleClick = if (period is Period.Custom) viewModel::openCustom else null,
            )
        }
        PeriodKindChips(state.kind, viewModel::showKind, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp))
        TotalsToggle(state, onSelect = viewModel::showType, modifier = Modifier.padding(horizontal = 16.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            WhiteCard(Modifier.fillMaxWidth()) {
                val parent = state.parent
                if (parent != null) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Ко всем категориям") }
                        Text(parent.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    }
                }
                if (state.rows.isEmpty()) {
                    if (!state.loading) {
                        Text(
                            if (state.type == TransactionType.INCOME) "Доходов за месяц нет" else "Расходов за месяц нет",
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center,
                            color = FinanceColors.Muted,
                        )
                    }
                } else {
                    Donut(state, Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp, bottom = 8.dp))
                    state.rows.forEach { row ->
                        StatRowItem(row, state.mainCurrency, onOpen = { row.categoryUid?.let(viewModel::open) })
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    customDraft?.let { draft ->
        CustomPeriodSheet(
            draft = draft,
            currency = state.mainCurrency,
            onPreset = viewModel::applyPreset,
            onFrom = viewModel::setCustomFrom,
            onTo = viewModel::setCustomTo,
            onConfirm = viewModel::confirmCustom,
            onDismiss = viewModel::dismissCustom,
        )
    }
}

private val KINDS = listOf(PeriodKind.WEEK to "Неделя", PeriodKind.MONTH to "Месяц", PeriodKind.YEAR to "Год", PeriodKind.CUSTOM to "Период")

@Composable
private fun PeriodKindChips(selected: PeriodKind, onSelect: (PeriodKind) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KINDS.forEach { (kind, label) ->
            val isSelected = kind == selected
            Surface(
                onClick = { onSelect(kind) },
                shape = RoundedCornerShape(17.dp),
                color = if (isSelected) FinanceColors.PrimarySoft else FinanceColors.Card,
                border = BorderStroke(1.dp, if (isSelected) FinanceColors.Primary else FinanceColors.ChipBorder),
                modifier = Modifier.weight(1f).height(34.dp).semantics { this.selected = isSelected; role = Role.Tab },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSelected) FontWeight.Medium else null,
                        color = if (isSelected) FinanceColors.OnPrimarySoft else FinanceColors.Ink,
                    )
                }
            }
        }
    }
}

/** Income/expense switch with the period totals on a second line: a year's sums do not fit beside the label. */
@Composable
private fun TotalsToggle(state: StatsUiState, onSelect: (TransactionType) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FinanceColors.Track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TYPES.forEach { type ->
            val isSelected = type == state.type
            val color = if (type == TransactionType.INCOME) FinanceColors.Income else FinanceColors.Expense
            val amount = if (type == TransactionType.INCOME) state.periodTotals.income else state.periodTotals.expense
            Column(
                Modifier.weight(1f).height(52.dp)
                    .then(if (isSelected) Modifier.shadow(1.dp, RoundedCornerShape(12.dp)) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) FinanceColors.Card else Color.Transparent)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab) { onSelect(type) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (type == TransactionType.INCOME) "Доходы" else "Расходы",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Medium else null,
                    color = if (isSelected) color else FinanceColors.Muted,
                )
                Text(
                    MoneyFormatter.format(amount, state.mainCurrency),
                    style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
                    fontWeight = if (isSelected) FontWeight.Medium else null,
                    color = if (isSelected) color else FinanceColors.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Donut(state: StatsUiState, modifier: Modifier) {
    val suffix = when (state.kind) {
        PeriodKind.WEEK -> " за неделю"
        PeriodKind.MONTH -> ""
        PeriodKind.YEAR -> " за год"
        PeriodKind.CUSTOM -> " за период"
    }
    val label = (if (state.type == TransactionType.INCOME) "доходы" else "расходы") + suffix
    val description = state.rows.take(6).joinToString(prefix = "${label.replaceFirstChar { it.uppercase() }} по категориям: ") {
        "${it.name} ${percent(it.share)}"
    }
    Box(modifier.size(184.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(184.dp)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2 + 10.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val gap = if (state.slices.size > 1) 1.2f else 0f
            var start = -90f
            state.slices.forEach { slice ->
                val sweep = slice.fraction * 360f
                drawArc(
                    color = slice.color,
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // "расходы" and "за период" on separate lines: together they touch the ring.
            Text(
                label.replaceFirst(" ", "\n"),
                style = MaterialTheme.typography.bodySmall,
                color = FinanceColors.Muted,
                textAlign = TextAlign.Center,
            )
            Text(
                MoneyFormatter.format(state.total, state.mainCurrency),
                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun StatRowItem(row: StatRow, currency: String, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .then(if (row.canOpen) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(row.color))
            Text(row.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                percent(row.share),
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                color = FinanceColors.Muted,
                textAlign = TextAlign.End,
                modifier = Modifier.width(48.dp),
            )
            ColoredAmount(MoneyFormatter.format(row.amount, currency), FinanceColors.Ink, style = MaterialTheme.typography.bodyLarge)
            if (row.canOpen) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Подкатегории", tint = FinanceColors.Muted, modifier = Modifier.size(18.dp))
            }
        }
        Box(Modifier.padding(start = 20.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(FinanceColors.Track)) {
            Box(Modifier.fillMaxWidth(row.bar).height(4.dp).clip(RoundedCornerShape(2.dp)).background(row.color))
        }
    }
}

private fun percent(share: Float): String = String.format(Locale.forLanguageTag("ru-RU"), "%.1f%%", share * 100)
