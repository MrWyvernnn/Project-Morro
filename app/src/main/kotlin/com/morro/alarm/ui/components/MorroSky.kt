package com.morro.alarm.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroThemeChoice
import com.morro.alarm.ui.theme.morroThemeChoice

/**
 * The sky behind the intro screens: one call site shape, two decorations.
 *
 * Screens ask for a sky, not for a specific drawing, so switching theme changes what falls without
 * touching any of them. Same arguments as [DriftingClouds] and [FallingLeaves], so either can be
 * used directly when a screen really does want one of them.
 *
 * The swaps cross-fade rather than cut: clouds and leaves are different silhouettes moving on
 * different axes, and a hard cut between them reads as a glitch. With reduced motion the fade
 * collapses to a single frame.
 */
@Composable
fun MorroSky(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    durationMillis: Int = 180_000,
) {
    val choice = morroThemeChoice()
    val reduced = rememberReducedMotion()

    Crossfade(
        targetState = choice,
        animationSpec = tween(
            durationMillis = if (reduced) 0 else MorroMotion.EnterMillis * 2,
            easing = MorroMotion.EnterEasing
        ),
        label = "sky"
    ) { sky ->
        when (sky) {
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
}
