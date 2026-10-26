package com.familyfinance.ui.transaction

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familyfinance.ui.common.CategoryAvatar
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.TabularNumbers
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors

/** Marks each part in the bar and in its row, so the two read together. */
private val PART_COLORS = listOf(
    Color(0xFF2E6B4F), Color(0xFFE0A93B), Color(0xFF6E8FB5), Color(0xFFB5655E), Color(0xFF8A6FB0), Color(0xFF5E9E8F),
)

private fun partColor(index: Int) = PART_COLORS[index % PART_COLORS.size]

/**
 * "Разделить по категориям": the operation's amount over several categories. The first part takes whatever the
 * others leave, so the parts always add up; the keypad types the [active] part.
 */
@Composable
fun SplitEditor(
    title: String,
    total: String,
    currency: String,
    parts: List<PartUi>,
    active: Int,
    hasError: Boolean,
    onActivate: (Int) -> Unit,
    onPickCategory: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onKey: (Char) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text(
                "Разделить по категориям",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = FinanceColors.Muted, modifier = Modifier.weight(1f))
                Text(total, style = MaterialTheme.typography.titleLarge.merge(TabularNumbers), fontWeight = FontWeight.Medium)
            }
            ShareBar(parts)
            WhiteCard(Modifier.fillMaxWidth()) {
                parts.forEachIndexed { i, part ->
                    PartRow(
                        index = i,
                        part = part,
                        currency = currency,
                        active = i == active && i > 0,
                        onActivate = { onActivate(i) },
                        onPickCategory = { onPickCategory(i) },
                        onRemove = { onRemove(i) },
                    )
                    HorizontalDivider(color = FinanceColors.Hairline)
                }
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onAdd).heightIn(min = 48.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = FinanceColors.Primary, modifier = Modifier.size(18.dp))
                    Text("Добавить категорию", color = FinanceColors.Primary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                if (hasError) "Части больше суммы операции или у какой-то нет суммы — первой категории ничего не остаётся."
                else "Первая строка забирает остаток, поэтому сумма всегда сходится с операцией. В статистике каждая часть идёт в свою категорию.",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasError) FinanceColors.Expense else FinanceColors.Muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().background(FinanceColors.Track).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (active > 0) AmountKeypad(onKey = onKey, onClear = onClear)
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(48.dp),
            ) { Text("Сохранить", fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun ShareBar(parts: List<PartUi>) {
    val amounts = parts.map { (it.amount ?: 0L).coerceAtLeast(0L) }
    if (amounts.sum() <= 0L) return
    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        amounts.forEachIndexed { i, amount ->
            if (amount > 0) Box(Modifier.weight(amount.toFloat()).fillMaxHeight().background(partColor(i)))
        }
    }
}

@Composable
private fun PartRow(index: Int, part: PartUi, currency: String, active: Boolean, onActivate: () -> Unit, onPickCategory: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (active) Modifier.background(FinanceColors.PrimarySoft.copy(alpha = 0.35f)) else Modifier)
            .heightIn(min = 60.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(partColor(index)))
        Row(
            Modifier.weight(1f).clickable(onClick = onPickCategory).heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Категория: ${part.label.ifEmpty { "не выбрана" }}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryAvatar(part.top?.name, part.top?.uid, size = 32.dp, icon = part.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // The leaf name fits next to the amount; the top-level one goes under it.
                Text(
                    if (part.categoryUid == null) "Выберите категорию" else part.shortName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (part.categoryUid == null) FinanceColors.Expense else FinanceColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val parent = part.top?.name?.takeIf { it != part.shortName }
                val hint = if (index == 0) listOfNotNull("остаток", parent).joinToString(" · ") else parent
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // The first part's amount is computed, so only the others take the keypad.
        val amountModifier = if (index == 0) Modifier else Modifier.clickable(onClick = onActivate)
        Row(
            amountModifier.heightIn(min = 48.dp).padding(horizontal = 4.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Сумма: ${part.amountText.ifEmpty { "0" }}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (index == 0) {
                // Computed, so shown as money; below zero it is the reason the split cannot be saved.
                val remainder = part.amount ?: 0L
                Text(
                    MoneyFormatter.format(remainder, currency),
                    style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                    fontWeight = FontWeight.Medium,
                    color = if (remainder <= 0) FinanceColors.Expense else FinanceColors.Ink,
                )
            } else {
                Text(
                    Calculator.display(part.amountText).ifEmpty { "0" },
                    style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                    fontWeight = FontWeight.Medium,
                    color = if (part.amountText.isEmpty()) FinanceColors.ChipBorder else FinanceColors.Ink,
                )
                if (active) Box(Modifier.padding(start = 2.dp).width(2.dp).height(20.dp).background(FinanceColors.Expense))
                Text(" " + MoneyFormatter.symbol(currency), color = FinanceColors.Muted, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (index > 0) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Убрать часть", tint = FinanceColors.Muted, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}
