package com.morro.alarm.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/**
 * MorroTheme: Global Compose Material Theme for Morro.
 *
 * Enforces cyber-defense contrast and reliability. No playful colors.
 * Strictly AmoledBlack for all root backgrounds (high reliability).
 *
 * dynamicColor is explicitly set to false so Material 3 never overwrites
 * the AmoledBlack palette with Android system Monet colors.
 */
@Composable
fun MorroTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = MorroColors.AlertOrange,
        secondary = MorroColors.ElectricCyan,
        background = MorroColors.AmoledBlack,
        surface = MorroColors.MidnightBlue,
        onPrimary = MorroColors.AmoledBlack,
        onSecondary = MorroColors.AmoledBlack,
        onBackground = MorroColors.WhitePrimary,
        onSurface = MorroColors.WhitePrimary
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}