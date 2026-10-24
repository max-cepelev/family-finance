package com.familyfinance.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.ui.common.ColoredAmount
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.MonthHeader
import com.familyfinance.ui.common.TabularNumbers
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun CalendarScreen(
    onOpenDay: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        MonthHeader(state.month, viewModel::showMonth)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            ) {
                ColoredAmount("+" + MoneyFormatter.format(state.totals.income, state.mainCurrency), FinanceColors.Income)
                ColoredAmount("−" + MoneyFormatter.format(state.totals.expense, state.mainCurrency), FinanceColors.Expense)
            }

            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp)) {
                    WeekdayHeader()
                    MonthGrid(state, onSelect = viewModel::select)
                }
            }

            val selected = state.selected
            if (selected != null) {
                Spacer(Modifier.height(12.dp))
                SelectedDayCard(selected, state.days[selected], state.mainCurrency) {
                    viewModel.openDay(selected)
                    onOpenDay()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        DayOfWeek.entries.forEach { day ->
            Text(
                day.getDisplayName(TextStyle.SHORT_STANDALONE, Dates.locale).replaceFirstChar { it.titlecase(Dates.locale) },
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = if (day.value >= 6) FinanceColors.Weekend else FinanceColors.Muted,
            )
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val first = state.month.atDay(1)
    // Weeks start on Monday: blanks before the 1st.
    val leading = first.dayOfWeek.value - 1
    val cells = leading + state.month.lengthOfMonth()
    val weeks = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(weeks) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { column ->
                    val dayNumber = week * 7 + column - leading + 1
                    if (dayNumber < 1 || dayNumber > state.month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).height(76.dp))
                    } else {
                        val date = state.month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            day = state.days[date],
                            selected = date == state.selected,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, day: CalendarDay?, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val income = day?.totals?.income ?: 0L
    val expense = day?.totals?.expense ?: 0L
    val description = buildString {
        append(Dates.dayWithWeekday(date))
        if (income != 0L) append(", доход ${MoneyFormatter.compact(income)}")
        if (expense != 0L) append(", расход ${MoneyFormatter.compact(expense)}")
    }
    Column(
        modifier
            .height(76.dp)
            .clip(shape)
            .background(if (selected) FinanceColors.PrimarySoft else Color.Transparent)
            .border(BorderStroke(2.dp, if (selected) FinanceColors.Primary else Color.Transparent), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            "${date.dayOfMonth}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (date.dayOfWeek.value >= 6) FinanceColors.Weekend else FinanceColors.Ink,
        )
        val small = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp).merge(TabularNumbers)
        if (income != 0L) Text("+" + MoneyFormatter.compact(income), style = small, color = FinanceColors.Income, maxLines = 1, softWrap = false)
        if (expense != 0L) Text("−" + MoneyFormatter.compact(expense), style = small, color = FinanceColors.Expense, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SelectedDayCard(date: LocalDate, day: CalendarDay?, currency: String, onOpen: () -> Unit) {
    WhiteCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Dates.dayWithWeekday(date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(Dates.operations(day?.count ?: 0), style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
            }
            if (day == null) {
                Text("Операций нет", style = MaterialTheme.typography.bodyMedium, color = FinanceColors.Muted)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    if (day.totals.income != 0L) ColoredAmount("+" + MoneyFormatter.format(day.totals.income, currency), FinanceColors.Income, style = MaterialTheme.typography.bodyLarge)
                    if (day.totals.expense != 0L) ColoredAmount("−" + MoneyFormatter.format(day.totals.expense, currency), FinanceColors.Expense, style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = onOpen, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Открыть день", color = FinanceColors.Primary, fontWeight = FontWeight.Medium)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FinanceColors.Primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
