package com.familyfinance.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyfinance.ui.theme.FinanceColors

/** Lets UI tests scroll the list of a [ChoiceDialog]. */
const val CHOICE_LIST_TAG = "choice-list"

/** A list to pick one from; [extra] adds an action row at the end ("Новая группа…"). */
@Composable
fun ChoiceDialog(
    title: String,
    items: List<Pair<String, String>>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    extra: Pair<String, () -> Unit>? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        containerColor = FinanceColors.Card,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.testTag(CHOICE_LIST_TAG)) {
                items(items, key = { it.first }) { (key, label) ->
                    ListItem(
                        modifier = Modifier.clickable { onPick(key) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(label) },
                        trailingContent = if (key == selected) ({ Icon(Icons.Filled.Check, null, tint = FinanceColors.Primary) }) else null,
                    )
                }
                if (extra != null) {
                    item {
                        ListItem(
                            modifier = Modifier.clickable(onClick = extra.second),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Icon(Icons.Filled.Add, null, tint = FinanceColors.Primary) },
                            headlineContent = { Text(extra.first, color = FinanceColors.Primary) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    note: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FinanceColors.Card,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
