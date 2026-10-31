package com.morro.alarm.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MorroColorPalette: Cyber-defense themed palette for Morro alarm app.
 *
 * Colors are deliberately stark and high-contrast for reliability.
 * All values use 0xRRGGBBAA format.
 */
object MorroColors {
    // Core background layers
    val AmoledBlack = Color(0xFF000000)      // Root, max darkness, no OLED glow
    val MidnightBlue = Color(0xFF0A0A0F)    // Surface, keypad, TextField backgrounds
    val AlertOrange = Color(0xFFFF4500)     // Interactive accents, Submit & Snooze buttons
    val ElectricCyan = Color(0xFF00F0FF)    // Optional accent for future use

    // Text colors (all white variants for maximum contrast)
    val WhitePrimary = Color(0xFFFFFFFF)    // Main text, headings
    val WhiteSecondary = Color(0xFFE0E0E0)  // Supporting text, placeholders
    val WhiteTertiary = Color(0xFFCFCFCF)   // Disabled, hints

    // Alerting
    val ErrorRed = Color(0xFFEF5350)         // Validation errors
    val SuccessGreen = Color(0xFF4CAF50)    // Success states, confirmed actions
}