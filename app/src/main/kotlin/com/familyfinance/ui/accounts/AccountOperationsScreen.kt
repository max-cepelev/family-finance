package com.familyfinance.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.model.AccountWithBalance
import com.familyfinance.domain.model.CreditCard
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.RoundIconButton
import com.familyfinance.ui.common.TabularNumbers
import com.familyfinance.ui.theme.FinanceColors
import com.familyfinance.ui.transactions.DayCard

/** One account: a card with its balance, then its operations of the shown month. */
@Composable
fun AccountOperationsScreen(
    onClose: () -> Unit,
    onEditAccount: (String) -> Unit,
    onOpenOperation: (String) -> Unit,
    onAddOperation: (accountUid: String) -> Unit,
    viewModel: AccountOperationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Deleted from its edit screen, or on the other phone: nothing left to show.
    LaunchedEffect(state.loading, state.account) { if (!state.loading && state.account == null) onClose() }
    val account = state.account ?: return

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", onClose)
                Text(
                    account.account.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(Icons.Outlined.Edit, "Изменить счёт", { onEditAccount(viewModel.uid) })
            }
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "card") { BalanceCard(account, state.groupName) }
                item(key = "month") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущий месяц", { viewModel.showMonth(state.month.minusMonths(1)) }, size = 36.dp)
                        Text(Dates.month(state.month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий месяц", { viewModel.showMonth(state.month.plusMonths(1)) }, size = 36.dp)
                    }
                }
                if (state.days.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        Text(
                            "За этот месяц операций по счёту нет",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FinanceColors.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    }
                }
                items(state.days, key = { "d:" + it.date }) { day -> DayCard(day, state.mainCurrency, onOpenOperation) }
            }
        }
        FloatingActionButton(
            onClick = { onAddOperation(viewModel.uid) },
            shape = CircleShape,
            containerColor = FinanceColors.Primary,
            contentColor = FinanceColors.Card,
            elevation = FloatingActionButtonDefaults.elevation(6.dp),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Добавить операцию по счёту") }
    }
}

/** The account as a bright card: blue, or red for a credit card with its debt and what is left of the limit. */
@Composable
private fun BalanceCard(item: AccountWithBalance, groupName: String) {
    val account = item.account
    val credit = account.isCredit
    val colors = if (credit) listOf(Color(0xFFE5574C), Color(0xFFB3261E)) else listOf(Color(0xFF1F7FE0), Color(0xFF1556B8))
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(colors))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(if (credit) Icons.Outlined.CreditCard else Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                if (credit) "Кредитная карта" else groupName.ifEmpty { "Счёт" },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                account.currencyCode,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val debt = CreditCard.debt(item.balance)
            Text(if (credit && debt > 0) "Долг" else "Остаток", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            Text(
                MoneyFormatter.format(if (credit && debt > 0) debt else item.balance, account.currencyCode),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                style = TabularNumbers,
                maxLines = 1,
            )
            if (credit && account.creditLimit > 0) {
                Text(
                    "Доступно ${MoneyFormatter.format(CreditCard.available(account.creditLimit, item.balance), account.currencyCode)}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                )
            }
        }
    }
}
