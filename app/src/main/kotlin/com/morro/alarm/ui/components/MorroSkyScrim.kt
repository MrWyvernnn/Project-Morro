package com.morro.alarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.morro.alarm.ui.theme.MorroColors

/**
 * The veil between the sky and the copy.
 *
 * The clouds are solid paper-white shapes with no transparency, so any text laid over them fights
 * for the same value and goes soft. This scrim is opaque across the reading column and feathers out
 * at both edges: the clouds stay in the margins, and a long question, a headline, or a progress
 * label always sits on black.
 *
 * One definition so the intro screens cannot drift apart. Applied over [DriftingClouds], under the
 * content.
 */
@Composable
fun MorroSkyScrim(
    modifier: Modifier = Modifier,
    alpha: Float = 0.94f,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.08f to MorroColors.Canvas.copy(alpha = alpha),
                    0.92f to MorroColors.Canvas.copy(alpha = alpha),
                    1f to Color.Transparent
                )
            )
    )
}
