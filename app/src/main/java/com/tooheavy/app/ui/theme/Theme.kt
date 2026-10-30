package com.tooheavy.app.ui.theme

import android.graphics.Typeface
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

// ---------- brand ----------

/** Primary brand violet. Containers and buttons (white text clears 4:1). */
val Accent = Color(0xFF7B68FF)
/** Lighter violet for lines, icons and text on the dark background. */
val AccentBright = Color(0xFFA597FF)
val Ember = Color(0xFFFF6A3D)
val Magenta = Color(0xFFD54BFF)

val Success = Color(0xFF2FD38A)
val Danger = Color(0xFFFF5468)
val Warmup = Color(0xFFFFB020)

/** Brand gradient: violet → magenta → ember. Used for hero numbers, primary actions, rings and the icon. */
val BrandColors = listOf(Color(0xFF7B5CFF), Magenta, Ember)
val BrandBrush: Brush get() = Brush.linearGradient(BrandColors)
fun brandHorizontal(): Brush = Brush.horizontalGradient(BrandColors)

// ---------- surfaces ----------

val Ink = Color(0xFF09090F)
val Surface1 = Color(0xFF14141E)
val Surface2 = Color(0xFF1B1B28)
val Surface3 = Color(0xFF242434)
val Hairline = Color(0xFF2B2B3D)
val TextPrimary = Color(0xFFF3F2FA)
val TextSecondary = Color(0xFFA3A2B8)
val TextMuted = Color(0xFF6F6E86)

// ---------- charts (validated for CVD separation on Surface1; see dataviz validator) ----------

object ChartColors {
    /** Categorical order. Assign in this order, never cycled. */
    val series = listOf(Color(0xFF8B7BFF), Color(0xFFE8622A), Color(0xFF14A88B), Color(0xFFBF8A14), Color(0xFFDC4F95))
    val Protein = Color(0xFF3D8BFF)
    val Carbs = Color(0xFFE8622A)
    val Fat = Color(0xFF14A88B)
    val Grid = Color(0xFF242434)
    val Axis = TextMuted

    /** Single-hue violet ramp for heatmaps: index 0 = nothing, 4 = most. */
    val heat = listOf(Color(0xFF1D1D2B), Color(0xFF2F2A63), Color(0xFF4A3FA3), Color(0xFF6D5CE6), Color(0xFFA597FF))
}

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2163),
    onPrimaryContainer = Color(0xFFEDEAFF),
    secondary = Ember,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E2766),
    onSecondaryContainer = Color(0xFFE9E5FF),
    tertiary = Success,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Color(0xFF101018),
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    surfaceBright = Surface3,
    inverseSurface = TextPrimary,
    inverseOnSurface = Ink,
    outline = Hairline,
    outlineVariant = Color(0xFF20202E),
    error = Danger,
    onError = Color.White,
    scrim = Color(0xCC000000),
)

/** Condensed face for big numbers: sporty and space-efficient. */
val NumberFont: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

private val base = Typography()
private val typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = NumberFont, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontFamily = NumberFont, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displaySmall = base.displaySmall.copy(fontFamily = NumberFont, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

/** Uppercase overline above section titles and stat labels. */
val Overline = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

/** Big stat numbers inside cards. */
val StatNumber = TextStyle(fontFamily = NumberFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TooHeavyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
