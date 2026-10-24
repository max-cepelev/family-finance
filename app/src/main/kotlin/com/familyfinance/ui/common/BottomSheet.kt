package com.familyfinance.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.familyfinance.ui.theme.FinanceColors

/** One sheet shown by [SheetHost]. */
class SheetEntry internal constructor(
    internal val onDismiss: () -> Unit,
    internal val content: @Composable ColumnScope.() -> Unit,
)

/** Holds the sheet on screen; there is at most one at a time. */
class SheetHostState {
    internal var entry by mutableStateOf<SheetEntry?>(null)
}

val LocalSheetHost = staticCompositionLocalOf<SheetHostState> { error("BottomSheet needs a SheetHost above it") }

/**
 * Draws [BottomSheet]s over [content] in the same window. A sheet in its own dialog window (Material's
 * ModalBottomSheet, or a full-width Dialog) left the UI tests hanging now and then; a layer does not.
 */
@Composable
fun SheetHost(content: @Composable BoxScope.() -> Unit) {
    val state = remember { SheetHostState() }
    CompositionLocalProvider(LocalSheetHost provides state) {
        Box(Modifier.fillMaxSize()) {
            content()
            state.entry?.let { sheet -> SheetLayer(sheet) }
        }
    }
}

@Composable
private fun SheetLayer(sheet: SheetEntry) {
    BackHandler(onBack = sheet.onDismiss)
    Box(
        Modifier.fillMaxSize()
            .background(Color(0x6B1B1F1C))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = sheet.onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(FinanceColors.Ground)
                // Taps inside the panel must not reach the scrim behind it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp)
                    .width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(FinanceColors.ChipBorder),
            )
            sheet.content(this)
        }
    }
}

/** A panel at the bottom over a dimmed screen; tap outside or Back closes it. Needs a [SheetHost] above. */
@Composable
fun BottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val host = LocalSheetHost.current
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentContent by rememberUpdatedState(content)
    val entry = remember { SheetEntry(onDismiss = { currentDismiss() }, content = { currentContent() }) }
    DisposableEffect(entry) {
        host.entry = entry
        onDispose { if (host.entry === entry) host.entry = null }
    }
}
