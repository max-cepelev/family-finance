package com.familyfinance.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.ui.draw.clip
import com.familyfinance.ui.common.RoundIconButton
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.model.AccountWithBalance
import com.familyfinance.domain.model.CreditCard
import com.familyfinance.ui.common.Amount
import com.familyfinance.ui.common.CardShape
import com.familyfinance.ui.common.ColoredAmount
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.ScreenTitle
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun AccountsScreen(
    onOpenAccount: (uid: String?) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Счета") {
            RoundIconButton(Icons.Filled.Add, "Добавить счёт", onClick = { onOpenAccount(null) })
        }
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { collapsed = if (isOpen) collapsed + group.name else collapsed - group.name }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(group.name.uppercase(), color = FinanceColors.Muted, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        ColoredAmount(MoneyFormatter.format(group.total, state.mainCurrency), FinanceColors.Muted, style = MaterialTheme.typography.labelMedium, weight = FontWeight.SemiBold)
                        Icon(
                            if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isOpen) "Свернуть" else "Развернуть",
                            tint = FinanceColors.Muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    if (isOpen) WhiteCard(Modifier.fillMaxWidth()) {
                        val today = LocalDate.now()
                        group.accounts.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = FinanceColors.Hairline, modifier = Modifier.padding(start = 62.dp))
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenAccount(item.account.uid) }
                                    .heightIn(min = 58.dp).padding(horizontal = 14.dp, vertical = 8.dp)
                                    .alpha(if (item.account.hidden) 0.55f else 1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AccountTile(item.account.isCredit)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    if (item.account.hidden) Text("скрыт", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
                                    creditNote(item, today)?.let { (text, soon) ->
                                        Text(text, style = MaterialTheme.typography.bodySmall, color = if (soon) FinanceColors.Warning else FinanceColors.Muted)
                                    }
                                }
                                ColoredAmount(
                                    MoneyFormatter.format(item.balance, item.account.currencyCode),
                                    if (item.balance < 0) FinanceColors.Expense else FinanceColors.Ink,
                                    style = MaterialTheme.typography.bodyLarge,
                                    weight = FontWeight.SemiBold,
                                )
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = FinanceColors.Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A credit card in red, any other account in blue. */
@Composable
private fun AccountTile(credit: Boolean) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(if (credit) FinanceColors.Expense else FinanceColors.Primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (credit) Icons.Outlined.CreditCard else Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Days before a payment when the card's line turns orange. */
private const val PAYMENT_SOON_DAYS = 3

/**
 * "Доступно 39 184,58 ₽ · платёж 25 ноя" under a credit card; true when the payment is near and something is owed.
 * Null for other accounts and for a card with neither a limit nor a payment day.
 */
private fun creditNote(item: AccountWithBalance, today: LocalDate): Pair<String, Boolean>? {
    val account = item.account
    if (!account.isCredit) return null
    val available = account.creditLimit.takeIf { it > 0 }
        ?.let { "Доступно ${MoneyFormatter.format(CreditCard.available(it, item.balance), account.currencyCode)}" }
    val date = account.paymentDay?.let { CreditCard.nextPayment(it, today) }
    val days = date?.let { CreditCard.daysUntil(it, today) }
    val soon = days != null && days <= PAYMENT_SOON_DAYS && CreditCard.debt(item.balance) > 0
    val payment = when {
        date == null -> null
        soon && days == 0L -> "платёж сегодня"
        soon -> "платёж через ${Dates.days(days!!)}"
        else -> "платёж ${Dates.dayMonthShort(date)}"
    }
    val text = listOfNotNull(available, payment).joinToString(" · ").ifEmpty { return null }
    return text to soon
}

@Composable
private fun Summary(state: AccountsUiState) {
    WhiteCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ИТОГО ПО ВСЕМ СЧЕТАМ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = FinanceColors.Muted)
            ColoredAmount(
                MoneyFormatter.format(state.total, state.mainCurrency),
                if (state.total < 0) FinanceColors.Expense else FinanceColors.Ink,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, letterSpacing = (-1).sp),
                weight = FontWeight.ExtraBold,
            )
            val assets = state.assets.coerceAtLeast(0)
            val debts = abs(state.liabilities)
            if (assets + debts > 0) {
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (assets > 0) Box(Modifier.weight(assets.toFloat()).fillMaxHeight().background(FinanceColors.Income))
                    if (debts > 0) Box(Modifier.weight(debts.toFloat()).fillMaxHeight().background(FinanceColors.Expense))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryValue("Активы", assets, state.mainCurrency, Modifier.weight(1f))
                SummaryValue("Долги", debts, state.mainCurrency, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: Long, currency: String, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
        ColoredAmount(MoneyFormatter.format(value, currency), FinanceColors.Ink, style = MaterialTheme.typography.bodySmall, weight = FontWeight.SemiBold)
    }
}
