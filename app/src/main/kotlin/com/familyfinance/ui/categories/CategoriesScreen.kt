package com.familyfinance.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyfinance.domain.model.CategoryType
import com.familyfinance.ui.common.CategoryAvatar
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.MoneyFormatter
import com.familyfinance.ui.common.SegmentedToggle
import com.familyfinance.ui.common.TabularNumbers
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors

private val TYPES = listOf(CategoryType.EXPENSE, CategoryType.INCOME)

/** Lets UI tests scroll the list. */
const val CATEGORIES_LIST_TAG = "categories-list"

@Composable
fun CategoriesScreen(onClose: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var hiddenOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Категории", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 4.dp))
            IconButton(onClick = { viewModel.openNew() }) { Icon(Icons.Filled.Add, contentDescription = "Добавить категорию") }
        }
        SegmentedToggle(
            options = listOf(
                "Расходы · ${state.counts[CategoryType.EXPENSE] ?: 0}",
                "Доходы · ${state.counts[CategoryType.INCOME] ?: 0}",
            ),
            selected = TYPES.indexOf(state.type),
            onSelect = { viewModel.showType(TYPES[it]) },
            selectedColor = { if (TYPES[it] == CategoryType.INCOME) FinanceColors.Income else FinanceColors.Expense },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // An open category gets a card of its own; closed neighbours share one, as in the design.
        val sections = buildList<List<TopCategoryUi>> {
            var run = ArrayList<TopCategoryUi>()
            state.tops.forEach { top ->
                if (top.row.category.uid in expanded) {
                    if (run.isNotEmpty()) add(run)
                    add(listOf(top))
                    run = ArrayList()
                } else {
                    run += top
                }
            }
            if (run.isNotEmpty()) add(run)
        }

        LazyColumn(
            Modifier.fillMaxSize().testTag(CATEGORIES_LIST_TAG),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sections, key = { it.first().row.category.uid }) { section ->
                WhiteCard(Modifier.fillMaxWidth()) {
                    section.forEachIndexed { index, top ->
                        if (index > 0) HorizontalDivider(color = FinanceColors.Hairline)
                        val uid = top.row.category.uid
                        val isOpen = uid in expanded
                        TopRow(
                            top = top,
                            open = isOpen,
                            onToggle = { expanded = if (isOpen) expanded - uid else expanded + uid },
                            onEdit = { viewModel.openEdit(uid) },
                        )
                        if (isOpen) {
                            HorizontalDivider(color = FinanceColors.Hairline)
                            top.subs.forEach { sub -> SubRow(sub, top.row.category.uid) { viewModel.openEdit(sub.category.uid) } }
                            HorizontalDivider(color = FinanceColors.Hairline)
                            Row(
                                Modifier.fillMaxWidth().clickable { viewModel.openNew(parentUid = uid) }
                                    .heightIn(min = 44.dp).padding(start = 32.dp, end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = FinanceColors.Primary, modifier = Modifier.size(18.dp))
                                Text("Подкатегория в «${top.row.category.name}»", color = FinanceColors.Primary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            if (state.hidden.isNotEmpty()) {
                item(key = "hidden") {
                    WhiteCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { hiddenOpen = !hiddenOpen }
                                .heightIn(min = 52.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Скрытые · ${state.hidden.size}", color = FinanceColors.Muted, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Icon(
                                if (hiddenOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (hiddenOpen) "Свернуть" else "Развернуть",
                                tint = FinanceColors.Muted,
                            )
                        }
                        if (hiddenOpen) {
                            state.hidden.forEach { row ->
                                HorizontalDivider(color = FinanceColors.Hairline)
                                SubRow(row, row.category.parentUid ?: row.category.uid) { viewModel.openEdit(row.category.uid) }
                            }
                        }
                    }
                }
            }
        }
    }

    state.edit?.let { edit ->
        CategorySheet(
            edit = edit,
            parents = state.parents,
            all = state.all,
            onName = viewModel::setName,
            onIcon = viewModel::setIcon,
            onParent = viewModel::setParent,
            onMergeTarget = viewModel::setMergeTarget,
            onMerge = viewModel::merge,
            onHidden = viewModel::setHidden,
            onDelete = viewModel::delete,
            onSave = viewModel::save,
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
private fun TopRow(top: TopCategoryUi, open: Boolean, onToggle: () -> Unit, onEdit: () -> Unit) {
    val category = top.row.category
    val details = if (top.subs.isNotEmpty()) Dates.plural(top.subs.size, "подкатегория", "подкатегории", "подкатегорий")
    else Dates.operations(top.row.operations)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clickable(onClick = onEdit).heightIn(min = 60.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryAvatar(category.name, category.uid, size = 40.dp, icon = category.icon)
            Column {
                Text(category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(details, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
            }
        }
        // Expanding is only for categories with subcategories, or to add the first one.
        IconButton(onClick = onToggle) {
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Свернуть «${category.name}»" else "Развернуть «${category.name}»",
                tint = FinanceColors.Muted,
            )
        }
    }
}

@Composable
private fun SubRow(row: CategoryRow, colorKey: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(start = 32.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryAvatar(row.category.name, colorKey, size = 32.dp, icon = row.category.icon)
        Text(row.category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            MoneyFormatter.count(row.operations),
            style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
            color = FinanceColors.Muted,
        )
    }
}
