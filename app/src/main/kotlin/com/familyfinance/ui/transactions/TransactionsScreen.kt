package com.familyfinance.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.model.Totals
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.ui.common.Amount
import com.familyfinance.ui.common.CategoryAvatar
import com.familyfinance.ui.common.ColoredAmount
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.MonthHeader
import com.familyfinance.ui.common.TypedAmount
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors

@Composable
fun TransactionsScreen(
    onAdd: () -> Unit,
    onOpen: (uid: String) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusDay by viewModel.focusDay.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Opened from the calendar: scroll to that day once the month is loaded.
    LaunchedEffect(focusDay, state.days) {
        val day = focusDay ?: return@LaunchedEffect
        if (state.loading || state.month.monthValue != day.monthValue || state.month.year != day.year) return@LaunchedEffect
        val index = state.days.indexOfFirst { it.date == day }
        if (index >= 0) listState.scrollToItem(index + 1)
        viewModel.focusConsumed()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            MonthHeader(state.month, viewModel::showMonth)
            if (!state.loading && state.days.isEmpty()) {
                MonthSummary(state.totals, state.mainCurrency, Modifier.padding(horizontal = 16.dp))
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет операций за этот месяц", style = MaterialTheme.typography.bodyLarge, color = FinanceColors.Muted)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "summary") { MonthSummary(state.totals, state.mainCurrency) }
                    items(state.days, key = { "d:" + it.date }) { day -> DayCard(day, state.mainCurrency, onOpen) }
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            shape = RoundedCornerShape(20.dp),
            containerColor = FinanceColors.Primary,
            contentColor = FinanceColors.Card,
            elevation = FloatingActionButtonDefaults.elevation(6.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Добавить операцию") }
    }
}

@Composable
private fun MonthSummary(totals: Totals, currency: String, modifier: Modifier = Modifier) {
    WhiteCard(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCell("Доходы", Modifier.weight(1f), Alignment.Start) {
                ColoredAmount(MoneyFormatter.format(totals.income, currency), FinanceColors.Income, weight = FontWeight.Medium)
            }
            SummaryCell("Расходы", Modifier.weight(1f), Alignment.Start) {
                ColoredAmount(MoneyFormatter.format(totals.expense, currency), FinanceColors.Expense, weight = FontWeight.Medium)
            }
            SummaryCell("Итого", Modifier.weight(1f), Alignment.End) {
                Amount(totals.net, currency, weight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, explicitPlus = true)
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, modifier: Modifier, align: Alignment.Horizontal, value: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FinanceColors.Muted)
        value()
    }
}

@Composable
private fun DayCard(day: DayUi, currency: String, onOpen: (String) -> Unit) {
    WhiteCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${day.date.dayOfMonth}", fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(Dates.weekday(day.date), style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted, modifier = Modifier.weight(1f))
            if (day.totals.income != 0L) ColoredAmount(MoneyFormatter.format(day.totals.income, currency), FinanceColors.Income)
            if (day.totals.expense != 0L) ColoredAmount(MoneyFormatter.format(day.totals.expense, currency), FinanceColors.Expense)
        }
        HorizontalDivider(color = FinanceColors.Hairline)
        day.rows.forEach { row -> TransactionRow(row, onClick = { onOpen(row.uid) }) }
    }
}

@Composable
private fun TransactionRow(row: TransactionRowUi, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (row.type == TransactionType.TRANSFER) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(FinanceColors.Track), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = FinanceColors.Muted, modifier = Modifier.size(20.dp))
            }
        } else {
            CategoryAvatar(row.avatarName, row.avatarKey, icon = row.avatarIcon)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TypedAmount(row.amount, row.currencyCode, row.type)
    }
}
