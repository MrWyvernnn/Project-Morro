package com.morro.alarm.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The one press behaviour in the app.
 *
 * Down is a snap, not a tween: waiting for an animation to reach the pressed size reads as lag.
 * Release is a critically damped spring back to 1, so a tap lands without a bounce. The scale is
 * written in a graphicsLayer lambda, so it never triggers recomposition.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = MorroMotion.PressedScale,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            scale.snapTo(pressedScale)
        } else {
            scale.animateTo(1f, MorroMotion.PressRelease)
        }
    }

    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
