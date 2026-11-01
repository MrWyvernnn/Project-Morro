package com.morro.alarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Morro Design Tokens — warm honey & organic amber palette.
 *
 * Rules enforced across the system:
 * - AMOLED black canvas, warm ivory text, honey-amber accent, zero gradients, zero glassmorphism.
 * - Disciplined soft radii override the reference spec's 0dp harshness.
 * - Typography triplet: clean sans-serif (headlines), monospace (data/badges), sans-serif (body).
 *
 * Font strategy: system font families used as offline-safe fallbacks.
 * When TTF files are bundled (e.g. res/font/), swap FontFamily.Default → FontFamily(Font(...))
 * without touching any other token definitions.
 */

object MorroTokens {
    // ── Geometry ──────────────────────────────────────────────────────────────
    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val buttonShape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    val chipShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)

    // ── Colors ───────────────────────────────────────────────────────────────
    // Canvas & surface layers
    val CanvasRoot   = Color(0xFF000000) // AMOLED black — 0% subpixel
    val Surface      = Color(0xFF0A0A0F) // Midnight obsidian — primary panel surface
    val SurfaceLow   = Color(0xFF14141A) // Sub-containers and inactive cards
    val SurfaceDim   = Color(0xFF131318) // Secondary dim panels

    // Accent & alerts
    val AccentPrimary = Color(0xFFFFA726) // Soft golden honey — armed / active / selected
    val Alert         = Color(0xFFFF5252) // Soft warning / amber state

    // Borders
    val Border        = Color(0xFF1F1F2E) // Structural outline, 1dp inactive
    val BorderActive  = Color(0xFFFFA726) // Armed focus border, 1.5dp active

    // Typography
    val TextPrimary   = Color(0xFFFFFFFF) // Crisp warm white — headings, active labels
    val TextSecondary = Color(0xFF999999) // Muted sand-gray — supporting text, placeholders
    val TextDisabled  = Color(0xFF404040) // 25% white — locked states

    // Status helpers
    val Success = Color(0xFF4CAF50)
    val Error   = Color(0xFFEF5350)
}

// ── Font families ─────────────────────────────────────────────────────────────
// System font fallbacks used because the app is 100% offline.
// Swap to FontFamily(Font(...)) when TTF assets are bundled — token names stay stable.

private val FontHeadlines  = FontFamily.SansSerif // maps to Space Grotesk at system level
private val FontMono       = FontFamily.Monospace  // maps to JetBrains Mono at system level
private val FontBody       = FontFamily.SansSerif  // maps to Inter at system level

// ── Typography ───────────────────────────────────────────────────────────────

private val TypographyHeadlineLg = TextStyle(
    fontFamily = FontHeadlines,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.02f).sp
)

private val TypographyHeadlineMd = TextStyle(
    fontFamily = FontHeadlines,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.01f).sp
)

private val TypographyHeadlineSm = TextStyle(
    fontFamily = FontHeadlines,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp
)

// Display time: large mono chrono readout (alarm clock face)
private val TypographyDisplayTime = TextStyle(
    fontFamily = FontHeadlines,
    fontWeight = FontWeight.Bold,
    fontSize = 56.sp,
    lineHeight = 56.sp,
    letterSpacing = (-0.04f).sp
)

// Data / telemetry readout
private val TypographyDataMonoLg = TextStyle(
    fontFamily = FontMono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.02f).sp
)

private val TypographyDataMonoMd = TextStyle(
    fontFamily = FontMono,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.01f).sp
)

// Label badges — monospaced uppercase protocol tags
private val TypographyLabelCaps = TextStyle(
    fontFamily = FontMono,
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.12f.sp
)

private val TypographyLabelCode = TextStyle(
    fontFamily = FontMono,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    letterSpacing = 0.08f.sp
)

// Body text — operational copy
private val TypographyBodyMd = TextStyle(
    fontFamily = FontBody,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.01f).sp
)

private val TypographyBodySm = TextStyle(
    fontFamily = FontBody,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp
)

private val MorroTypography = androidx.compose.material3.Typography(
    headlineLarge  = TypographyHeadlineLg,
    headlineMedium = TypographyHeadlineMd,
    headlineSmall  = TypographyHeadlineSm,
    bodyLarge      = TypographyBodyMd,
    bodyMedium     = TypographyBodySm,
    // Material3 extras — map to nearest spec token
    displayLarge   = TypographyDisplayTime,
    labelLarge     = TypographyLabelCaps,
    labelSmall     = TypographyLabelCode,
)

private val MorroColorScheme = darkColorScheme(
    // Surface layers
    background      = MorroTokens.CanvasRoot,
    surface         = MorroTokens.Surface,
    surfaceVariant  = MorroTokens.SurfaceLow,
    onBackground    = MorroTokens.TextPrimary,
    onSurface       = MorroTokens.TextPrimary,
    onSurfaceVariant = MorroTokens.TextSecondary,
    // Primary — Soft Golden Honey
    primary         = MorroTokens.AccentPrimary,
    onPrimary       = Color.Black,
    primaryContainer = MorroTokens.SurfaceLow,
    onPrimaryContainer = MorroTokens.AccentPrimary,
    // Secondary — not used in v1 spec; kept for M3 completeness
    secondary       = MorroTokens.TextSecondary,
    onSecondary     = MorroTokens.CanvasRoot,
    // Tertiary — Alert Orange (destructive / breach)
    tertiary        = MorroTokens.Alert,
    onTertiary      = Color.Black,
    // Error
    error           = MorroTokens.Error,
    onError         = Color.Black,
    // Outlines & borders
    outline         = MorroTokens.Border,
    outlineVariant  = MorroTokens.Border,
    // Inverse (not used — AMOLED black canvas has no inverse)
    inverseSurface  = MorroTokens.TextPrimary,
    inverseOnSurface = MorroTokens.CanvasRoot,
    inversePrimary  = MorroTokens.Surface,
    surfaceTint     = MorroTokens.AccentPrimary,
)

/**
 * MorroTheme — applies the full Cyber-Defense design system.
 *
 * Usage:
 * ```
 * MorroTheme {
 *     // your composable content
 * }
 * ```
 *
 * dynamicColor is always false — the system palette is locked and never
 * overwritten by Android system Monet colors.
 */
@Composable
fun MorroTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MorroColorScheme,
        typography  = MorroTypography,
        content    = content
    )
}
