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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.theme.MorroColors

/**
 * Ambient light for the intro screens.
 *
 * Not radial blobs: three long, soft ribbons sweeping diagonally across pure black, at 0.06-0.12
 * alpha. They give the stage a direction and connect the splash's ribbon to the questions, without
 * ever reading as a coloured panel behind the text.
 *
 * One slow breathe (11s, reverse) moves the light a few percent. System animations off -> still.
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
            val w = size.width
            val h = size.height
            val breathe = 0.7f + 0.3f * drift

            // Ribbon 1: low sweep, orange -> pink, the widest light.
            val band = Path().apply {
                moveTo(-w * 0.30f, h * 0.86f + h * 0.04f * drift)
                cubicTo(
                    w * 0.18f, h * 1.02f,
                    w * 0.42f, h * 0.50f,
                    w * 1.20f, h * 0.26f
                )
            }
            drawPath(
                path = band,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MorroColors.AccentTactical.copy(alpha = 0.11f * breathe),
                        MorroColors.AccentBubbly.copy(alpha = 0.09f * breathe)
                    ),
                    start = Offset(0f, h * 0.85f),
                    end = Offset(w, h * 0.30f)
                ),
                style = Stroke(width = 120.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = band,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MorroColors.AccentBubbly.copy(alpha = 0.10f * breathe),
                        Color.Transparent
                    ),
                    start = Offset(0f, h * 0.85f),
                    end = Offset(w, h * 0.35f)
                ),
                style = Stroke(width = 28.dp.toPx(), cap = StrokeCap.Round)
            )

            // Ribbon 2: a narrower echo higher up, only on the right half.
            val echo = Path().apply {
                moveTo(w * 0.20f, h * 0.06f - h * 0.02f * drift)
                cubicTo(
                    w * 0.52f, h * 0.10f,
                    w * 0.70f, h * 0.24f,
                    w * 1.18f, h * 0.20f
                )
            }
            drawPath(
                path = echo,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        MorroColors.AccentBubbly.copy(alpha = 0.08f * breathe),
                        MorroColors.AccentTactical.copy(alpha = 0.05f * breathe)
                    )
                ),
                style = Stroke(width = 64.dp.toPx(), cap = StrokeCap.Round)
            )

            // A quiet warm lift at the very bottom so the canvas is not flat black.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MorroColors.AccentTactical.copy(alpha = 0.035f * breathe)
                    ),
                    startY = h * 0.62f,
                    endY = h
                )
            )
        }
    }
}

private const val GlowCycleMillis = 11_000

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
