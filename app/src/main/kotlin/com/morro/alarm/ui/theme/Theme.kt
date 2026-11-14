package com.morro.alarm.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Morro Design Tokens — Premium Dark AMOLED Identity
 *
 * Palette locked per Manifesto:
 * - Canvas: #000000 (pure black)
 * - Surface: #12121A (elevated surface)
 * - SurfaceVariant: #0A0A0F (warm black)
 * - SurfaceLow: #0A0A0F (subtle variant)
 * - TextPrimary: #FFFFFF (white)
 * - TextSecondary: #B3FFFFFF (white at 70%)
 * - TextDisabled: #80FFFFFF (white at 50%)
 * - AccentTactical: #FF4500 (tactical orange — primary action, alarm emphasis, focus/CTA)
 * - AccentBubbly: #FF6B9D (bubbly pink — GLOW ONLY, live/active/recording states)
 * - BorderSubtle: #1FFFFFFF (very subtle white divider)
 * - BorderActive: #FF4500 (focus/armed — matches Tactical Orange)
 * - Success: #2E7D32 (positive/armed)
 * - Error: #2E7D32 (kept non-alarming; not red)
 *
 * CRITICAL RULES:
 * - NO MaterialTheme color slots. Morro owns tokens via CompositionLocal.
 * - NO light/cream theme.
 * - AccentBubbly (#FF6B9D) is GLOW ONLY — never body text, never icons, never semantic.
 * - AccentTactical (#FF4500) for primary action, alarm emphasis, focus rings, CTA fills.
 * - Body text stays white/white-alpha. Never use pink or orange for long body text.
 */

// ── Geometry ──────────────────────────────────────────────────────────────
object MorroShapes {
    val Card = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val Button = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val Chip = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val Sheet = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val FocusRing = 2.dp // Additional radius for concentric focus ring
}

// ── Colors ────────────────────────────────────────────────────────────────
object MorroColors {
    // Canvas & surface layers
    val Canvas        = Color(0xFF000000) // Root background — pure black
    val Surface       = Color(0xFF0A0A0F) // Cards, panels — midnight blue squarcle
    val SurfaceVariant = Color(0xFF0A0A0F) // Warm black
    val SurfaceLow    = Color(0xFF12121A) // Raised tiles, pills, drag handles

    // Typography colors
    val TextPrimary   = Color(0xFFFFFFFF) // White — headlines, primary labels
    val TextSecondary = Color(0xFFB3FFFFFF) // White at 70% — body text
    val TextDisabled  = Color(0xFF80FFFFFF) // White at 50% — disabled only
    val TextTertiary  = Color(0xFF80FFFFFF) // White at 50% — tertiary text, hints

    // Accent system
    val AccentTactical   = Color(0xFFFF4500) // Tactical Orange — CTA, focus, icons ≥24sp, alarm emphasis
    val AccentBubbly     = Color(0xFFFF6B9D) // Bubbly Pink — GLOW ONLY (waveform, burst, shimmer, live states)
    val AccentBubblyDeep = Color(0xFF9C2D5E) // Deep pink — decorative waveform/depth only, not text or semantic
    val WarmYellow       = Color(0xFFFFC94A) // Warm yellow — armed / positive states only

    // Borders
    val BorderSubtle  = Color(0xFF1FFFFFFF) // Very subtle white divider
    val BorderActive  = Color(0xFFFF4500) // Focus/armed borders — matches Tactical Orange

    // Status
    val Success = Color(0xFF2E7D32) // Positive / armed
    val Error   = Color(0xFFC62828) // Destructive / stop actions only

    // Legacy aliases for gradual migration (DO NOT USE IN NEW CODE)
    @Suppress("DEPRECATION")
    @Deprecated("Use MorroColors.Canvas", ReplaceWith("MorroColors.Canvas"))
    val CanvasRoot = Canvas
    @Deprecated("Use MorroColors.Surface", ReplaceWith("MorroColors.Surface"))
    val SurfaceDark = Surface
    @Deprecated("Use MorroColors.TextPrimary", ReplaceWith("MorroColors.TextPrimary"))
    val TextPrimaryDark = TextPrimary
    @Deprecated("Use MorroColors.AccentTactical", ReplaceWith("MorroColors.AccentTactical"))
    val AccentPrimary = AccentTactical
    @Deprecated("Use MorroColors.BorderActive", ReplaceWith("MorroColors.BorderActive"))
    val Border = BorderActive
}

// ── Font families ─────────────────────────────────────────────────────────
// System font fallbacks used because the app is 100% offline.
// Swap to FontFamily(Font(...)) when TTF assets are bundled — token names stay stable.
private val FontDisplay = FontFamily.SansSerif // → Space Grotesk
private val FontMono    = FontFamily.Monospace  // → JetBrains Mono
private val FontBody    = FontFamily.SansSerif  // → system UI

// ── Typography Tokens ─────────────────────────────────────────────────────
object MorroTypography {
    // Display / Hero
    val DisplayHero = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.04f).sp
    )

    val DisplayLarge = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.03f).sp
    )

    // Headlines
    val HeadlineLarge = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.02f).sp
    )

    val HeadlineMedium = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.01f).sp
    )

    val HeadlineSmall = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )

    // Titles
    val TitleMedium = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )

    // Body
    val BodyLarge = TextStyle(
        fontFamily = FontBody,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.02f.sp
    )

    val BodyMedium = TextStyle(
        fontFamily = FontBody,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.04f.sp
    )

    // Labels / Badges (mono, caps, 0.12em tracking)
    val LabelLarge = TextStyle(
        fontFamily = FontMono,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.12f.sp
    )

    val LabelSmall = TextStyle(
        fontFamily = FontMono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.08f.sp
    )
}

// ── Material3 Typography mapping (for components that still use MaterialTheme.typography) ───────────
internal val MaterialTypography = androidx.compose.material3.Typography(
    headlineLarge  = MorroTypography.HeadlineLarge,
    headlineMedium = MorroTypography.HeadlineMedium,
    headlineSmall  = MorroTypography.HeadlineSmall,
    bodyLarge      = MorroTypography.BodyLarge,
    bodyMedium     = MorroTypography.BodyMedium,
    displayLarge   = MorroTypography.DisplayHero,
    displayMedium  = MorroTypography.DisplayLarge,
    titleMedium    = MorroTypography.TitleMedium,
    labelLarge     = MorroTypography.LabelLarge,
    labelSmall     = MorroTypography.LabelSmall,
)

// ── Material3 ColorScheme mapping ─────────────────────────────────────────
internal val MaterialColorScheme = androidx.compose.material3.darkColorScheme(
    background      = MorroColors.Canvas,
    surface         = MorroColors.Surface,
    surfaceVariant  = MorroColors.SurfaceVariant,
    onBackground    = MorroColors.TextPrimary,
    onSurface       = MorroColors.TextPrimary,
    onSurfaceVariant = MorroColors.TextSecondary,
    primary         = MorroColors.AccentTactical,
    onPrimary       = Color.Black,
    primaryContainer = MorroColors.SurfaceVariant,
    onPrimaryContainer = MorroColors.TextPrimary,
    secondary       = MorroColors.AccentBubbly,
    onSecondary     = Color.Black,
    secondaryContainer = MorroColors.SurfaceLow,
    onSecondaryContainer = MorroColors.TextPrimary,
    tertiary        = MorroColors.AccentBubbly,
    onTertiary      = Color.Black,
    error           = MorroColors.Error,
    onError         = Color.White,
    outline         = MorroColors.BorderSubtle,
    outlineVariant  = MorroColors.BorderSubtle,
    inverseSurface  = MorroColors.TextPrimary,
    inverseOnSurface = MorroColors.Canvas,
    inversePrimary  = MorroColors.AccentTactical,
    surfaceTint     = MorroColors.AccentTactical,
)

// ── Spring Specs ──────────────────────────────────────────────────────────
object MorroSpring {
    val FloatSpringDefault: androidx.compose.animation.core.SpringSpec<Float> = androidx.compose.animation.core.spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    val FloatSpringPress: androidx.compose.animation.core.SpringSpec<Float> = androidx.compose.animation.core.spring<Float>(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val FloatSpringMomentum: androidx.compose.animation.core.SpringSpec<Float> = androidx.compose.animation.core.spring<Float>(
        dampingRatio = 0.82f,
        stiffness = 1500f
    )
}

// ── CompositionLocal Providers ────────────────────────────────────────────
object MorroCompositionLocals {
    // Colors
    val LocalMorroColors = staticCompositionLocalOf { MorroColorPalette() }

    // Typography
    val LocalMorroTypography = staticCompositionLocalOf { MorroTextPalette() }

    // Shapes
    val LocalMorroShapes = staticCompositionLocalOf { MorroShapes }
}

/**
 * Color palette data class for CompositionLocal
 */
data class MorroColorPalette(
    val canvas: Color = MorroColors.Canvas,
    val surface: Color = MorroColors.Surface,
    val surfaceVariant: Color = MorroColors.SurfaceVariant,
    val surfaceLow: Color = MorroColors.SurfaceLow,
    val textPrimary: Color = MorroColors.TextPrimary,
    val textSecondary: Color = MorroColors.TextSecondary,
    val textDisabled: Color = MorroColors.TextDisabled,
    val textTertiary: Color = MorroColors.TextTertiary,
    val accentTactical: Color = MorroColors.AccentTactical,
    val accentBubbly: Color = MorroColors.AccentBubbly,
    val accentBubblyDeep: Color = MorroColors.AccentBubblyDeep,
    val warmYellow: Color = MorroColors.WarmYellow,
    val borderSubtle: Color = MorroColors.BorderSubtle,
    val borderActive: Color = MorroColors.BorderActive,
    val success: Color = MorroColors.Success,
    val error: Color = MorroColors.Error,
)

/**
 * Typography palette data class for CompositionLocal
 */
data class MorroTextPalette(
    val displayHero: TextStyle = MorroTypography.DisplayHero,
    val displayLarge: TextStyle = MorroTypography.DisplayLarge,
    val headlineLarge: TextStyle = MorroTypography.HeadlineLarge,
    val headlineMedium: TextStyle = MorroTypography.HeadlineMedium,
    val headlineSmall: TextStyle = MorroTypography.HeadlineSmall,
    val titleMedium: TextStyle = MorroTypography.TitleMedium,
    val bodyLarge: TextStyle = MorroTypography.BodyLarge,
    val bodyMedium: TextStyle = MorroTypography.BodyMedium,
    val labelLarge: TextStyle = MorroTypography.LabelLarge,
    val labelSmall: TextStyle = MorroTypography.LabelSmall,
)

// ── Spring Helpers ────────────────────────────────────────────────────────
@Composable
fun rememberMorroSpring(
    initialValue: Float,
    spec: androidx.compose.animation.core.SpringSpec<Float> = MorroSpring.FloatSpringDefault
): androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    return remember { androidx.compose.animation.core.Animatable(initialValue) }
}

@Composable
fun springToColor(spring: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, from: Color, to: Color): Color {
    val t = spring.value.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha + (to.alpha - from.alpha) * t,
        colorSpace = from.colorSpace
    )
}

@Composable
fun springToAlpha(spring: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, from: Float, to: Float): Float {
    return from + (to - from) * spring.value.coerceIn(0f, 1f)
}

@Composable
fun springToScale(spring: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, from: Float, to: Float): Float {
    return from + (to - from) * spring.value.coerceIn(0f, 1f)
}

@Composable
fun springToDp(spring: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, from: androidx.compose.ui.unit.Dp, to: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
    val t = spring.value.coerceIn(0f, 1f)
    return androidx.compose.ui.unit.Dp(from.value + (to.value - from.value) * t)
}

@Composable
fun derivedSpringValue(spring: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, calc: (Float) -> androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
    return calc(spring.value.coerceIn(0f, 1f))
}

// ── MorroTokens (Legacy Compatibility) ─────────────────────────────────────
/**
 * MorroTokens — Legacy compatibility object for code referencing the old token structure.
 * Maps to the new MorroColors/MorroTypography/MorroShapes system.
 */
object MorroTokens {
    val CanvasRoot: Color = MorroColors.Canvas
    val buttonShape = MorroShapes.Button
}

// ── Extension Accessors ───────────────────────────────────────────────────
@Composable
fun morroColors(): MorroColorPalette {
    return MorroCompositionLocals.LocalMorroColors.current
}

@Composable
fun morroTypography(): MorroTextPalette {
    return MorroCompositionLocals.LocalMorroTypography.current
}

@Composable
fun morroShapes(): MorroShapes {
    return MorroCompositionLocals.LocalMorroShapes.current
}

/**
 * MorroTheme — applies the full premium dark AMOLED design system via CompositionLocal.
 * This REPLACES MaterialTheme entirely for colors/typography/shapes.
 */
@Composable
fun MorroTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        MorroCompositionLocals.LocalMorroColors provides MorroColorPalette(),
        MorroCompositionLocals.LocalMorroTypography provides MorroTextPalette(),
        MorroCompositionLocals.LocalMorroShapes provides MorroShapes
    ) {
        // Provide MaterialTheme with our color scheme for components that still use it
        MaterialTheme(
            colorScheme = MaterialColorScheme,
            typography = MaterialTypography,
            shapes = androidx.compose.material3.Shapes(
                small = MorroShapes.Chip,
                medium = MorroShapes.Button,
                large = MorroShapes.Card,
                extraLarge = MorroShapes.Card,
                extraSmall = MorroShapes.Chip
            ),
            content = content
        )
    }
}