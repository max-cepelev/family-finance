package com.familyfinance.ui.budget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.model.BudgetState
import com.familyfinance.domain.model.Category
import com.familyfinance.ui.common.BottomSheet
import com.familyfinance.ui.common.CategoryAvatar
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.RoundIconButton
import com.familyfinance.ui.common.TabularNumbers
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.common.rememberFieldText
import androidx.compose.runtime.setValue
import com.familyfinance.ui.theme.FinanceColors
import java.time.LocalDate
import java.time.YearMonth

/** Bar colour of a budget: green, orange from 90%, red once over. */
fun budgetColor(level: BudgetState.Level): Color = when (level) {
    BudgetState.Level.OK -> BudgetGreen
    BudgetState.Level.NEAR -> FinanceColors.Warning
    BudgetState.Level.OVER -> FinanceColors.Expense
}

private val BudgetGreen = Color(0xFF22A447)

/** Monthly limits: all expenses on top, then by category, the most spent first. */
@Composable
fun BudgetsScreen(onClose: () -> Unit, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", onClose)
            Text("Бюджеты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            RoundIconButton(Icons.Filled.Add, "Новый бюджет", viewModel::openNew)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "month") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущий месяц", { viewModel.showMonth(state.month.minusMonths(1)) }, size = 36.dp)
                    Text(Dates.month(state.month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий месяц", { viewModel.showMonth(state.month.plusMonths(1)) }, size = 36.dp)
                }
            }
            if (!state.loading && state.overall == null && state.byCategory.isEmpty()) {
                item(key = "empty") {
                    WhiteCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Бюджетов пока нет", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Задайте лимит расходов на месяц — на всё сразу или на категорию. Он действует каждый месяц, пока его не удалить.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FinanceColors.Muted,
                            )
                            Button(onClick = viewModel::openNew, colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary)) {
                                Text("Новый бюджет", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            state.overall?.let { overall ->
                item(key = "overall") { OverallCard(overall.state, state.month, state.mainCurrency) { viewModel.openEdit(overall.state.budget) } }
            }
            if (state.byCategory.isNotEmpty()) {
                item(key = "title") {
                    Text(
                        "ПО КАТЕГОРИЯМ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        color = FinanceColors.Muted,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                    )
                }
                items(state.byCategory, key = { it.state.budget.uid }) { item ->
                    CategoryBudgetCard(item, state.mainCurrency) { viewModel.openEdit(item.state.budget) }
                }
            }
        }
    }
    state.edit?.let { edit ->
        BudgetSheet(
            edit = edit,
            categories = state.categories,
            lastMonth = state.lastMonth,
            currency = state.mainCurrency,
            onTarget = viewModel::setTarget,
            onAmount = viewModel::setAmount,
            onWarn = viewModel::setWarn,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
private fun BudgetBar(state: BudgetState, height: Int) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape((height / 2).dp)).background(FinanceColors.Track)) {
        Box(
            Modifier.fillMaxWidth(state.share.coerceIn(0.0, 1.0).toFloat()).height(height.dp)
                .clip(RoundedCornerShape((height / 2).dp)).background(budgetColor(state.level)),
        )
    }
}

/** "Осталось 6 264,44 ₽" or "Превышен на 536,35 ₽". */
@Composable
private fun LeftLine(state: BudgetState, currency: String, modifier: Modifier = Modifier) {
    if (state.level == BudgetState.Level.OVER) {
        Text("Превышен на ${MoneyFormatter.format(-state.left, currency)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = FinanceColors.Expense, modifier = modifier)
    } else {
        Text("Осталось ${MoneyFormatter.format(state.left, currency)}", style = MaterialTheme.typography.bodySmall.merge(TabularNumbers), color = FinanceColors.Muted, modifier = modifier)
    }
}

@Composable
private fun OverallCard(state: BudgetState, month: YearMonth, currency: String, onClick: () -> Unit) {
    WhiteCard(Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ВСЕ РАСХОДЫ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, color = FinanceColors.Muted)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(MoneyFormatter.format(state.spent, currency), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, style = TabularNumbers)
                Text("из ${MoneyFormatter.format(state.budget.amount, currency)}", style = MaterialTheme.typography.bodyMedium, color = FinanceColors.Muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            BudgetBar(state, 10)
            Row {
                LeftLine(state, currency, Modifier.weight(1f))
                val today = LocalDate.now()
                val left = when {
                    month < YearMonth.from(today) -> "месяц закончился"
                    month == YearMonth.from(today) -> "ещё " + Dates.days((month.lengthOfMonth() - today.dayOfMonth + 1).toLong())
                    else -> null
                }
                if (left != null) Text(left, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
            }
        }
    }
}

@Composable
private fun CategoryBudgetCard(item: BudgetUi, currency: String, onClick: () -> Unit) {
    val state = item.state
    WhiteCard(Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryAvatar(item.category?.name, item.category?.uid, size = 38.dp, icon = item.category?.icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.category?.name ?: "Категория удалена", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${MoneyFormatter.format(state.spent, currency)} из ${MoneyFormatter.format(state.budget.amount, currency)}",
                        style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                        color = FinanceColors.Muted,
                    )
                }
                Text("${(state.share * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = budgetColor(state.level))
            }
            BudgetBar(state, 10)
            LeftLine(state, currency)
        }
    }
}

/** New or existing budget: what for, the monthly limit, warnings. */
@Composable
private fun BudgetSheet(
    edit: BudgetEditUi,
    categories: List<Category>,
    lastMonth: Long?,
    currency: String,
    onTarget: (String?) -> Unit,
    onAmount: (String) -> Unit,
    onWarn: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Warnings need notifications: ask once, on save; the budget is saved either way.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onSave() }
    fun save() {
        val missing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && edit.warn &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (missing) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else onSave()
    }

    // Typed into local state: a round trip through the view model's flow drops fast keystrokes.
    var amount by rememberFieldText(edit.uid ?: "new", edit.amountText)

    BottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (edit.uid == null) "Новый бюджет" else "Бюджет", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
            Text("На что", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = FinanceColors.Muted, modifier = Modifier.padding(horizontal = 4.dp))
            val targets: List<Category?> = listOf(null) + categories
            targets.chunked(TARGETS_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { category ->
                        val on = category?.uid == edit.categoryUid
                        Surface(
                            onClick = { onTarget(category?.uid) },
                            shape = RoundedCornerShape(18.dp),
                            color = FinanceColors.Card,
                            border = BorderStroke(2.dp, if (on) FinanceColors.Primary else Color.Transparent),
                            modifier = Modifier.weight(1f).semantics { selected = on },
                        ) {
                            Column(Modifier.padding(vertical = 10.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (category == null) {
                                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(FinanceColors.Muted), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Functions, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                } else {
                                    CategoryAvatar(category.name, category.uid, size = 34.dp, icon = category.icon)
                                }
                                Text(category?.name ?: "Все расходы", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    repeat(TARGETS_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (edit.amountError) "Лимит в месяц — введите сумму" else "Лимит в месяц",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (edit.amountError) FinanceColors.Expense else FinanceColors.Muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).background(FinanceColors.Card).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = amount,
                        onValueChange = { amount = it; onAmount(it) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, color = FinanceColors.Ink),
                        cursorBrush = SolidColor(FinanceColors.Primary),
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Лимит в месяц" },
                    )
                    Text(MoneyFormatter.symbol(currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = FinanceColors.ChipBorder)
                }
                if (lastMonth != null && lastMonth > 0) {
                    Text(
                        "В прошлом месяце ушло ${MoneyFormatter.format(lastMonth, currency)}",
                        style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                        color = FinanceColors.Muted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            WhiteCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().clickable { onWarn(!edit.warn) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Предупредить на 80% и 100%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("Уведомление от приложения", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
                    }
                    Switch(checked = edit.warn, onCheckedChange = onWarn, colors = SwitchDefaults.colors(checkedTrackColor = FinanceColors.Primary))
                }
            }

            Button(
                onClick = ::save,
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Сохранить", fontWeight = FontWeight.SemiBold) }
            if (edit.uid != null) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Удалить бюджет", color = FinanceColors.Expense, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private const val TARGETS_PER_ROW = 4
