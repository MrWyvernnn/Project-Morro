package com.morro.alarm.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.theme.morroColors

/**
 * LiveWaveform — Concentric amplitude rings drawn on a raw Canvas.
 *
 * Draws 8 concentric circles expanding outward from the center. A single `amplitude`
 * parameter (0.0–1.0) drives stroke width and opacity; the hue stays honey at every level, so
 * loudness reads as weight rather than as a colour change:
 *   - Stroke width: 1dp → 4dp (mapped per-ring, outer rings thicker at high amplitude)
 *   - Opacity: 0.5 → 1.0, outer rings fading first at low amplitude
 *
 * All drawing is stateless — pass `amplitude` from your view model / state holder.
 * Reduced motion: when amplitude is 0, rings are static; when > 0, they pulse.
 *
 * Usage:
 * ```
 * var amplitude by remember { mutableFloatStateOf(0f) }
 * LiveWaveform(
 *     modifier = Modifier.size(200.dp),
 *     amplitude = amplitude
 * )
 * ```
 *
 * @param modifier outer modifier (use size() to set the waveform diameter)
 * @param amplitude 0.0 (silent) to 1.0 (peak) — drives color shift and stroke thickness
 */
@Composable
fun LiveWaveform(
    modifier: Modifier = Modifier,
    amplitude: Float = 0f
) {
    val colors = morroColors()
    val density = LocalDensity.current.density

    // 8 rings, evenly spaced outward from center
    val ringCount = 8
    val maxRadiusDp = 120f // outermost ring at 120dp radius
    val strokeMinDp = 1f
    val strokeMaxDp = 4f

    Canvas(
        modifier = modifier
            .size(240.dp) // 2 × maxRadius + padding
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        for (i in 0 until ringCount) {
            // Normalized ring position: 0 (inner) → 1 (outer)
            val ringProgress = i.toFloat() / (ringCount - 1)

            // Radius for this ring (dp → px)
            val ringRadiusDp = maxRadiusDp * ringProgress
            val ringRadiusPx = ringRadiusDp * density

            // One honey tone for every ring; loudness is carried by width and opacity, not hue.
            val color = colors.honey

            // Stroke width: outer rings get thicker at high amplitude
            // Base stroke scales with ring position + amplitude
            val strokeWidthDp = strokeMinDp + (strokeMaxDp - strokeMinDp) * ringProgress * amplitude
            val strokeWidthPx = strokeWidthDp * density

            // Opacity fades slightly for outer rings at low amplitude
            val alpha = 0.5f + 0.5f * amplitude * (1f - ringProgress * 0.3f)

            drawArc(
                color = color.copy(alpha = alpha),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidthPx),
                topLeft = Offset(centerX - ringRadiusPx, centerY - ringRadiusPx),
                size = Size(ringRadiusPx * 2f, ringRadiusPx * 2f)
            )
        }
    }
}
