package com.familyfinance.ui.banks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyfinance.domain.model.Account
import com.familyfinance.ui.common.BottomSheet
import com.familyfinance.ui.common.ChoiceDialog
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.common.rememberFieldText
import com.familyfinance.ui.theme.FinanceColors

/** Link a card (or the bank's "Без номера" fallback) to an account. */
@Composable
fun CardSheet(
    edit: CardEditUi,
    accounts: List<Account>,
    onDigits: (String) -> Unit,
    onAccount: (String) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var digits by rememberFieldText(edit.uid to edit.bank, edit.last4.orEmpty())
    val accountName = accounts.firstOrNull { it.uid == edit.accountUid }?.name

    BottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                if (edit.isFallback) "Без номера карты — ${edit.bank.title}" else "Карта ${edit.bank.genitive()}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            WhiteCard(Modifier.fillMaxWidth()) {
                if (!edit.isFallback) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Последние 4 цифры", style = MaterialTheme.typography.bodySmall, color = if (edit.digitsError != null) FinanceColors.Expense else FinanceColors.Muted)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("••", fontSize = 18.sp, color = FinanceColors.Muted)
                            BasicTextField(
                                value = digits,
                                onValueChange = { typed ->
                                    digits = typed.filter(Char::isDigit).take(4)
                                    onDigits(digits)
                                },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, color = FinanceColors.Ink),
                                cursorBrush = SolidColor(FinanceColors.Primary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.width(96.dp).semantics { contentDescription = "Последние 4 цифры" },
                            )
                        }
                        edit.digitsError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Expense) }
                    }
                    HorizontalDivider(color = FinanceColors.Hairline)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { picking = true }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Записывать на счёт", style = MaterialTheme.typography.bodySmall, color = if (edit.accountError) FinanceColors.Expense else FinanceColors.Muted)
                        Text(accountName ?: "Выберите счёт", style = MaterialTheme.typography.bodyLarge, color = if (edit.accountError) FinanceColors.Expense else FinanceColors.Ink)
                    }
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = FinanceColors.Muted)
                }
            }
            Text(
                if (edit.isFallback) {
                    "Сюда попадут уведомления ${edit.bank.genitive()} без номера карты и по картам, которые ещё не привязаны."
                } else {
                    "Операции из уведомлений ${edit.bank.genitive()} с картой •• ${edit.last4.orEmpty().ifEmpty { "____" }} будут записываться " +
                        "на «${accountName ?: "…"}». У одного банка может быть сколько угодно карт."
                },
                style = MaterialTheme.typography.bodySmall,
                color = FinanceColors.Muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (edit.uid != null) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Удалить привязку", color = FinanceColors.Expense, fontWeight = FontWeight.Medium)
                }
            }
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Сохранить", fontWeight = FontWeight.Medium) }
        }
    }

    if (picking) {
        ChoiceDialog(
            title = "Счёт",
            items = accounts.map { it.uid to it.name },
            selected = edit.accountUid,
            onPick = { picking = false; onAccount(it) },
            onDismiss = { picking = false },
        )
    }
}
