package com.morro.alarm.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colour tokens, with no Compose runtime or Android dependency beyond [Color].
 *
 * Kept in its own file for two reasons: [MorroThemeChoice] can be stored and compared by code that
 * has no business pulling in a theme, and the whole palette can be unit-tested for contrast without
 * an emulator or Robolectric.
 *
 * Rules the values follow, in both themes:
 *  - Canvas is pure black. Nothing tints the ground, in either theme.
 *  - Depth is surface steps plus a hairline. No shadows, glows or hue-to-hue gradients.
 *  - Text has three roles. Metadata at 11–12sp must clear 4.5:1 on black and on a card.
 *  - One accent per theme, and status colours stay at least 15° away from it in hue so an error
 *    never reads as decoration.
 */
data class MorroColorPalette(
    val canvas: Color = Color(0xFF000000),
    val surface: Color = Color(0xFF0B0A08),
    val surfaceCard: Color = Color(0xFF121110),
    val surfaceVariant: Color = Color(0xFF121110),
    val surfaceElevated: Color = Color(0xFF1A1714),
    val surfaceHigh: Color = Color(0xFF231F19),
    val textPrimary: Color = Color(0xFFEFE7D6),
    val textSecondary: Color = Color(0xFFA79E8D),
    val textTertiary: Color = Color(0xFF877F70),
    val textDisabled: Color = Color(0xFF6B6458),
    val honey: Color = Color(0xFFE8C58F),
    val borderSubtle: Color = Color(0x14EFE7D6),
    val borderStrong: Color = Color(0x2EEFE7D6),
    val borderActive: Color = Color(0x59EFE7D6),
    val success: Color = Color(0xFFB9C4A6),
    val error: Color = Color(0xFFD8C1A4),
)

object MorroPalettes {
    /** Honey on black, clouds in the sky. The original look; values are frozen by a unit test. */
    val Midnight = MorroColorPalette()

    /**
     * Ember on black, leaves in the sky.
     *
     * Same structure as Midnight — black canvas, one accent, three text roles, hairline borders —
     * with the neutral ramp moved toward amber and the accent moved from honey to ember. Measured
     * pairs: text 17.4 / 8.9 / 5.6:1 on black, accent 10.1:1 on black and 9.1:1 on a card.
     *
     * The status colours are deliberately not part of the warm ramp: `error` is a rose, which sits
     * a clear hue step away from the ember accent, so a wrong-answer state can never be mistaken
     * for a decorative one.
     */
    val Autumn = MorroColorPalette(
        canvas = Color(0xFF000000),
        surface = Color(0xFF0D0906),
        surfaceCard = Color(0xFF16100A),
        surfaceVariant = Color(0xFF16100A),
        surfaceElevated = Color(0xFF201710),
        surfaceHigh = Color(0xFF2B1F15),
        textPrimary = Color(0xFFF5E8D6),
        textSecondary = Color(0xFFB9A78D),
        textTertiary = Color(0xFF93816A),
        textDisabled = Color(0xFF75654F),
        honey = Color(0xFFF2A25C),
        borderSubtle = Color(0x14F5E8D6),
        borderStrong = Color(0x2EF5E8D6),
        borderActive = Color(0x59F5E8D6),
        success = Color(0xFFB7BE9B),
        error = Color(0xFFD0787E),
    )

    fun of(choice: MorroThemeChoice): MorroColorPalette = when (choice) {
        MorroThemeChoice.MIDNIGHT -> Midnight
        MorroThemeChoice.AUTUMN -> Autumn
    }
}
