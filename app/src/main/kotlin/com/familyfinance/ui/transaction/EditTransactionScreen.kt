package com.familyfinance.ui.transaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.model.Money
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.ui.common.CategoryAvatar
import com.familyfinance.ui.common.ChoiceChip
import com.familyfinance.ui.common.DateDialog
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.SegmentedToggle
import com.familyfinance.ui.common.TabularNumbers
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.common.rememberFieldText
import com.familyfinance.ui.common.typeColor
import com.familyfinance.ui.theme.FinanceColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private enum class Picker { DATE, ACCOUNT, TO_ACCOUNT, CATEGORY }

private val TYPES = listOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER)
private val TYPE_LABELS = listOf("Доход", "Расход", "Перевод")

/** Which amount the keypad types into. */
private enum class KeypadTarget { AMOUNT, TO_AMOUNT }

@Composable
fun EditTransactionScreen(
    onClose: () -> Unit,
    viewModel: EditTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var receiptInfo by remember { mutableStateOf(false) }
    // The amount comes first, so the keypad is open from the start; a receipt brings its own amount.
    val fromReceipt = viewModel.fromReceipt
    var keypad by rememberSaveable { mutableStateOf(if (fromReceipt) null else KeypadTarget.AMOUNT) }
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val noteFocus = remember { FocusRequester() }

    fun showKeypad(target: KeypadTarget) {
        focus.clearFocus()
        keypad = target
    }

    LaunchedEffect(state.done) { if (state.done) onClose() }
    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) {
            noteOpen = false
            showKeypad(KeypadTarget.AMOUNT)
            snackbar.showSnackbar("Сохранено. Следующая позиция")
        }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                Text(
                    when {
                        fromReceipt -> "Операция из чека"
                        state.isNew -> "Новая операция"
                        else -> "Операция"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (!fromReceipt && state.receipt != null) {
                    IconButton(onClick = { receiptInfo = true }) { Icon(Icons.Outlined.ReceiptLong, contentDescription = "Чек") }
                }
                if (!state.isNew && !state.loading) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Удалить") }
                }
            }

            val draft = state.draft
            if (draft == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val isTransfer = draft.type == TransactionType.TRANSFER
            val accent = if (isTransfer) FinanceColors.Primary else typeColor(draft.type)

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val receipt = state.receipt
                if (fromReceipt && receipt != null) {
                    ReceiptBanner(receipt, payee = draft.note, modifier = Modifier.padding(top = 4.dp))
                } else {
                    // A receipt fixes the type: a purchase is an expense, a refund an income.
                    SegmentedToggle(
                        options = TYPE_LABELS,
                        selected = TYPES.indexOf(draft.type),
                        onSelect = { viewModel.setType(TYPES[it]) },
                        selectedColor = { i -> if (TYPES[i] == TransactionType.TRANSFER) FinanceColors.Primary else typeColor(TYPES[i]) },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                AmountDisplay(
                    label = if (fromReceipt) "Сумма из чека" else "Сумма",
                    expression = draft.amountText,
                    currency = state.currency,
                    accent = accent,
                    active = keypad == KeypadTarget.AMOUNT,
                    isError = DraftError.AMOUNT in state.errors,
                    onClick = { showKeypad(KeypadTarget.AMOUNT) },
                )

                WhiteCard(Modifier.fillMaxWidth()) {
                    if (fromReceipt) {
                        NoteRow(
                            note = draft.note,
                            resetKey = state.savedTick,
                            onChange = viewModel::setNote,
                            placeholder = "Магазин",
                            icon = Icons.Outlined.Storefront,
                            modifier = Modifier.onFocusChanged { if (it.isFocused) keypad = null },
                        )
                        HorizontalDivider(color = FinanceColors.Hairline)
                    }
                    CompactRow(
                        label = "Дата",
                        value = if (receipt != null && fromReceipt) "${Dates.relative(draft.date)}, ${receipt.time}" else Dates.relative(draft.date),
                        leading = { RowIcon(Icons.Outlined.CalendarMonth) },
                        onClick = { picker = Picker.DATE },
                    )
                    HorizontalDivider(color = FinanceColors.Hairline)
                    CompactRow(
                        label = if (isTransfer) "Со счёта" else "Счёт",
                        value = state.accountName.ifEmpty { "Выберите счёт" },
                        leading = { RowIcon(Icons.Outlined.AccountBalanceWallet) },
                        trailing = state.accountBalance?.let { MoneyFormatter.format(it, state.accountCurrency) },
                        isError = DraftError.ACCOUNT in state.errors,
                        onClick = { picker = Picker.ACCOUNT },
                    )
                    HorizontalDivider(color = FinanceColors.Hairline)
                    val showNote = noteOpen || draft.note.isNotEmpty()
                    val openNote = {
                        noteOpen = true
                        keypad = null
                    }
                    if (isTransfer) {
                        CompactRow(
                            label = "На счёт",
                            value = state.toAccountName.ifEmpty { "Выберите счёт" },
                            leading = { RowIcon(Icons.Outlined.SwapHoriz) },
                            isError = DraftError.TO_ACCOUNT in state.errors,
                            onClick = { picker = Picker.TO_ACCOUNT },
                            action = if (showNote) null else ("Заметка" to openNote),
                        )
                    } else {
                        CompactRow(
                            label = "Категория",
                            value = state.categoryName.ifEmpty { if (fromReceipt) "Выберите категорию" else "Без категории" },
                            leading = { CategoryAvatar(state.categoryTop?.name, state.categoryTop?.uid, size = 22.dp, icon = state.categoryIcon) },
                            onClick = { picker = Picker.CATEGORY },
                            // The shop name is the note of a receipt operation, so there is no separate note.
                            action = if (showNote || fromReceipt) null else ("Заметка" to openNote),
                            isError = fromReceipt && draft.categoryUid == null,
                        )
                    }
                    if (showNote && !fromReceipt) {
                        HorizontalDivider(color = FinanceColors.Hairline)
                        NoteRow(
                            note = draft.note,
                            resetKey = state.savedTick,
                            onChange = viewModel::setNote,
                            modifier = Modifier.focusRequester(noteFocus).onFocusChanged { if (it.isFocused) keypad = null },
                        )
                        LaunchedEffect(noteOpen) { if (noteOpen && draft.note.isEmpty()) noteFocus.requestFocus() }
                    }
                }

                if (state.frequent.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.frequent.forEach { category ->
                            ChoiceChip(listOfNotNull(category.icon, category.name).joinToString(" "), selected = category.uid == draft.categoryUid) {
                                viewModel.pickCategory(category.uid)
                            }
                        }
                    }
                }

                if (fromReceipt) {
                    RememberRegisterRow(checked = receipt?.remember ?: true, onChange = viewModel::setRemember)
                }

                if (isTransfer && state.needsToAmount) {
                    AmountDisplay(
                        label = "Зачислено",
                        expression = draft.toAmountText,
                        currency = state.toCurrency,
                        accent = FinanceColors.Primary,
                        active = keypad == KeypadTarget.TO_AMOUNT,
                        isError = DraftError.TO_AMOUNT in state.errors,
                        onClick = { showKeypad(KeypadTarget.TO_AMOUNT) },
                    )
                }
            }

            val target = keypad
            Column(
                Modifier.fillMaxWidth()
                    .then(if (target != null) Modifier.background(FinanceColors.Track) else Modifier)
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (target != null) {
                    val toAccount = target == KeypadTarget.TO_AMOUNT
                    AmountKeypad(
                        onKey = { viewModel.pressKey(it, toAccount) },
                        onClear = { viewModel.clearAmount(toAccount) },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.isNew && !fromReceipt) {
                        OutlinedButton(
                            onClick = viewModel::saveAndAddAnother,
                            border = BorderStroke(1.dp, FinanceColors.Primary),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("Сохранить и ещё", color = FinanceColors.Primary, fontWeight = FontWeight.Medium) }
                    }
                    Button(
                        onClick = viewModel::save,
                        colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("Сохранить", fontWeight = FontWeight.Medium) }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).padding(top = 56.dp))

        val draft = state.draft
        when (picker) {
            Picker.DATE -> if (draft != null) DateDialog(
                initial = draft.date,
                onDismiss = { picker = null },
                onPick = { picker = null; viewModel.setDate(it) },
            )
            Picker.ACCOUNT -> AccountPickerDialog(
                accounts = state.accounts,
                selected = draft?.accountUid,
                onDismiss = { picker = null },
                onPick = { picker = null; viewModel.pickAccount(it) },
            )
            Picker.TO_ACCOUNT -> AccountPickerDialog(
                accounts = state.accounts.filter { it.uid != draft?.accountUid },
                selected = draft?.toAccountUid,
                onDismiss = { picker = null },
                onPick = { picker = null; viewModel.pickToAccount(it) },
            )
            Picker.CATEGORY -> CategoryPickerDialog(
                categories = state.categories,
                selected = draft?.categoryUid,
                onDismiss = { picker = null },
                onPick = { picker = null; viewModel.pickCategory(it) },
            )
            null -> Unit
        }
    }

    val receipt = state.receipt
    if (receiptInfo && receipt != null) {
        AlertDialog(
            onDismissRequest = { receiptInfo = false },
            title = { Text("Чек") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    receipt.details.forEach { (label, value) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(label, color = FinanceColors.Muted, modifier = Modifier.width(56.dp))
                            Text(value, style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { receiptInfo = false }) { Text("Закрыть") } },
            dismissButton = {
                TextButton(onClick = { receiptInfo = false; viewModel.detachReceipt() }) {
                    Text("Открепить", color = FinanceColors.Expense)
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить операцию?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

/** The typed expression, large, with its result under it once there is an operation. Tap to type. */
@Composable
private fun AmountDisplay(
    label: String,
    expression: String,
    currency: String,
    accent: Color,
    active: Boolean,
    isError: Boolean,
    onClick: () -> Unit,
) {
    val line = if (isError) FinanceColors.Expense else if (active) accent else FinanceColors.ChipBorder
    val result = Calculator.evaluate(expression)
        ?.takeIf { Calculator.hasOperation(expression) }
        ?.let { runCatching { MoneyFormatter.format(Money.toMinor(it, 2), currency) }.getOrNull() }
    Column(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            }
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            if (isError) "$label — введите сумму" else label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) FinanceColors.Expense else FinanceColors.Muted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Expression and caret take the free width; the currency sign stays at the right edge.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (expression.isEmpty()) "0" else Calculator.display(expression),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (expression.isEmpty()) FinanceColors.ChipBorder else FinanceColors.Ink,
                    style = TabularNumbers,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // A still caret: a blinking one would be an endless animation.
                if (active) Box(Modifier.padding(start = 2.dp).width(2.dp).height(30.dp).background(accent))
            }
            Text(MoneyFormatter.symbol(currency), fontSize = 24.sp, color = FinanceColors.Muted, modifier = Modifier.padding(start = 8.dp))
        }
        if (result != null) {
            Text("= $result", style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers), color = FinanceColors.Muted)
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) =
    Icon(icon, contentDescription = null, tint = FinanceColors.Muted, modifier = Modifier.size(20.dp))

/** A field as one 48dp line: icon, value, optional trailing text or action. The label goes to accessibility only. */
@Composable
private fun CompactRow(
    label: String,
    value: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    isError: Boolean = false,
    trailing: String? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { leading() }
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isError) FinanceColors.Expense else FinanceColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers), color = FinanceColors.Muted, maxLines = 1)
            }
        }
        if (action != null) {
            TextButton(onClick = action.second) { Text(action.first, color = FinanceColors.Muted) }
        }
        if (action == null && trailing == null) Spacer(Modifier.width(8.dp))
    }
}

/** "Новая касса" or "Касса знакома" above the amount of an operation made from a receipt. */
@Composable
private fun ReceiptBanner(receipt: ReceiptUi, payee: String, modifier: Modifier = Modifier) {
    val text = if (receipt.known && payee.isNotBlank()) {
        "Касса •• ${receipt.registerTail} знакома: «$payee». Проверьте и сохраните."
    } else {
        "Новая касса •• ${receipt.registerTail}. Укажите магазин и категорию — следующие чеки с неё заполнятся сами."
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FinanceColors.PrimarySoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = FinanceColors.OnPrimarySoft, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = FinanceColors.OnPrimarySoft)
    }
}

@Composable
private fun RememberRegisterRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Запомнить кассу", style = MaterialTheme.typography.bodyLarge)
            Text("Магазин и категория для следующих чеков", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = FinanceColors.Primary),
        )
    }
}

@Composable
private fun NoteRow(
    note: String,
    resetKey: Any?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Заметка",
    icon: ImageVector = Icons.Outlined.Edit,
) {
    var text by rememberFieldText(resetKey, note)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = FinanceColors.Muted, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it; onChange(it) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = FinanceColors.Ink),
            cursorBrush = SolidColor(FinanceColors.Primary),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = FinanceColors.Muted)
                    inner()
                }
            },
            modifier = modifier.weight(1f).semantics { contentDescription = placeholder },
        )
    }
}
