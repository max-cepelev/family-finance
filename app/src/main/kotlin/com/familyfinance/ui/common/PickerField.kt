package com.familyfinance.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Looks like a text field, opens a picker on tap. */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // A disabled field does not swallow clicks, so the clickable modifier receives them.
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        enabled = false,
        readOnly = true,
        singleLine = true,
        isError = isError,
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = scheme.onSurface,
            disabledBorderColor = if (isError) scheme.error else scheme.outline,
            disabledLabelColor = if (isError) scheme.error else scheme.onSurfaceVariant,
            disabledTrailingIconColor = scheme.onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
