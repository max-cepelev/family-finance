package com.familyfinance.ui.receipt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.familyfinance.ui.common.BottomSheet
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.TabularNumbers
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors
import com.familyfinance.ui.transactions.TransactionRow

/** "Чек уже есть в операциях": attach the receipt to the operation it matches, or treat it as another purchase. */
@Composable
fun ReceiptMatchSheet(
    match: ReceiptMatchUi,
    onRemember: (Boolean) -> Unit,
    onAttach: () -> Unit,
    onOther: () -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Чек уже есть в операциях",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            WhiteCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(FinanceColors.PrimarySoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = FinanceColors.OnPrimarySoft, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (match.isRefund) "Чек · возврат" else "Чек · покупка", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${match.time} · касса •• ${match.registerTail}",
                            style = MaterialTheme.typography.bodySmall,
                            color = FinanceColors.Muted,
                        )
                    }
                    Text(
                        MoneyFormatter.format(match.amount, match.candidate.currencyCode),
                        style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(
                "Совпадает по сумме и времени с операцией:",
                style = MaterialTheme.typography.bodyMedium,
                color = FinanceColors.Muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            WhiteCard(Modifier.fillMaxWidth()) { TransactionRow(match.candidate, onClick = {}) }
            if (match.rememberHint != null) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Switch) { onRemember(!match.remember) }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Запомнить кассу", style = MaterialTheme.typography.bodyLarge)
                        Text(match.rememberHint, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
                    }
                    Switch(
                        checked = match.remember,
                        onCheckedChange = onRemember,
                        colors = SwitchDefaults.colors(checkedTrackColor = FinanceColors.Primary),
                    )
                }
            }
            Button(
                onClick = onAttach,
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Прикрепить чек к операции", fontWeight = FontWeight.Medium) }
            TextButton(onClick = onOther, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Это другая покупка", color = FinanceColors.Primary, fontWeight = FontWeight.Medium)
            }
        }
    }
}
