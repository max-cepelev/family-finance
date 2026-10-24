package com.familyfinance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette of the approved design (light only for now). */
object FinanceColors {
    val Ground = Color(0xFFF6F7F4)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1B1F1C)
    val Muted = Color(0xFF5E665F)
    val Hairline = Color(0xFFE3E6E0)
    val Track = Color(0xFFE9ECE6)
    val ChipBorder = Color(0xFFC5CBC2)
    val Primary = Color(0xFF2E6B4F)
    val PrimarySoft = Color(0xFFDCEBE2)
    val OnPrimarySoft = Color(0xFF1F4D38)
    val OnPrimaryMuted = Color(0xFFCFE3D7)
    val Income = Color(0xFF1A63C4)
    val Expense = Color(0xFFC2382F)
    val Weekend = Color(0xFFB0352C)
}

private val Scheme = lightColorScheme(
    primary = FinanceColors.Primary,
    onPrimary = Color.White,
    primaryContainer = FinanceColors.PrimarySoft,
    onPrimaryContainer = FinanceColors.OnPrimarySoft,
    secondaryContainer = FinanceColors.PrimarySoft,
    onSecondaryContainer = FinanceColors.OnPrimarySoft,
    background = FinanceColors.Ground,
    onBackground = FinanceColors.Ink,
    surface = FinanceColors.Ground,
    onSurface = FinanceColors.Ink,
    onSurfaceVariant = FinanceColors.Muted,
    surfaceContainer = FinanceColors.Card,
    surfaceContainerHigh = FinanceColors.Card,
    surfaceContainerHighest = FinanceColors.Track,
    outline = FinanceColors.ChipBorder,
    outlineVariant = FinanceColors.Hairline,
    error = FinanceColors.Expense,
)

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
