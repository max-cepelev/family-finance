package com.familyfinance.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyfinance.ui.theme.FinanceColors
import java.time.YearMonth
import kotlin.math.abs

val CardShape = RoundedCornerShape(20.dp)

/** White rounded card on the ground color. */
@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = CardShape, color = FinanceColors.Card) {
        Column(content = content)
    }
}

/** Screen title row for tabs without a month switcher. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        actions()
    }
}

/** "‹ Февраль 2021 ›"; tapping the title opens a month picker. */
@Composable
fun MonthHeader(month: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    PeriodHeader(
        title = Dates.month(month),
        onPrevious = { onMonthChange(month.minusMonths(1)) },
        onNext = { onMonthChange(month.plusMonths(1)) },
        onTitleClick = { picking = true },
        previousLabel = "Предыдущий месяц",
        nextLabel = "Следующий месяц",
    )
    if (picking) {
        MonthPickerDialog(month, onDismiss = { picking = false }, onPick = { picking = false; onMonthChange(it) })
    }
}

/** Title with arrows; without [onPrevious]/[onNext] the arrows are left out (a custom period has no neighbours). */
@Composable
fun PeriodHeader(
    title: String,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onTitleClick: (() -> Unit)?,
    previousLabel: String = "Предыдущий период",
    nextLabel: String = "Следующий период",
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp)) {
            if (onPrevious != null) {
                IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = previousLabel) }
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .then(if (onTitleClick != null) Modifier.clickable(role = Role.Button, onClick = onTitleClick) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Box(Modifier.size(48.dp)) {
            if (onNext != null) {
                IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = nextLabel) }
            }
        }
    }
}

@Composable
private fun MonthPickerDialog(initial: YearMonth, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit) {
    var year by remember { mutableIntStateOf(initial.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = FinanceColors.Card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущий год") }
                Text("$year", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий год") }
            }
        },
        text = {
            LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                items((1..12).toList()) { m ->
                    FilterChip(
                        selected = initial.year == year && initial.monthValue == m,
                        onClick = { onPick(YearMonth.of(year, m)) },
                        label = { Text(Dates.monthName(m)) },
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        },
    )
}

/** Grey track with a white pill on the selected option. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: (Int) -> Color = { FinanceColors.Ink },
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FinanceColors.Track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(if (isSelected) Modifier.shadow(1.dp, RoundedCornerShape(12.dp)) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) FinanceColors.Card else Color.Transparent)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSelected) selectedColor(index) else FinanceColors.Muted,
                    fontWeight = if (isSelected) FontWeight.Medium else null,
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** Tint and ink pairs for category avatars; a category keeps its pair. */
object CategoryPalette {
    private val pairs = listOf(
        Color(0xFFDCEBE2) to Color(0xFF1F4D38),
        Color(0xFFF6E1EA) to Color(0xFF8A2E55),
        Color(0xFFDDE7F6) to Color(0xFF1F4C8F),
        Color(0xFFD9F0EE) to Color(0xFF1C5E58),
        Color(0xFFF6ECD5) to Color(0xFF7A5512),
        Color(0xFFFBE3D3) to Color(0xFF8A4316),
        Color(0xFFE8E1F5) to Color(0xFF4F3787),
    )
    private val none = Color(0xFFE9ECE6) to Color(0xFF4A514B)

    fun of(uid: String?): Pair<Color, Color> = if (uid == null) none else pairs[abs(uid.hashCode() % pairs.size)]

    /** Donut colors by rank: the five largest categories, then grey for the rest. */
    private val ranked = listOf(
        Color(0xFF2E6B4F), Color(0xFFE07A3F), Color(0xFF3D6FB6), Color(0xFF7FB99A), Color(0xFFD9A441),
    )
    val Rest = Color(0xFFB8BDB5)
    const val RANKED = 5

    fun rank(index: Int): Color = ranked.getOrElse(index) { Rest }
}

/** Round avatar: the category's emoji, or the first letter of its name on the category's tint. */
@Composable
fun CategoryAvatar(name: String?, colorKey: String?, size: Dp = 36.dp, icon: String? = null) {
    val (tint, ink) = CategoryPalette.of(colorKey)
    Box(
        Modifier.size(size).clip(CircleShape).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Text(icon, fontSize = (size.value * 0.55f).sp)
        } else {
            Text(
                name?.firstOrNull()?.uppercase() ?: "•",
                color = ink,
                fontWeight = FontWeight.Medium,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

/** Outlined pill used for quick picks. */
@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) FinanceColors.PrimarySoft else FinanceColors.Card,
        border = BorderStroke(1.dp, if (selected) FinanceColors.Primary else FinanceColors.ChipBorder),
        modifier = Modifier.height(36.dp).semantics { this.selected = selected },
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) FinanceColors.OnPrimarySoft else FinanceColors.Ink,
                fontWeight = if (selected) FontWeight.Medium else null,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
