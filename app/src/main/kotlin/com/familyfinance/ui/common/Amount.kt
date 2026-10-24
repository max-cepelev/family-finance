package com.familyfinance.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.familyfinance.domain.model.TransactionType
import com.familyfinance.ui.theme.FinanceColors

/** Digits of equal width, so amounts line up in columns. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

fun signedColor(minor: Long): Color = when {
    minor > 0 -> FinanceColors.Income
    minor < 0 -> FinanceColors.Expense
    else -> FinanceColors.Muted
}

fun typeColor(type: TransactionType): Color = when (type) {
    TransactionType.INCOME -> FinanceColors.Income
    TransactionType.EXPENSE -> FinanceColors.Expense
    TransactionType.TRANSFER -> FinanceColors.Ink
}

/** Amount colored by sign. [explicitPlus] prefixes positive values with "+". */
@Composable
fun Amount(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    weight: FontWeight? = null,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    explicitPlus: Boolean = false,
) {
    val text = MoneyFormatter.format(minor, currency)
    Text(
        if (explicitPlus && minor > 0) "+$text" else text,
        modifier = modifier,
        color = signedColor(minor),
        fontWeight = weight,
        style = style.merge(TabularNumbers),
        maxLines = 1,
        softWrap = false,
    )
}

/** Unsigned amount in the color of the operation type. */
@Composable
fun TypedAmount(
    minor: Long,
    currency: String,
    type: TransactionType,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Text(
        MoneyFormatter.format(minor, currency),
        modifier = modifier,
        color = typeColor(type),
        style = style.merge(TabularNumbers),
        maxLines = 1,
        softWrap = false,
    )
}

/** Plain colored amount, e.g. a day's income in blue. */
@Composable
fun ColoredAmount(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    weight: FontWeight? = null,
) {
    Text(text, modifier = modifier, color = color, style = style.merge(TabularNumbers), fontWeight = weight, maxLines = 1, softWrap = false)
}
