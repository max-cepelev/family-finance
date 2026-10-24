package com.familyfinance.ui.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyfinance.ui.theme.FinanceColors

private val ROWS = listOf("789÷", "456×", "123-", ",0⌫+")

private val NAMES = mapOf(
    Calculator.DIVIDE to "Разделить",
    Calculator.TIMES to "Умножить",
    Calculator.MINUS to "Минус",
    Calculator.PLUS to "Плюс",
    Calculator.BACKSPACE to "Стереть",
    Calculator.COMMA to "Запятая",
)

/** Calculator keys in place of the system keyboard; long press on ⌫ clears the whole amount. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AmountKeypad(onKey: (Char) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    val operator = Calculator.isOperator(key)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (operator) FinanceColors.PrimarySoft else FinanceColors.Card)
                            .combinedClickable(
                                role = Role.Button,
                                onClick = { onKey(key) },
                                onLongClick = if (key == Calculator.BACKSPACE) onClear else null,
                            )
                            .semantics { contentDescription = NAMES[key] ?: key.toString() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (key == Calculator.MINUS) "−" else key.toString(),
                            fontSize = 22.sp,
                            fontWeight = if (operator) FontWeight.Medium else FontWeight.Normal,
                            color = if (operator) FinanceColors.OnPrimarySoft else FinanceColors.Ink,
                        )
                    }
                }
            }
        }
    }
}
