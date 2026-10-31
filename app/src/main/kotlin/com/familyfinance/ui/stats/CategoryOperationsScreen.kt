package com.familyfinance.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.model.BudgetState
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.RoundIconButton
import com.familyfinance.ui.common.TabularNumbers
import com.familyfinance.ui.theme.FinanceColors
import com.familyfinance.ui.transactions.DayCard

/** One category over a statistics period: a card in its colour with the sum and budget, then its operations. */
@Composable
fun CategoryOperationsScreen(
    onClose: () -> Unit,
    onEditCategory: (String) -> Unit,
    onOpenOperation: (String) -> Unit,
    onAddOperation: (categoryUid: String) -> Unit,
    viewModel: CategoryOperationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Deleted here or on the other phone: nothing left to show.
    LaunchedEffect(state.loading, state.category) { if (!state.loading && state.category == null) onClose() }
    val category = state.category ?: return

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", onClose)
                Text(category.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                RoundIconButton(Icons.Outlined.Edit, "Изменить категорию", { onEditCategory(viewModel.uid) })
            }
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "card") { SumCard(state) }
                item(key = "period") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(36.dp)) {
                            if (state.period.previous() != null) RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущий период", viewModel::previous, size = 36.dp)
                        }
                        Text(Dates.period(state.period), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        Box(Modifier.size(36.dp)) {
                            if (state.period.next() != null) RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий период", viewModel::next, size = 36.dp)
                        }
                    }
                }
                if (state.days.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        Text(
                            "За этот период операций нет",
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
        ) { Icon(Icons.Filled.Add, contentDescription = "Добавить операцию в категорию") }
    }
}

@Composable
private fun SumCard(state: CategoryOperationsUiState) {
    val category = state.category ?: return
    val color = state.color
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.08f), lerp(color, Color.Black, 0.22f))))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Text(category.icon ?: category.name.take(1).uppercase(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                (if (category.type == CategoryType.INCOME) "Доходы" else "Расходы") + " · " + Dates.period(state.period),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(MoneyFormatter.format(state.total, state.mainCurrency), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, style = TabularNumbers, maxLines = 1)
        state.budget?.let { budget ->
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(budget.share.coerceIn(0.0, 1.0).toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White))
            }
            val limit = MoneyFormatter.format(budget.budget.amount, state.mainCurrency)
            Text(
                if (budget.level == BudgetState.Level.OVER) "Бюджет $limit · превышен на ${MoneyFormatter.format(-budget.left, state.mainCurrency)}"
                else "Бюджет $limit · осталось ${MoneyFormatter.format(budget.left, state.mainCurrency)}",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                fontWeight = if (budget.level == BudgetState.Level.OVER) FontWeight.SemiBold else null,
            )
        }
    }
}
