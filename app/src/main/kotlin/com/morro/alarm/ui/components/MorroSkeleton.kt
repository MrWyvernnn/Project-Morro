package com.morro.alarm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroColors

/**
 * A placeholder block for a wait that is actually happening — camera binding, memo finalising.
 *
 * It is deliberately not used as a decorative loading state on local reads: prefs answer in a
 * frame, and a fake skeleton would make the app measurably slower.
 *
 * With reduced motion it becomes a flat block instead of a travelling highlight.
 */
@Composable
fun MorroSkeleton(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 12,
) {
    val reduced = rememberReducedMotion()

    val highlight = if (reduced) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(MorroMotion.ShimmerMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "skeletonShimmer"
        )
        value
    }

    val brush = Brush.linearGradient(
        colors = listOf(
            MorroColors.SurfaceCard,
            MorroColors.SurfaceLow,
            MorroColors.SurfaceCard
        ),
        start = androidx.compose.ui.geometry.Offset(highlight * 600f - 300f, 0f),
        end = androidx.compose.ui.geometry.Offset(highlight * 600f, 220f)
    )

    val surface = Modifier
        .clip(RoundedCornerShape(cornerRadius.dp))
        .then(
            if (reduced) Modifier.background(MorroColors.SurfaceCard)
            else Modifier.background(brush)
        )

    Box(modifier = modifier.then(surface))
}
