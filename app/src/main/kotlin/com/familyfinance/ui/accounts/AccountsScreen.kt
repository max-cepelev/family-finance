package com.familyfinance.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.ui.common.Amount
import com.familyfinance.ui.common.CardShape
import com.familyfinance.ui.common.ColoredAmount
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.ScreenTitle
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors
import kotlin.math.abs

@Composable
fun AccountsScreen(viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Счета")
        if (!state.loading && state.groups.isEmpty()) {
            Text(
                "Счетов пока нет. Импортируйте бэкап Money Manager во вкладке «Ещё».",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = FinanceColors.Muted,
            )
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") { Summary(state) }
            items(state.groups, key = { "g:" + it.name }) { group ->
                val isOpen = group.name !in collapsed
                WhiteCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(role = Role.Button) { collapsed = if (isOpen) collapsed + group.name else collapsed - group.name }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(group.name, color = FinanceColors.Muted, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Amount(group.total, state.mainCurrency, weight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                        Icon(
                            if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isOpen) "Свернуть" else "Развернуть",
                            tint = FinanceColors.Muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (isOpen) {
                        HorizontalDivider(color = FinanceColors.Hairline)
                        group.accounts.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 6.dp)
                                    .alpha(if (item.account.hidden) 0.55f else 1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.account.name, style = MaterialTheme.typography.bodyLarge)
                                    if (item.account.hidden) Text("скрыт", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
                                }
                                Amount(item.balance, item.account.currencyCode)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(state: AccountsUiState) {
    Surface(shape = CardShape, color = FinanceColors.Primary, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Итого", style = MaterialTheme.typography.bodySmall, color = FinanceColors.OnPrimaryMuted)
                ColoredAmount(
                    MoneyFormatter.format(state.total, state.mainCurrency),
                    FinanceColors.Card,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                    weight = FontWeight.Medium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryValue("Активы", state.assets, state.mainCurrency, Modifier.weight(1f))
                SummaryValue("Долги", abs(state.liabilities), state.mainCurrency, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: Long, currency: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FinanceColors.OnPrimaryMuted)
        ColoredAmount(MoneyFormatter.format(value, currency), FinanceColors.Card, style = MaterialTheme.typography.bodyLarge)
    }
}
