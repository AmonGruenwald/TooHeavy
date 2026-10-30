package com.tooheavy.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- brand: warm black, ivory and a single champagne-gold accent ----------

/** Champagne gold: primary buttons (with dark text), selection, the single highlighted mark in a chart. */
val Accent = Color(0xFFC9A66B)
/** Pale champagne for gold text and icons on the dark background. */
val AccentBright = Color(0xFFE4CF9F)
/** Antique bronze, the secondary warm tone (streaks, burn, today markers). */
val Ember = Color(0xFFB08458)
/** Text and icons on gold. */
val OnAccent = Color(0xFF17130C)

val Success = Color(0xFF8DB596)
val Danger = Color(0xFFD27766)
val Warmup = Color(0xFFE09A5F)

/** Metallic gold, deep to pale. Used sparingly: the progress ring, the highlighted bar, gold numerals. */
val BrandColors = listOf(Color(0xFF9C7A45), Color(0xFFD2B47E), Color(0xFFEFE0BC))
val BrandBrush: Brush get() = Brush.linearGradient(BrandColors)
fun brandHorizontal(): Brush = Brush.horizontalGradient(BrandColors)

// ---------- surfaces ----------

val Ink = Color(0xFF0C0B0A)
val Surface1 = Color(0xFF151412)
val Surface2 = Color(0xFF1B1A17)
val Surface3 = Color(0xFF26241F)
val Hairline = Color(0xFF2F2C27)
val TextPrimary = Color(0xFFF3EEE5)
val TextSecondary = Color(0xFFA9A398)
val TextMuted = Color(0xFF706B62)

// ---------- charts (validated for colorblind separation on Surface1; see dataviz validator) ----------

object ChartColors {
    /** Categorical order for multi-series charts. Assign in this order, never cycled. */
    val series = listOf(Color(0xFFB88A3A), Color(0xFF5A7FD0), Color(0xFF2F9E7E), Color(0xFFC0664A), Color(0xFF9A66B4))
    val Protein = Color(0xFF5A7FD0)
    val Carbs = Color(0xFFB88A3A)
    val Fat = Color(0xFF2F9E7E)
    val Water = Color(0xFF4F93B8)
    val Grid = Color(0xFF23211D)
    val Axis = TextMuted
    /** De-emphasized bars: everything except the one mark the chart is about. */
    val Muted = Color(0xFF4A463F)

    /** Single-hue gold ramp for heatmaps: index 0 = nothing, 4 = most. */
    val heat = listOf(Color(0xFF1E1C19), Color(0xFF3A3224), Color(0xFF66543A), Color(0xFF9E8257), Color(0xFFD9BD86))
}

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = Color(0xFF231E16),
    onPrimaryContainer = AccentBright,
    secondary = Ember,
    onSecondary = OnAccent,
    secondaryContainer = Color(0xFF2E281D),
    onSecondaryContainer = AccentBright,
    tertiary = Success,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Color(0xFF11100E),
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    surfaceBright = Surface3,
    inverseSurface = TextPrimary,
    inverseOnSurface = Ink,
    outline = Hairline,
    outlineVariant = Color(0xFF221F1B),
    error = Danger,
    onError = OnAccent,
    scrim = Color(0xCC000000),
)

/** Serif for headlines and figures: quiet, editorial, a little luxurious. */
val DisplayFont: FontFamily = FontFamily.Serif
val NumberFont: FontFamily = FontFamily.Serif

private val base = Typography()
private val typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
    displayMedium = base.displayMedium.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Normal),
    headlineLarge = base.headlineLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Normal, letterSpacing = (-0.3).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Normal),
    titleLarge = base.titleLarge.copy(fontFamily = DisplayFont, fontWeight = FontWeight.Normal),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

/** Small spaced capitals above section titles and stat labels. */
val Overline = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)

/** Figures inside cards. */
val StatNumber = TextStyle(fontFamily = NumberFont, fontWeight = FontWeight.Normal, fontSize = 28.sp, letterSpacing = (-0.3).sp)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun TooHeavyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
