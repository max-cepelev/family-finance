package com.familyfinance.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familyfinance.domain.model.Category
import com.familyfinance.ui.common.BottomSheet
import com.familyfinance.ui.common.CategoryAvatar
import com.familyfinance.ui.common.ChoiceChip
import com.familyfinance.ui.common.ChoiceDialog
import com.familyfinance.ui.common.Dates
import com.familyfinance.ui.common.TextInputDialog
import com.familyfinance.ui.common.WhiteCard
import com.familyfinance.ui.theme.FinanceColors
import java.text.BreakIterator

/** Quick picks; any other emoji goes through "Своё эмодзи…". */
private val SUGGESTED = listOf(
    "🛒", "🍲", "🍎", "🥛", "☕", "🍫", "🍞", "🥩", "🐟", "🥚", "💧", "🥘", "🍕", "🍰",
    "🚌", "🚕", "⛽", "🏠", "💡", "📱", "💊", "🎁", "🎮", "🎬", "👕", "✂️", "📚", "🐶",
    "👶", "💳", "💰", "🏦", "✈️", "🏋️",
)

private const val EMOJI_PER_ROW = 7

private enum class SheetDialog { PARENT, MERGE, CONFIRM_MERGE, OWN_EMOJI }

/** Edit or create a category: emoji, name, parent, merge into another, hide, delete. */
@Composable
fun CategorySheet(
    edit: CategoryEditUi,
    parents: List<Category>,
    all: List<Category>,
    onName: (String) -> Unit,
    onIcon: (String?) -> Unit,
    onParent: (String?) -> Unit,
    onMergeTarget: (String?) -> Unit,
    onMerge: () -> Unit,
    onHidden: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var dialog by remember { mutableStateOf<SheetDialog?>(null) }
    val byUid = all.associateBy { it.uid }
    fun label(c: Category?): String {
        if (c == null) return ""
        val parent = c.parentUid?.let(byUid::get)
        val name = if (parent == null) c.name else "${parent.name} › ${c.name}"
        return listOfNotNull(c.icon, name).joinToString(" ")
    }
    val title = when {
        edit.isNew && edit.parentUid != null -> "Новая подкатегория"
        edit.isNew -> "Новая категория"
        edit.parentUid != null -> "Подкатегория"
        else -> "Категория"
    }

    BottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CategoryAvatar(edit.name.ifEmpty { "?" }, edit.parentUid ?: edit.uid, size = 56.dp, icon = edit.icon)
                // Plain rows of seven: a FlowRow with weighted items sent the UI tests into an endless layout loop.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SUGGESTED.chunked(EMOJI_PER_ROW).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { emoji ->
                                val on = emoji == edit.icon
                                Box(
                                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (on) FinanceColors.PrimarySoft else FinanceColors.Card)
                                        .border(if (on) 2.dp else 1.dp, if (on) FinanceColors.Primary else FinanceColors.Hairline, RoundedCornerShape(10.dp))
                                        .clickable(role = Role.Button) { onIcon(emoji) }
                                        .semantics { contentDescription = "Значок $emoji"; selected = on },
                                    contentAlignment = Alignment.Center,
                                ) { Text(emoji, fontSize = 19.sp) }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Своё эмодзи…", selected = false) { dialog = SheetDialog.OWN_EMOJI }
                ChoiceChip("Без значка", selected = edit.icon == null) { onIcon(null) }
            }

            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Название", style = MaterialTheme.typography.bodySmall, color = if (edit.nameError) FinanceColors.Expense else FinanceColors.Muted)
                    BasicTextField(
                        value = edit.name,
                        onValueChange = onName,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = FinanceColors.Ink),
                        cursorBrush = SolidColor(FinanceColors.Primary),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Название" },
                    )
                    if (edit.nameError) Text("Введите название", style = MaterialTheme.typography.bodySmall, color = FinanceColors.Expense)
                }
                // A category with subcategories stays on the top level.
                if (!edit.hasChildren) {
                    HorizontalDivider(color = FinanceColors.Hairline)
                    PickRow("Входит в", edit.parentUid?.let { label(byUid[it]) } ?: "Верхний уровень") { dialog = SheetDialog.PARENT }
                }
            }

            if (edit.canMerge) {
                WhiteCard(Modifier.fillMaxWidth()) {
                    PickRow("Объединить с", edit.mergeTargetUid?.let { label(byUid[it]) } ?: "Выберите категорию") { dialog = SheetDialog.MERGE }
                    if (edit.mergeTargetUid != null) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "${Dates.operations(edit.operations).replaceFirstChar { it.uppercase() }} перейдут в «${byUid[edit.mergeTargetUid]?.name}», а «${edit.name}» будет скрыта.",
                                style = MaterialTheme.typography.bodySmall,
                                color = FinanceColors.Muted,
                            )
                            OutlinedButton(onClick = { dialog = SheetDialog.CONFIRM_MERGE }, border = BorderStroke(1.dp, FinanceColors.Primary)) {
                                Text("Объединить", color = FinanceColors.Primary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            if (!edit.isNew) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { onHidden(!edit.archived) }) {
                        Text(if (edit.archived) "Показать снова" else "Скрыть", color = FinanceColors.Muted, fontWeight = FontWeight.Medium)
                    }
                    TextButton(onClick = onDelete, enabled = edit.canDelete) {
                        Text(if (edit.canDelete) "Удалить" else "Удалить — только пустую", color = if (edit.canDelete) FinanceColors.Expense else FinanceColors.ChipBorder)
                    }
                }
            }

            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = FinanceColors.Primary),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Сохранить", fontWeight = FontWeight.Medium) }
        }
    }

    when (dialog) {
        SheetDialog.PARENT -> ChoiceDialog(
            title = "Входит в",
            items = listOf("" to "Верхний уровень") + parents.filter { it.uid != edit.uid }.map { it.uid to label(it) },
            selected = edit.parentUid ?: "",
            onPick = { dialog = null; onParent(it.ifEmpty { null }) },
            onDismiss = { dialog = null },
        )
        SheetDialog.MERGE -> ChoiceDialog(
            title = "Объединить с",
            items = all.filter { it.uid != edit.uid }
                .sortedWith(compareBy({ it.parentUid?.let(byUid::get)?.sortOrder ?: it.sortOrder }, { it.parentUid != null }, { it.name }))
                .map { it.uid to label(it) },
            selected = edit.mergeTargetUid,
            onPick = { dialog = null; onMergeTarget(it) },
            onDismiss = { dialog = null },
        )
        SheetDialog.CONFIRM_MERGE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Объединить?") },
            text = { Text("${Dates.operations(edit.operations).replaceFirstChar { it.uppercase() }} перейдут в «${byUid[edit.mergeTargetUid]?.name}». Вернуть их обратно разом будет нельзя.") },
            confirmButton = { TextButton(onClick = { dialog = null; onMerge() }) { Text("Объединить") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
        SheetDialog.OWN_EMOJI -> TextInputDialog(
            title = "Своё эмодзи",
            label = "Эмодзи с клавиатуры",
            onConfirm = { text -> dialog = null; firstGrapheme(text.trim())?.let(onIcon) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** The first visible character, so "👨‍👩‍👧 семья" gives the whole family emoji, not a piece of it. */
private fun firstGrapheme(text: String): String? {
    if (text.isEmpty()) return null
    val it = BreakIterator.getCharacterInstance()
    it.setText(text)
    return text.substring(0, it.next())
}

@Composable
private fun PickRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = FinanceColors.Muted)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = FinanceColors.Muted, modifier = Modifier.size(24.dp))
    }
}
