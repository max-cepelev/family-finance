package com.familyfinance.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.sp
import com.familyfinance.ui.common.CategoryAvatar
import com.familyfinance.domain.model.BudgetState
import com.familyfinance.ui.budget.budgetColor
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

@OptIn(ExperimentalFoundationApi::class)
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
        // A tapped slice scrolls to its rows and tints them for a moment.
        val scope = rememberCoroutineScope()
        var highlighted by remember { mutableStateOf<Set<Int>>(emptySet()) }
        val requesters = remember(state.rows.size) { List(state.rows.size) { BringIntoViewRequester() } }
        LaunchedEffect(highlighted) {
            if (highlighted.isNotEmpty()) {
                delay(HIGHLIGHT_MS)
                highlighted = emptySet()
            }
        }
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
                    Donut(state, Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp, bottom = 8.dp)) { slice ->
                        // The last slice is "the rest" when there are more rows than slices: all of those rows.
                        val rest = state.rows.size > state.slices.size - 1 && slice == state.slices.size - 1 && state.rows.size != state.slices.size
                        val rows = if (rest) (slice until state.rows.size).toSet() else setOf(slice)
                        highlighted = rows
                        scope.launch { requesters.getOrNull(rows.min())?.bringIntoView() }
                    }
                    state.rows.forEachIndexed { i, row ->
                        StatRowItem(
                            row,
                            state.mainCurrency,
                            highlighted = i in highlighted,
                            modifier = Modifier.bringIntoViewRequester(requesters[i]),
                            onOpen = { row.categoryUid?.let(viewModel::open) },
                        )
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
                color = if (isSelected) FinanceColors.Primary else FinanceColors.Card,
                shadowElevation = if (isSelected) 0.dp else 1.dp,
                modifier = Modifier.weight(1f).height(34.dp).semantics { this.selected = isSelected; role = Role.Tab },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.White else FinanceColors.Ink,
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
private fun Donut(state: StatsUiState, modifier: Modifier, onSlice: (Int) -> Unit) {
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
    val ring = 220.dp
    val stroke = 34.dp
    Box(modifier.size(ring).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.size(ring).pointerInput(state.slices) {
                detectTapGestures { tap ->
                    val outer = size.width / 2f - 4.dp.toPx()
                    val inner = outer - stroke.toPx()
                    val dx = tap.x - size.width / 2f
                    val dy = tap.y - size.height / 2f
                    // Only the ring: a little slack around it, nothing in the hole.
                    val distance = hypot(dx, dy)
                    if (distance < inner - 8.dp.toPx() || distance > outer + 8.dp.toPx()) return@detectTapGestures
                    val angle = (Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f + 360f) % 360f
                    var end = 0f
                    val index = state.slices.indexOfFirst { slice -> end += slice.fraction * 360f; angle < end }
                    if (index >= 0) onSlice(index)
                }
            },
        ) {
            val width = stroke.toPx()
            val outer = size.width / 2 - 4.dp.toPx()
            val inner = outer - width
            val gap = if (state.slices.size > 1) 2f else 0f
            var start = -90f
            state.slices.forEach { slice ->
                val sweep = slice.fraction * 360f
                if (state.slices.size == 1) {
                    drawCircle(slice.color, radius = outer - width / 2, style = Stroke(width))
                } else if (sweep - gap > 0.2f) {
                    drawPath(roundedSector(center, outer, inner, start + gap / 2, sweep - gap, SliceCorner.toPx()), slice.color)
                }
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
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** Corner radius of a slice: softened ends, not half circles that would turn small slices into dots. */
private val SliceCorner = 10.dp

/**
 * A slice of the ring from [start] over [sweep] degrees with rounded corners; a thin slice gets smaller corners
 * so they still fit.
 */
private fun roundedSector(center: Offset, outer: Float, inner: Float, start: Float, sweep: Float, corner: Float): Path {
    val c = minOf(corner, (outer - inner) / 2, inner * Math.toRadians(sweep / 2.0).toFloat())
    fun at(r: Float, deg: Float): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
    }
    val end = start + sweep
    // The corners take this much of each arc.
    val outerTrim = Math.toDegrees((c / outer).toDouble()).toFloat()
    val innerTrim = Math.toDegrees((c / inner).toDouble()).toFloat()
    val outerBox = Rect(center.x - outer, center.y - outer, center.x + outer, center.y + outer)
    val innerBox = Rect(center.x - inner, center.y - inner, center.x + inner, center.y + inner)
    return Path().apply {
        at(outer, start + outerTrim).let { moveTo(it.x, it.y) }
        arcTo(outerBox, start + outerTrim, sweep - 2 * outerTrim, false)
        at(outer, end).let { k -> at(outer - c, end).let { quadraticTo(k.x, k.y, it.x, it.y) } }
        at(inner + c, end).let { lineTo(it.x, it.y) }
        at(inner, end).let { k -> at(inner, end - innerTrim).let { quadraticTo(k.x, k.y, it.x, it.y) } }
        arcTo(innerBox, end - innerTrim, -(sweep - 2 * innerTrim), false)
        at(inner, start).let { k -> at(inner + c, start).let { quadraticTo(k.x, k.y, it.x, it.y) } }
        at(outer - c, start).let { lineTo(it.x, it.y) }
        at(outer, start).let { k -> at(outer, start + outerTrim).let { quadraticTo(k.x, k.y, it.x, it.y) } }
        close()
    }
}

@Composable
private fun StatRowItem(row: StatRow, currency: String, highlighted: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val tint by animateColorAsState(if (highlighted) FinanceColors.PrimarySoft else Color.Transparent, tween(300), label = "highlight")
    Row(
        modifier.fillMaxWidth()
            .background(tint)
            .then(if (row.canOpen) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryAvatar(row.name, row.categoryUid, size = 36.dp, icon = row.icon, color = row.color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                ColoredAmount(MoneyFormatter.format(row.amount, currency), FinanceColors.Ink, style = MaterialTheme.typography.bodyLarge, weight = FontWeight.SemiBold)
            }
            val budget = row.budget
            // With a budget the bar is how much of it is spent, coloured by how close the limit is.
            val (fill, color) = if (budget != null) budget.share.coerceIn(0.0, 1.0).toFloat() to budgetColor(budget.level) else row.bar to row.color
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(FinanceColors.Track)) {
                Box(Modifier.fillMaxWidth(fill).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
            }
            when {
                budget == null -> Text(percent(row.share) + " " + RowNoun, style = MaterialTheme.typography.bodySmall.merge(TabularNumbers), color = FinanceColors.Muted)
                budget.level == BudgetState.Level.OVER -> Text(
                    "сверх бюджета ${MoneyFormatter.format(-budget.left, currency)}",
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                    fontWeight = FontWeight.SemiBold,
                    color = FinanceColors.Expense,
                )
                else -> Text(
                    "из ${MoneyFormatter.format(budget.budget.amount, currency)} · осталось ${MoneyFormatter.format(budget.left, currency)}",
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                    color = FinanceColors.Muted,
                )
            }
        }
        if (row.canOpen) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Подкатегории", tint = FinanceColors.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

private const val RowNoun = "от суммы"

/** How long a row stays tinted after its slice is tapped. */
private const val HIGHLIGHT_MS = 1500L

private fun percent(share: Float): String = String.format(Locale.forLanguageTag("ru-RU"), "%.1f%%", share * 100)
