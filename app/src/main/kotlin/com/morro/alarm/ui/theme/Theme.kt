package com.morro.alarm.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Morro Design Tokens — one source of truth.
 *
 * The identity is a pure-AMOLED stage: #000000 everywhere, near-black panels a single step up
 * from it, and a single warm signal — tactical orange (#FF5500) bleeding into bubbly pink
 * (#FF66A3) — used for progress, active borders, the second hand and every primary action.
 *
 * Rules that survive every screen:
 *  - Canvas is #000000. No cream, no light theme, no navy cards.
 *  - Surfaces separate by a small step in tone, never by a heavy shadow.
 *  - Orange -> pink is the only gradient. It is a signature, not decoration, so it appears on
 *    progress, selected states and the primary action.
 *  - Text is white, #A0A0AB (secondary) or #6B6B76 (tertiary labels only, never body copy).
 *  - Interactive corners are squarcles: cards 20dp, buttons 16dp, pills fully round.
 */

// ── Geometry ──────────────────────────────────────────────────────────────
object MorroShapes {
    val Card = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    val Button = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val Chip = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val Pill = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val Sheet = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val FocusRing = 2.dp
}

// ── Colors ────────────────────────────────────────────────────────────────
object MorroColors {
    // Canvas & surface layers (steps, not shadows)
    val Canvas          = Color(0xFF000000) // root background — pure AMOLED black
    val Surface         = Color(0xFF07070B) // sheets, large panels
    val SurfaceElevated = Color(0xFF0D0D12) // bento cards, answer cards
    val SurfaceCard     = Color(0xFF09090D) // rows, quiet containers
    val SurfaceLow      = Color(0xFF12121A) // legacy raised tile (pills, handles)
    val SurfaceVariant  = SurfaceElevated

    // Typography
    val TextPrimary   = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA0A0AB)
    val TextTertiary  = Color(0xFF6B6B76)
    val TextDisabled  = Color(0xFF6B6B76)

    // The warm signal: orange -> pink
    val AccentTactical   = Color(0xFFFF5500) // primary action, focus, active states
    val AccentBubbly     = Color(0xFFFF66A3) // secondary accent, glow, live states
    val AccentBubblySoft = Color(0xFFFF85B3)
    val AccentBubblyDeep = Color(0xFFFF5E9E)
    val WarmYellow       = Color(0xFFFFC94A) // armed / positive status only

    // Borders
    val BorderSubtle  = Color(0x1FFFFFFF) // white at 12%
    val BorderStrong  = Color(0x33FFFFFF) // white at 20%
    val BorderActive  = AccentTactical

    // Status
    val Success = Color(0xFF2E7D32)
    val Error   = Color(0xFFFF4D5E)

    // Legacy aliases (kept so older call sites compile)
    val CanvasRoot = Canvas
    val SurfaceDark = Surface
    val TextPrimaryDark = TextPrimary
    val AccentPrimary = AccentTactical
    val Border = BorderActive
}

/** The signature ribbon gradient. One gradient, used with intent. */
object MorroGradients {
    val Ribbon: List<Color> = listOf(MorroColors.AccentTactical, MorroColors.AccentBubbly)

    /** Horizontal orange -> pink, for progress fills, borders and the primary action. */
    fun horizontal(alpha: Float = 1f): Brush = Brush.horizontalGradient(
        colors = listOf(
            MorroColors.AccentTactical.copy(alpha = alpha),
            MorroColors.AccentBubbly.copy(alpha = alpha)
        )
    )

    /** Diagonal variant, for ambient light ribbons sweeping across the black canvas. */
    fun diagonal(alpha: Float = 1f): Brush = Brush.linearGradient(
        colors = listOf(
            MorroColors.AccentTactical.copy(alpha = alpha),
            MorroColors.AccentBubblySoft.copy(alpha = alpha * 0.9f),
            MorroColors.AccentBubbly.copy(alpha = alpha * 0.6f)
        )
    )

    /** Soft text/icon wash so pink accents never sit flat on black. */
    fun glow(alpha: Float = 0.22f): Brush = Brush.radialGradient(
        colors = listOf(
            MorroColors.AccentBubbly.copy(alpha = alpha),
            MorroColors.AccentTactical.copy(alpha = alpha * 0.45f),
            Color.Transparent
        )
    )
}

/**
 * Draws a 1dp orange -> pink border inside the component's bounds.
 *
 * A modifier rather than a `border(...)` call because Compose's border only takes a solid colour.
 * Drawn inside the clip so the radius matches the surface exactly; no ring ever escapes the card.
 */
fun Modifier.morroGradientBorder(
    shape: Shape,
    width: Dp = 1.dp,
    brush: Brush = MorroGradients.horizontal(),
): Modifier = this.drawBehind {
    val radius = cornerRadiusFor(shape, size, this)
    drawRoundRect(
        brush = brush,
        cornerRadius = radius,
        style = Stroke(width = width.toPx())
    )
}

/** Reads a rounded shape's corner size so the border follows the same squarcle as the clip. */
private fun cornerRadiusFor(
    shape: Shape,
    size: Size,
    density: androidx.compose.ui.unit.Density,
): CornerRadius {
    val rounded = shape as? androidx.compose.foundation.shape.RoundedCornerShape
    val px = rounded?.topStart?.toPx(size, density) ?: 0f
    return CornerRadius(px, px)
}

// ── Typography ────────────────────────────────────────────────────────────
// System faces only: the app ships no font assets and makes no network calls.
private val FontDisplay = FontFamily.SansSerif
private val FontMono    = FontFamily.Monospace
private val FontBody    = FontFamily.SansSerif

object MorroTypography {
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
        lineHeight = 40.sp,
        letterSpacing = (-0.03f).sp
    )

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

    val TitleMedium = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )

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

    // Labels / eyebrows (mono, tracked) — used sparingly, never over a headline.
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

internal val MaterialColorScheme = androidx.compose.material3.darkColorScheme(
    background      = MorroColors.Canvas,
    surface         = MorroColors.Surface,
    surfaceVariant  = MorroColors.SurfaceVariant,
    onBackground    = MorroColors.TextPrimary,
    onSurface       = MorroColors.TextPrimary,
    onSurfaceVariant = MorroColors.TextSecondary,
    primary         = MorroColors.AccentTactical,
    onPrimary       = Color.Black,
    primaryContainer = MorroColors.SurfaceElevated,
    onPrimaryContainer = MorroColors.TextPrimary,
    secondary       = MorroColors.AccentBubbly,
    onSecondary     = Color.Black,
    secondaryContainer = MorroColors.SurfaceLow,
    onSecondaryContainer = MorroColors.TextPrimary,
    tertiary        = MorroColors.AccentBubbly,
    onTertiary      = Color.Black,
    error           = MorroColors.Error,
    onError         = Color.Black,
    outline         = MorroColors.BorderSubtle,
    outlineVariant  = MorroColors.BorderSubtle,
    inverseSurface  = MorroColors.TextPrimary,
    inverseOnSurface = MorroColors.Canvas,
    inversePrimary  = MorroColors.AccentTactical,
    surfaceTint     = MorroColors.AccentTactical,
)

// ── Spring specs ──────────────────────────────────────────────────────────
/**
 * Apple's damping/response pair expressed as Compose springs (mass 1):
 * stiffness = (2π / response)^2.
 *
 *  - default   damping 1.0  / response 0.35s  — settles with no overshoot
 *  - momentum  damping 0.82 / response 0.32s  — only for gestures that carried velocity
 *  - press     damping 1.0  / response 0.12s  — instant acknowledgement on touch-down
 */
object MorroSpring {
    val FloatSpringDefault: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 322f)
    val FloatSpringMomentum: SpringSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 386f)
    val FloatSpringPress: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 2741f)
    val DpSpringDefault: SpringSpec<androidx.compose.ui.unit.Dp> = spring(
        dampingRatio = 1f,
        stiffness = 322f
    )
    const val ReducedMotionMillis = 150
    const val ScreenTransitionMillis = 300
}

object MorroCompositionLocals {
    val LocalMorroColors = androidx.compose.runtime.staticCompositionLocalOf { MorroColorPalette() }
    val LocalMorroTypography = androidx.compose.runtime.staticCompositionLocalOf { MorroTextPalette() }
    val LocalMorroShapes = androidx.compose.runtime.staticCompositionLocalOf { MorroShapes }
}

data class MorroColorPalette(
    val canvas: Color = MorroColors.Canvas,
    val surface: Color = MorroColors.Surface,
    val surfaceElevated: Color = MorroColors.SurfaceElevated,
    val surfaceCard: Color = MorroColors.SurfaceCard,
    val surfaceVariant: Color = MorroColors.SurfaceVariant,
    val surfaceLow: Color = MorroColors.SurfaceLow,
    val textPrimary: Color = MorroColors.TextPrimary,
    val textSecondary: Color = MorroColors.TextSecondary,
    val textDisabled: Color = MorroColors.TextDisabled,
    val textTertiary: Color = MorroColors.TextTertiary,
    val accentTactical: Color = MorroColors.AccentTactical,
    val accentBubbly: Color = MorroColors.AccentBubbly,
    val accentBubblySoft: Color = MorroColors.AccentBubblySoft,
    val accentBubblyDeep: Color = MorroColors.AccentBubblyDeep,
    val warmYellow: Color = MorroColors.WarmYellow,
    val borderSubtle: Color = MorroColors.BorderSubtle,
    val borderStrong: Color = MorroColors.BorderStrong,
    val borderActive: Color = MorroColors.BorderActive,
    val success: Color = MorroColors.Success,
    val error: Color = MorroColors.Error,
)

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

// ── Spring helpers ────────────────────────────────────────────────────────
@Composable
fun rememberMorroSpring(
    initialValue: Float,
    spec: SpringSpec<Float> = MorroSpring.FloatSpringDefault
): Animatable<Float, AnimationVector1D> {
    // `spec` is accepted so call sites can name their intent; the Animatable itself is retargeted
    // with that spec by animateTo(...), keeping one Animatable per component.
    return remember { Animatable(initialValue) }
}

fun lerpColor(from: Color, to: Color, t: Float): Color {
    val p = t.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * p,
        green = from.green + (to.green - from.green) * p,
        blue = from.blue + (to.blue - from.blue) * p,
        alpha = from.alpha + (to.alpha - from.alpha) * p,
        colorSpace = from.colorSpace
    )
}

@Composable
fun springToColor(spring: Animatable<Float, AnimationVector1D>, from: Color, to: Color): Color =
    lerpColor(from, to, spring.value)

@Composable
fun springToAlpha(spring: Animatable<Float, AnimationVector1D>, from: Float, to: Float): Float =
    from + (to - from) * spring.value.coerceIn(0f, 1f)

@Composable
fun springToScale(spring: Animatable<Float, AnimationVector1D>, from: Float, to: Float): Float =
    from + (to - from) * spring.value.coerceIn(0f, 1f)

@Composable
fun springToDp(spring: Animatable<Float, AnimationVector1D>, from: Dp, to: Dp): Dp =
    Dp(from.value + (to.value - from.value) * spring.value.coerceIn(0f, 1f))

@Composable
fun derivedSpringValue(spring: Animatable<Float, AnimationVector1D>, calc: (Float) -> Dp): Dp =
    calc(spring.value.coerceIn(0f, 1f))

// ── Legacy compatibility ──────────────────────────────────────────────────
object MorroTokens {
    val CanvasRoot: Color = MorroColors.Canvas
    val buttonShape = MorroShapes.Button
}

@Composable
fun morroColors(): MorroColorPalette = MorroCompositionLocals.LocalMorroColors.current

@Composable
fun morroTypography(): MorroTextPalette = MorroCompositionLocals.LocalMorroTypography.current

@Composable
fun morroShapes(): MorroShapes = MorroCompositionLocals.LocalMorroShapes.current

/** Applies the full Morro system. MaterialTheme is kept in sync for stock components. */
@Composable
fun MorroTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        MorroCompositionLocals.LocalMorroColors provides MorroColorPalette(),
        MorroCompositionLocals.LocalMorroTypography provides MorroTextPalette(),
        MorroCompositionLocals.LocalMorroShapes provides MorroShapes
    ) {
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
