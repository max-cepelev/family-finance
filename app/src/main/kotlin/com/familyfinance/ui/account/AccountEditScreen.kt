package com.familyfinance.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.model.CreditCard
import com.familyfinance.ui.common.CardShape
import com.familyfinance.ui.common.ChoiceDialog
import com.familyfinance.ui.common.ColoredAmount
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.TextInputDialog
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.common.rememberFieldText
import com.familyfinance.ui.theme.FinanceColors
import com.familyfinance.ui.transaction.TransactionDraft
import java.time.LocalDate

private enum class AccountDialog { GROUP, NEW_GROUP, CURRENCY, BALANCE, PAYMENT_DAY, DELETE }

@Composable
fun AccountEditScreen(onClose: () -> Unit, viewModel: AccountEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }
    LaunchedEffect(state.done) { if (state.done) onClose() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text(if (state.isNew) "Новый счёт" else "Счёт", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        }
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val form = state.form
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.isNew) {
                Surface(shape = CardShape, color = FinanceColors.Primary, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (form.isCredit) "Долг по карте" else "Остаток",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FinanceColors.OnPrimaryMuted,
                                )
                                ColoredAmount(
                                    MoneyFormatter.format(if (form.isCredit) CreditCard.debt(state.balance) else state.balance, form.currencyCode),
                                    FinanceColors.Card,
                                    style = MaterialTheme.typography.headlineSmall,
                                    weight = FontWeight.Medium,
                                )
                                if (!form.isCredit) {
                                    Text(Dates.operations(state.operations), style = MaterialTheme.typography.labelMedium, color = FinanceColors.OnPrimaryMuted)
                                }
                            }
                            OutlinedIconButton(
                                onClick = { dialog = AccountDialog.BALANCE },
                                border = BorderStroke(1.dp, FinanceColors.OnPrimaryMuted),
                            ) { Icon(Icons.Outlined.Edit, contentDescription = "Изменить остаток", tint = FinanceColors.Card) }
                        }
                        if (form.isCredit) CreditSummary(state.balance, form)
                    }
                }
            }

            WhiteCard(Modifier.fillMaxWidth()) {
                TextRow("Название", form.name, viewModel::setName, isError = state.nameError, hint = if (state.nameError) "Введите название" else null)
                HorizontalDivider(color = FinanceColors.Hairline)
                PickRow("Группа", state.groups.firstOrNull { it.uid == form.groupUid }?.name ?: "Без группы") { dialog = AccountDialog.GROUP }
                HorizontalDivider(color = FinanceColors.Hairline)
                if (state.currencyLocked) {
                    InfoRow("Валюта", "${form.currencyCode} ${MoneyFormatter.symbol(form.currencyCode)}", "Не меняется: на счёте есть операции")
                } else {
                    PickRow("Валюта", "${form.currencyCode} ${MoneyFormatter.symbol(form.currencyCode)}") { dialog = AccountDialog.CURRENCY }
                }
                HorizontalDivider(color = FinanceColors.Hairline)
                if (state.isNew) {
                    TextRow("Начальный остаток", form.openingBalance, viewModel::setOpeningBalance, keyboard = KeyboardType.Decimal, placeholder = "0")
                    HorizontalDivider(color = FinanceColors.Hairline)
                }
                TextRow(
                    "Заметка", form.note, viewModel::setNote,
                    hint = "Последние цифры карты: по ним уведомления банка попадут на этот счёт",
                )
            }

            WhiteCard(Modifier.fillMaxWidth()) {
                SwitchRow("Кредитная карта", "Лимит, «доступно» и дата платежа", form.isCredit, viewModel::setCredit)
                if (form.isCredit) {
                    HorizontalDivider(color = FinanceColors.Hairline)
                    TextRow(
                        "Кредитный лимит, ${MoneyFormatter.symbol(form.currencyCode)}", form.creditLimit, viewModel::setCreditLimit,
                        keyboard = KeyboardType.Decimal, placeholder = "0",
                    )
                    HorizontalDivider(color = FinanceColors.Hairline)
                    PickRow(
                        "День платежа",
                        form.paymentDay?.let { "$it-е число каждого месяца" } ?: "Не указан",
                        note = "Необязательно: только чтобы видеть дату в списке счетов",
                    ) { dialog = AccountDialog.PAYMENT_DAY }
                }
            }

            WhiteCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable { viewModel.setHidden(!form.hidden) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Скрыть счёт", style = MaterialTheme.typography.bodyLarge)
                        Text("Не показывать при вводе операций и в итогах", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
                    }
                    Switch(
                        checked = form.hidden,
                        onCheckedChange = viewModel::setHidden,
                        colors = SwitchDefaults.colors(checkedTrackColor = FinanceColors.Primary),
                    )
                }
            }

            if (!state.isNew) {
                TextButton(onClick = { dialog = AccountDialog.DELETE }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Удалить счёт", color = FinanceColors.Expense, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = viewModel::save,
            colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
        ) { Text("Сохранить", fontWeight = FontWeight.Medium) }
    }

    when (dialog) {
        AccountDialog.GROUP -> ChoiceDialog(
            title = "Группа",
            items = state.groups.map { it.uid to it.name },
            selected = state.form.groupUid,
            onPick = { dialog = null; viewModel.pickGroup(it) },
            onDismiss = { dialog = null },
            extra = "Новая группа…" to { dialog = AccountDialog.NEW_GROUP },
        )
        AccountDialog.NEW_GROUP -> TextInputDialog(
            title = "Новая группа",
            label = "Название группы",
            onConfirm = { dialog = null; viewModel.createGroup(it) },
            onDismiss = { dialog = null },
        )
        AccountDialog.CURRENCY -> ChoiceDialog(
            title = "Валюта",
            items = state.currencies.map { it to "$it ${MoneyFormatter.symbol(it)}" },
            selected = state.form.currencyCode,
            onPick = { dialog = null; viewModel.pickCurrency(it) },
            onDismiss = { dialog = null },
        )
        AccountDialog.BALANCE -> TextInputDialog(
            title = "Изменить остаток",
            label = "Сколько на счёте сейчас, ${MoneyFormatter.symbol(state.form.currencyCode)}",
            initial = MoneyFormatter.compactExact(state.balance),
            keyboard = KeyboardType.Decimal,
            note = "Разница запишется операцией «Корректировка остатка» без категории.",
            onConfirm = { dialog = null; viewModel.adjustBalance(it) },
            onDismiss = { dialog = null },
        )
        AccountDialog.PAYMENT_DAY -> ChoiceDialog(
            title = "День платежа",
            items = listOf("" to "Не указан") + (1..31).map { it.toString() to "$it-е число" },
            selected = state.form.paymentDay?.toString() ?: "",
            onPick = { dialog = null; viewModel.pickPaymentDay(it.toIntOrNull()) },
            onDismiss = { dialog = null },
        )
        AccountDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Удалить счёт?") },
            text = { Text("Удалить можно только счёт без операций.") },
            confirmButton = { TextButton(onClick = { dialog = null; viewModel.delete() }) { Text("Удалить", color = FinanceColors.Expense) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
        null -> Unit
    }

    if (state.deleteBlocked) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteBlocked,
            title = { Text("Удалить нельзя") },
            text = { Text("У счёта ${Dates.operations(state.operations)}: удаление стёрло бы историю. Скрыть его вместо этого?") },
            confirmButton = { TextButton(onClick = viewModel::hideInstead) { Text("Скрыть") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDeleteBlocked) { Text("Отмена") } },
        )
    }
}

@Composable
private fun TextRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    isError: Boolean = false,
    hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
) {
    var text by rememberFieldText(label, value)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (isError) FinanceColors.Expense else FinanceColors.Muted)
        BasicTextField(
            value = text,
            onValueChange = { text = it; onChange(it) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = FinanceColors.Ink),
            cursorBrush = SolidColor(FinanceColors.Primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty() && placeholder != null) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = FinanceColors.ChipBorder)
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = if (isError) FinanceColors.Expense else FinanceColors.Muted)
    }
}

/** Used share of the limit, what is available, and the next payment date. */
@Composable
private fun CreditSummary(balance: Long, form: AccountForm) {
    val limit = TransactionDraft.parseAmount(form.creditLimit, 2)?.takeIf { it > 0 }
    val payment = form.paymentDay?.let { Dates.dayMonthShort(CreditCard.nextPayment(it, LocalDate.now())) }
    if (limit == null && payment == null) return
    if (limit != null) {
        val used = (CreditCard.debt(balance).toFloat() / limit).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(FinanceColors.Card.copy(alpha = 0.25f))) {
            Box(Modifier.fillMaxWidth(used).fillMaxHeight().background(CreditBar))
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (limit != null) {
            Text(
                "Доступно ${MoneyFormatter.format(CreditCard.available(limit, balance), form.currencyCode)} " +
                    "из ${MoneyFormatter.format(limit, form.currencyCode)}",
                style = MaterialTheme.typography.bodySmall,
                color = FinanceColors.OnPrimaryMuted,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (payment != null) Text("платёж $payment", style = MaterialTheme.typography.bodySmall, color = FinanceColors.OnPrimaryMuted)
    }
}

@Composable
private fun SwitchRow(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = FinanceColors.Primary))
    }
}

/** The used part of a credit limit: the gold of the app's icon, readable on the green card. */
private val CreditBar = Color(0xFFF2C46B)

@Composable
private fun PickRow(label: String, value: String, note: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
            Text(value, style = MaterialTheme.typography.bodyLarge)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = FinanceColors.Muted)
    }
}

@Composable
private fun InfoRow(label: String, value: String, note: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        Text(note, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
    }
}
