package com.familyfinance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.familyfinance.R

/** Palette of the approved design (light only for now). */
object FinanceColors {
    val Ground = Color(0xFFF2F3F7)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF111318)
    val Muted = Color(0xFF62677A)
    val Hairline = Color(0xFFE6E8EE)
    val Track = Color(0xFFE4E6EC)
    val ChipBorder = Color(0xFFD5D8E0)
    val Primary = Color(0xFF1A73E8)
    val PrimarySoft = Color(0xFFE7F0FD)
    val OnPrimarySoft = Color(0xFF1558B0)
    val OnPrimaryMuted = Color(0xFFDCE9FB)
    val Income = Color(0xFF12803B)
    val Expense = Color(0xFFD92D20)
    val Warning = Color(0xFFB25E00)
    val Weekend = Color(0xFFC42B1F)
}

/** Inter, one variable file for every weight (SIL Open Font License 1.1, rsms.me/inter). */
@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

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

private fun TextStyle.inter() = copy(fontFamily = Inter)

private val Type = Typography().run {
    Typography(
        displayLarge = displayLarge.inter(), displayMedium = displayMedium.inter(), displaySmall = displaySmall.inter(),
        headlineLarge = headlineLarge.inter(), headlineMedium = headlineMedium.inter(), headlineSmall = headlineSmall.inter(),
        titleLarge = titleLarge.inter(), titleMedium = titleMedium.inter(), titleSmall = titleSmall.inter(),
        bodyLarge = bodyLarge.inter(), bodyMedium = bodyMedium.inter(), bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter(), labelMedium = labelMedium.inter(), labelSmall = labelSmall.inter(),
    )
}

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}
