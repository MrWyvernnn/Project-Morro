package com.morro.alarm.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
 * Morro design tokens — one warm monochrome ramp on pure black.
 *
 * Direction: calm, warm, a little stubborn. The world is a windowsill before sunrise: black glass,
 * warm paper-white light, and soft edges. Colour is not the brand here — light is. Every surface is
 * a step of one warm neutral ramp, every border is the text colour at 8% opacity, and the only
 * "accent" is the honey-white the text is already made of.
 *
 * Rules:
 *  - Canvas is #000000. Nothing tints the ground.
 *  - Depth comes from surface steps plus a hairline, never from shadows, glows or gradients.
 *  - No hue-to-hue gradients anywhere. A single-neutral tone shift is the only gradient allowed.
 *  - Text has three roles: honey white, warm grey, muted. No fourth.
 *  - One bright thing per screen carries the character (the mascot on splash, the dial on the
 *    dashboard). Chrome stays quiet.
 */

// ── Geometry ──────────────────────────────────────────────────────────────
object MorroShapes {
    val Card = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val Button = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val Chip = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    val Pill = androidx.compose.foundation.shape.RoundedCornerShape(50)
    val Sheet = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val FocusRing = 2.dp
}

// ── Colours ───────────────────────────────────────────────────────────────
object MorroColors {
    // Warm neutral ramp
    val Canvas           = Color(0xFF000000)
    val Surface          = Color(0xFF0B0A08)
    val SurfaceCard      = Color(0xFF121110)
    val SurfaceElevated  = Color(0xFF1A1714)
    val SurfaceLow       = Color(0xFF231F19)
    val SurfaceVariant   = SurfaceCard

    // Text roles
    val TextPrimary      = Color(0xFFEFE7D6) // honey white, never pure #FFFFFF
    val TextSecondary    = Color(0xFFA79E8D)
    val TextTertiary     = Color(0xFF7C7466)
    val TextDisabled     = Color(0xFF6B6458)

    /**
     * Warm highlight. The old accent names are kept so every call site compiles, but they all point
     * at the same honey tone: the product no longer has a second hue.
     */
    val AccentTactical   = Color(0xFFE8C58F)
    val AccentBubbly     = Color(0xFFE8C58F)
    val AccentBubblySoft = Color(0xFFD8B581)
    val AccentBubblyDeep = Color(0xFFC99B63)
    val WarmYellow       = Color(0xFFE8C58F)

    // Hairlines: the text colour at low alpha, never a coloured border
    val BorderSubtle     = Color(0x14EFE7D6) // 8%
    val BorderStrong     = Color(0x2EEFE7D6) // 18%
    val BorderActive     = Color(0x59EFE7D6) // 35%

    // Status is tonal, not chromatic: the interface stays monochrome
    val Success          = Color(0xFFB9C4A6)
    val Error            = Color(0xFFD8C1A4)

    // Legacy aliases
    val CanvasRoot = Canvas
    val SurfaceDark = Surface
    val TextPrimaryDark = TextPrimary
    val AccentPrimary = AccentTactical
    val Border = BorderActive
}

/**
 * A hairline border drawn inside the component's own bounds.
 *
 * Compose's `border` only takes a solid colour and sits on top of the clip; drawing it ourselves
 * keeps the radius concentric with the surface, which is what makes nested cards look machined
 * rather than stuck on.
 */
fun Modifier.morroHairline(
    radius: Dp = 16.dp,
    width: Dp = 1.dp,
    color: Color = MorroColors.BorderSubtle,
): Modifier = this.drawBehind {
    val r = radius.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = width.toPx())
    )
}


// ── Typography ────────────────────────────────────────────────────────────
// System faces only: no font assets ship and nothing is fetched.
// Hierarchy comes from size and colour. Weights stay at 400/500, 600 for a rare emphasis.
private val FontDisplay = FontFamily.SansSerif
private val FontBody    = FontFamily.SansSerif

object MorroTypography {
    val DisplayHero = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.6).sp
    )

    val DisplayLarge = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp
    )

    val HeadlineLarge = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    )

    val HeadlineMedium = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp
    )

    val HeadlineSmall = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )

    val TitleMedium = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )

    val BodyLarge = TextStyle(
        fontFamily = FontBody,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp
    )

    val BodyMedium = TextStyle(
        fontFamily = FontBody,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    )

    /** Small metadata. Sentence case; pair with Monospace at the call site for figures. */
    val LabelLarge = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    )

    val LabelSmall = TextStyle(
        fontFamily = FontDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp
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
    primary         = MorroColors.TextPrimary,
    onPrimary       = MorroColors.Canvas,
    primaryContainer = MorroColors.SurfaceElevated,
    onPrimaryContainer = MorroColors.TextPrimary,
    secondary       = MorroColors.TextSecondary,
    onSecondary     = MorroColors.Canvas,
    secondaryContainer = MorroColors.SurfaceLow,
    onSecondaryContainer = MorroColors.TextPrimary,
    tertiary        = MorroColors.TextSecondary,
    onTertiary      = MorroColors.Canvas,
    error           = MorroColors.Error,
    onError         = MorroColors.Canvas,
    outline         = MorroColors.BorderSubtle,
    outlineVariant  = MorroColors.BorderSubtle,
    inverseSurface  = MorroColors.TextPrimary,
    inverseOnSurface = MorroColors.Canvas,
    inversePrimary  = MorroColors.TextSecondary,
    surfaceTint     = Color.Transparent,
)

// ── Motion ────────────────────────────────────────────────────────────────
/**
 * Springs carry the feel. Damping/response pairs, translated to stiffness with mass 1:
 *   default  1.00 / 0.35s -> 322   settles, no overshoot
 *   momentum 0.82 / 0.32s -> 386   only after a gesture carried velocity
 *   press    1.00 / 0.12s -> 2741  instant acknowledgement
 *   playful  0.55 / 0.30s -> 438   the mascot and button pops, where a small bounce is the point
 */
object MorroSpring {
    val FloatSpringDefault: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 322f)
    val FloatSpringMomentum: SpringSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 386f)
    val FloatSpringPress: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 2741f)
    val FloatSpringPlayful: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 438f)
    val DpSpringDefault: SpringSpec<Dp> = spring(dampingRatio = 1f, stiffness = 322f)
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
): Animatable<Float, AnimationVector1D> = remember { Animatable(initialValue) }

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

// ── Compatibility ─────────────────────────────────────────────────────────
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
