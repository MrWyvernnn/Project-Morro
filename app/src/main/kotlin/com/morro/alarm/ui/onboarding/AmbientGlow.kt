package com.morro.alarm.ui.onboarding

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.morro.alarm.ui.theme.MorroColors

/**
 * Ambient lighting for the questions onboarding.
 *
 * A black field lit from behind by two soft sources - bubbly pink and tactical orange - plus one
 * diagonal wash that gives the light a direction. Every layer sits at 0.07-0.10 alpha so it reads
 * as atmosphere, never as a coloured panel or a banded gradient.
 *
 * The drift is one slow breathe (11s, reverse) that moves each source a few percent and lets the
 * light dim and recover. When the user has animations switched off, the light holds still.
 */
@Composable
fun AmbientGlow(modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()

    val drift = if (reducedMotion) {
        0.5f
    } else {
        rememberInfiniteTransition(label = "ambientGlow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = GlowCycleMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowDrift"
        ).value
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MorroColors.Canvas)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            // Keep the breathe gentle: the light recovers to full strength, never flickers.
            val breathe = 0.75f + 0.25f * drift

            // Pink source, upper left, drifting toward the centre.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MorroColors.AccentBubbly.copy(alpha = 0.10f * breathe),
                        Color.Transparent
                    ),
                    center = Offset(
                        x = width * (0.16f + 0.07f * drift),
                        y = height * (0.20f - 0.05f * drift)
                    ),
                    radius = width * 0.90f
                )
            )

            // Orange source, lower right, drifting up as the pink comes down.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MorroColors.AccentTactical.copy(alpha = 0.09f * breathe),
                        Color.Transparent
                    ),
                    center = Offset(
                        x = width * (0.94f - 0.09f * drift),
                        y = height * (0.78f + 0.06f * drift)
                    ),
                    radius = width * 0.95f
                )
            )

            // One diagonal wash so the light has a direction rather than radiating from nothing.
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        MorroColors.AccentBubbly.copy(alpha = 0.08f * breathe),
                        MorroColors.AccentTactical.copy(alpha = 0.06f * breathe),
                        Color.Transparent
                    ),
                    start = Offset(x = -width * 0.25f + width * 0.10f * drift, y = 0f),
                    end = Offset(x = width * 1.15f, y = height * (1.05f - 0.10f * drift))
                )
            )
        }
    }
}

private const val GlowCycleMillis = 11_000

/**
 * True when the user has turned system animations off (animator duration scale of 0). The whole
 * screen uses this so motion, drift and pulses all stop together.
 */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { isReducedMotion(context) }
}

internal fun isReducedMotion(context: Context): Boolean {
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    return scale == 0f
}
