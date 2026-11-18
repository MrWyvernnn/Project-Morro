package com.morro.alarm.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroColors

/**
 * The armed/idle dot. When it becomes armed it emits one ring and settles — the single
 * celebratory pulse in the app, 320ms, then everything is still again.
 */
@Composable
fun MorroStatusDot(
    armed: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(armed) {
        if (armed && !reduced) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(MorroMotion.SuccessPulseMillis))
        }
    }

    Box(
        modifier = modifier
            .size(10.dp)
            .drawBehind {
                if (pulse.value > 0f && pulse.value < 1f) {
                    val radius = size.minDimension / 2f + size.minDimension * 1.4f * pulse.value
                    drawCircle(
                        color = MorroColors.Honey.copy(alpha = (1f - pulse.value) * 0.5f),
                        radius = radius,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (armed) MorroColors.Honey else MorroColors.TextDisabled, CircleShape)
        )
    }
}
