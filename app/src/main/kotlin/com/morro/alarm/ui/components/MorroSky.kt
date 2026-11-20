package com.morro.alarm.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.morro.alarm.ui.theme.MorroThemeChoice
import com.morro.alarm.ui.theme.morroThemeChoice

/**
 * The sky behind the intro screens: one call site shape, two decorations.
 *
 * Screens ask for a sky, not for a specific drawing, so switching theme changes what falls without
 * touching any of them. Same arguments as [DriftingClouds] and [FallingLeaves], so either can be
 * used directly when a screen really does want one of them.
 */
@Composable
fun MorroSky(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    durationMillis: Int = 180_000,
) {
    when (morroThemeChoice()) {
        MorroThemeChoice.MIDNIGHT -> DriftingClouds(
            modifier = modifier,
            intensity = intensity,
            durationMillis = durationMillis
        )

        MorroThemeChoice.AUTUMN -> FallingLeaves(
            modifier = modifier,
            intensity = intensity,
            durationMillis = durationMillis
        )
    }
}
