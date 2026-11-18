package com.morro.alarm.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Reveals a row 30ms after the row above it, on the shared entrance curve.
 *
 * The delay is capped inside [MorroMotion.staggerDelayMs], so a seven-row list settles in 180ms
 * rather than trickling in for a second. With reduced motion the row is simply present.
 */
@Composable
fun Modifier.staggeredReveal(index: Int): Modifier {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }

    LaunchedEffect(index) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            delay(MorroMotion.staggerDelayMs(index))
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(MorroMotion.EnterMillis, easing = MorroMotion.EnterEasing)
            )
        }
    }

    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12f
    }
}
