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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
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

val CardShape = RoundedCornerShape(24.dp)

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
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
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
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (onPrevious != null) RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, previousLabel, onPrevious)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .then(if (onTitleClick != null) Modifier.clickable(role = Role.Button, onClick = onTitleClick) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (onNext != null) RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, nextLabel, onNext)
        }
    }
}

/** White circle with an icon, 40dp drawn in a 48dp touch target. */
@Composable
fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit, size: Dp = 40.dp) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = FinanceColors.Card,
        contentColor = FinanceColors.Ink,
        shadowElevation = 1.dp,
        modifier = Modifier.size(size).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) }
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
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(FinanceColors.Track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(if (isSelected) Modifier.shadow(1.dp, RoundedCornerShape(16.dp)) else Modifier)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) FinanceColors.Card else Color.Transparent)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSelected) selectedColor(index) else FinanceColors.Muted,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** Colour of every category by uid ([com.familyfinance.domain.model.CategoryColors]), provided at the app's root. */
val LocalCategoryColors = staticCompositionLocalOf<Map<String, Color>> { emptyMap() }

/** Parses "#22A447"; grey for anything else. */
fun categoryColor(hex: String?): Color =
    hex?.removePrefix("#")?.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: CategoryColorNone

private val CategoryColorNone = Color(0xFF848A99)

/** Slice colour for "Остальное" in statistics. */
val RestColor = Color(0xFFC3C7D1)

/**
 * A category's tile: its emoji on a light tint of its colour, or the first letter of its name on the colour.
 * [colorKey] is the category's uid; [color] overrides it (a colour being picked).
 */
@Composable
fun CategoryAvatar(name: String?, colorKey: String?, size: Dp = 36.dp, icon: String? = null, color: Color? = null) {
    val base = color ?: colorKey?.let { LocalCategoryColors.current[it] } ?: CategoryColorNone
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(if (icon != null) base.copy(alpha = 0.15f) else base),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Text(icon, fontSize = (size.value * 0.5f).sp)
        } else {
            Text(
                name?.firstOrNull()?.uppercase() ?: "•",
                color = Color.White,
                fontWeight = FontWeight.Bold,
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
