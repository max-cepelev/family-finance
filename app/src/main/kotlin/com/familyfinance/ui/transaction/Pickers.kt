package com.familyfinance.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.familyfinance.ui.common.CategoryAvatar
import com.familyfinance.ui.theme.FinanceColors
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
    PickerDialog("Счёт", onDismiss) {
        if (accounts.isEmpty()) {
            Text("Счетов нет. Импортируйте бэкап Money Manager во вкладке «Ещё».", modifier = Modifier.padding(horizontal = 8.dp))
        } else {
            LazyColumn {
                items(accounts, key = { it.uid }) { account ->
                    PickerRow(
                        leading = {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(if (account.isCredit) FinanceColors.Expense else FinanceColors.Primary), contentAlignment = Alignment.Center) {
                                Icon(if (account.isCredit) Icons.Outlined.CreditCard else Icons.Outlined.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                        },
                        label = account.name,
                        note = account.currencyCode,
                        checked = account.uid == selected,
                    ) { onPick(account.uid) }
                }
            }
        }
    }
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

    PickerDialog("Категория", onDismiss) {
        LazyColumn {
            item {
                PickerRow(
                    leading = {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(FinanceColors.Track), contentAlignment = Alignment.Center) {
                            Text("?", color = FinanceColors.Muted, fontWeight = FontWeight.Bold)
                        }
                    },
                    label = "Без категории",
                    checked = selected == null,
                ) { onPick(null) }
            }
            top.forEach { category ->
                val subs = children[category.uid].orEmpty()
                item(key = category.uid) {
                    val isOpen = expanded == category.uid
                    PickerRow(
                        leading = { CategoryAvatar(category.name, category.uid, size = 30.dp, icon = category.icon) },
                        label = category.name,
                        checked = subs.isEmpty() && category.uid == selected,
                        trailing = if (subs.isEmpty()) null else ({
                            Icon(if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = FinanceColors.Muted)
                        }),
                    ) {
                        if (subs.isEmpty()) onPick(category.uid) else expanded = if (isOpen) null else category.uid
                    }
                }
                if (expanded == category.uid) {
                    item(key = category.uid + ":self") {
                        PickerRow(
                            leading = { CategoryAvatar(category.name, category.uid, size = 24.dp, icon = category.icon) },
                            label = "Без подкатегории",
                            checked = category.uid == selected,
                            indent = true,
                        ) { onPick(category.uid) }
                    }
                    items(subs, key = { it.uid }) { sub ->
                        PickerRow(
                            leading = { CategoryAvatar(sub.name, sub.uid, size = 24.dp, icon = sub.icon ?: category.icon) },
                            label = sub.name,
                            checked = sub.uid == selected,
                            indent = true,
                        ) { onPick(sub.uid) }
                    }
                }
            }
        }
    }
}

/** A picker in the app's look: a white card with a bold title and a list. */
@Composable
private fun PickerDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", fontWeight = FontWeight.SemiBold) } },
        containerColor = FinanceColors.Card,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = content,
    )
}

/** One 48dp line: a tile, the name with an optional note, a check or a chevron; the chosen one tinted. */
@Composable
private fun PickerRow(
    leading: @Composable () -> Unit,
    label: String,
    checked: Boolean,
    note: String? = null,
    indent: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(start = if (indent) 22.dp else 0.dp, top = 1.dp, bottom = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) FinanceColors.PrimarySoft else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .semantics { selected = checked },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = FinanceColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
        }
        when {
            trailing != null -> trailing()
            checked -> Icon(Icons.Filled.Check, contentDescription = null, tint = FinanceColors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}
