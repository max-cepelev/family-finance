package com.familyfinance.ui.transaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.Category

@Composable
fun AccountPickerDialog(
    accounts: List<Account>,
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Счёт") },
        text = {
            if (accounts.isEmpty()) {
                Text("Счетов нет. Импортируйте бэкап Money Manager во вкладке «Ещё».")
            } else {
                LazyColumn {
                    items(accounts, key = { it.uid }) { account ->
                        ListItem(
                            modifier = Modifier.clickable { onPick(account.uid) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(account.name) },
                            supportingContent = { Text(account.currencyCode) },
                            trailingContent = if (account.uid == selected) ({ Icon(Icons.Filled.Check, null) }) else null,
                        )
                    }
                }
            }
        },
    )
}

/**
 * Two levels like Money Manager: tapping a category with subcategories unfolds them,
 * where the first entry picks the parent itself.
 */
@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val children = remember(categories) {
        categories.filter { it.parentUid != null }.groupBy { it.parentUid }
            .mapValues { (_, list) -> list.sortedBy { it.sortOrder } }
    }
    val uids = remember(categories) { categories.map { it.uid }.toSet() }
    // A subcategory whose parent is not offered (e.g. archived) is shown on the top level.
    val top = remember(categories) {
        categories.filter { it.parentUid == null || it.parentUid !in uids }.sortedBy { it.sortOrder }
    }
    val selectedParent = categories.firstOrNull { it.uid == selected }?.parentUid
    var expanded by remember { mutableStateOf(selectedParent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        title = { Text("Категория") },
        text = {
            LazyColumn {
                item {
                    PickerRow("Без категории", selected == null) { onPick(null) }
                }
                top.forEach { category ->
                    val subs = children[category.uid].orEmpty()
                    item(key = category.uid) {
                        val isOpen = expanded == category.uid
                        ListItem(
                            modifier = Modifier.clickable {
                                if (subs.isEmpty()) onPick(category.uid)
                                else expanded = if (isOpen) null else category.uid
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(listOfNotNull(category.icon, category.name).joinToString(" ")) },
                            trailingContent = when {
                                subs.isNotEmpty() -> ({ Icon(if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) })
                                category.uid == selected -> ({ Icon(Icons.Filled.Check, null) })
                                else -> null
                            },
                        )
                    }
                    if (expanded == category.uid) {
                        item(key = category.uid + ":self") {
                            PickerRow("${category.name} (без подкатегории)", category.uid == selected, indent = true) {
                                onPick(category.uid)
                            }
                        }
                        items(subs, key = { it.uid }) { sub ->
                            PickerRow(listOfNotNull(sub.icon, sub.name).joinToString(" "), sub.uid == selected, indent = true) { onPick(sub.uid) }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun PickerRow(label: String, checked: Boolean, indent: Boolean = false, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(start = if (indent) 24.dp else 0.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(label) },
        trailingContent = if (checked) ({ Icon(Icons.Filled.Check, null) }) else null,
    )
}
