package com.morro.alarm.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Morro design tokens — one warm monochrome ramp on pure black, with a single rose accent that
 * belongs to the mascot's eyes and nothing else.
 *
 * Direction: calm, warm, a little stubborn. The world is a windowsill before sunrise: black glass,
 * warm paper-white light, soft edges. Colour is not the brand here — light is. Every surface is a
 * step of one warm neutral ramp, every border is the text colour at low alpha, and the warm
 * highlight is the same honey the text is made of.
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
    /** Three steps only: control, card, sheet. More radii than that reads as inconsistency. */
    val Button = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

// ── Colours ───────────────────────────────────────────────────────────────
object MorroColors {
    // Warm neutral ramp
    val Canvas          = Color(0xFF000000)
    val Surface         = Color(0xFF0B0A08)
    val SurfaceCard     = Color(0xFF121110)
    val SurfaceElevated = Color(0xFF1A1714)
    val SurfaceLow      = Color(0xFF231F19)

    /** Same tone as a card, named for the Material slot it fills. */
    val SurfaceVariant = SurfaceCard

    // Text roles
    val TextPrimary   = Color(0xFFEFE7D6) // honey white, never pure #FFFFFF
    val TextSecondary = Color(0xFFA79E8D)

    /**
     * 5.3:1 on #000000, so 11–12sp metadata clears WCAG AA. The previous value sat at 4.47:1 and
     * technically failed for text this small.
     */
    val TextTertiary  = Color(0xFF877F70)
    val TextDisabled  = Color(0xFF6B6458)

    /** The warm highlight: elapsed-hour arc, armed dot, second hand, selection checks. */
    val Honey = Color(0xFFE8C58F)

    /**
     * Drawn-cloud white. A shade warmer than the text colour, so a solid cloud reads as paper in
     * warm light rather than as a white cut-out sitting on the black.
     */
    val CloudWhite = Color(0xFFF6EFE1)

    /**
     * The mascot's eyes. This is the one chromatic value in the product, and it is deliberately
     * not exposed on the palette: it may only be used by the mascot's face, never by chrome,
     * status, or text.
     */
    val EyeRose = Color(0xFFE8B4AE)

    // Hairlines: the text colour at low alpha, never a coloured border
    val BorderSubtle = Color(0x14EFE7D6) // 8%
    val BorderStrong = Color(0x2EEFE7D6) // 18%
    val BorderActive = Color(0x59EFE7D6) // 35%

    // Status is tonal, not chromatic: the interface stays monochrome
    val Success = Color(0xFFB9C4A6)
    val Error   = Color(0xFFD8C1A4)
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

private val MaterialTypography = Typography(
    displayLarge   = MorroTypography.DisplayHero,
    displayMedium  = MorroTypography.DisplayLarge,
    headlineLarge  = MorroTypography.HeadlineLarge,
    headlineMedium = MorroTypography.HeadlineMedium,
    headlineSmall  = MorroTypography.HeadlineSmall,
    titleMedium    = MorroTypography.TitleMedium,
    bodyLarge      = MorroTypography.BodyLarge,
    bodyMedium     = MorroTypography.BodyMedium,
    labelLarge     = MorroTypography.LabelLarge,
    labelSmall     = MorroTypography.LabelSmall,
)

private val MaterialColorScheme = darkColorScheme(
    background         = MorroColors.Canvas,
    surface            = MorroColors.Surface,
    surfaceVariant     = MorroColors.SurfaceCard,
    onBackground       = MorroColors.TextPrimary,
    onSurface          = MorroColors.TextPrimary,
    onSurfaceVariant   = MorroColors.TextSecondary,
    primary            = MorroColors.TextPrimary,
    onPrimary          = MorroColors.Canvas,
    primaryContainer   = MorroColors.SurfaceElevated,
    onPrimaryContainer = MorroColors.TextPrimary,
    secondary          = MorroColors.TextSecondary,
    onSecondary        = MorroColors.Canvas,
    secondaryContainer = MorroColors.SurfaceLow,
    onSecondaryContainer = MorroColors.TextPrimary,
    tertiary           = MorroColors.TextSecondary,
    onTertiary         = MorroColors.Canvas,
    error              = MorroColors.Error,
    onError            = MorroColors.Canvas,
    outline            = MorroColors.BorderSubtle,
    outlineVariant     = MorroColors.BorderSubtle,
    inverseSurface     = MorroColors.TextPrimary,
    inverseOnSurface   = MorroColors.Canvas,
    inversePrimary     = MorroColors.TextSecondary,
    surfaceTint        = Color.Transparent,
)

// ── Motion ────────────────────────────────────────────────────────────────
/**
 * Springs carry the feel:
 *   default  1.00 damping / 322 stiffness -> settles, no overshoot
 *   momentum 0.82 damping / 386 stiffness -> only after a gesture carried velocity
 *   press    1.00 damping / 2741 stiffness -> instant acknowledgement
 *   playful  0.55 damping / 438 stiffness -> the mascot and button pops
 */
object MorroSpring {
    val FloatSpringDefault: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 322f)
    val FloatSpringPress: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 2741f)
    val FloatSpringPlayful: SpringSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 438f)
}

// ── Theme ─────────────────────────────────────────────────────────────────
data class MorroColorPalette(
    val canvas: Color = MorroColors.Canvas,
    val surface: Color = MorroColors.Surface,
    val surfaceCard: Color = MorroColors.SurfaceCard,
    val surfaceVariant: Color = MorroColors.SurfaceVariant,
    val surfaceElevated: Color = MorroColors.SurfaceElevated,
    val surfaceLow: Color = MorroColors.SurfaceLow,
    val textPrimary: Color = MorroColors.TextPrimary,
    val textSecondary: Color = MorroColors.TextSecondary,
    val textTertiary: Color = MorroColors.TextTertiary,
    val textDisabled: Color = MorroColors.TextDisabled,
    val honey: Color = MorroColors.Honey,
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

private val LocalMorroColors = staticCompositionLocalOf { MorroColorPalette() }
private val LocalMorroTypography = staticCompositionLocalOf { MorroTextPalette() }
private val LocalMorroShapes = staticCompositionLocalOf { MorroShapes }

@Composable
fun morroColors(): MorroColorPalette = LocalMorroColors.current

@Composable
fun morroTypography(): MorroTextPalette = LocalMorroTypography.current

@Composable
fun morroShapes(): MorroShapes = LocalMorroShapes.current

// ── Spring helpers ────────────────────────────────────────────────────────
/** Animatable factory kept here so every component starts from the same default spring. */
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
fun MorroTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMorroColors provides MorroColorPalette(),
        LocalMorroTypography provides MorroTextPalette(),
        LocalMorroShapes provides MorroShapes
    ) {
        MaterialTheme(
            colorScheme = MaterialColorScheme,
            typography = MaterialTypography,
            shapes = Shapes(
                extraSmall = MorroShapes.Chip,
                small = MorroShapes.Chip,
                medium = MorroShapes.Button,
                large = MorroShapes.Card,
                extraLarge = MorroShapes.Card
            ),
            content = content
        )
    }
}
