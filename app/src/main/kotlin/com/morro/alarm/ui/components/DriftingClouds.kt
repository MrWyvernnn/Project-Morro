package com.morro.alarm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroColors
import kotlin.math.sin

private data class CloudSpec(
    val yFraction: Float,
    val startFraction: Float,
    val travelFraction: Float,
    val widthFraction: Float,
    val flip: Boolean,
)

/**
 * Seven clouds, from the top edge down into the lower half of the screen.
 *
 * The lower ones are the point: the sky should feel like it fills the screen rather than sitting in
 * a strip at the top. They lean on the sides at different heights, mid-size, so the copy column
 * still has room between them.
 */
private val CloudLayers = listOf(
    CloudSpec(yFraction = 0.005f, startFraction = 0.14f, travelFraction = 0.62f, widthFraction = 0.34f, flip = false),
    CloudSpec(yFraction = 0.045f, startFraction = 0.86f, travelFraction = 0.46f, widthFraction = 0.22f, flip = true),
    CloudSpec(yFraction = 0.085f, startFraction = 1.36f, travelFraction = 0.52f, widthFraction = 0.16f, flip = true),
    CloudSpec(yFraction = 0.38f, startFraction = 1.02f, travelFraction = 0.38f, widthFraction = 0.17f, flip = true),
    CloudSpec(yFraction = 0.56f, startFraction = 0.08f, travelFraction = 0.44f, widthFraction = 0.26f, flip = false),
    CloudSpec(yFraction = 0.72f, startFraction = 0.92f, travelFraction = 0.36f, widthFraction = 0.21f, flip = true),
    CloudSpec(yFraction = 0.88f, startFraction = 0.34f, travelFraction = 0.58f, widthFraction = 0.30f, flip = false),
    CloudSpec(yFraction = 0.965f, startFraction = 1.24f, travelFraction = 0.42f, widthFraction = 0.24f, flip = true),
)

/**
 * DriftingClouds — solid white, hand-drawn clouds moving slowly to the left.
 *
 * These are drawn clouds, not weather: a flat base and four round puffs, one continuous outline,
 * one flat fill. No blur, no transparency, no gradient — a cloud in a children's book rather than
 * a photographed sky. They run from the top edge down through the lower half, drifting left on one
 * clock so the layers cannot stutter against each other.
 *
 * Used on the intro screens only (splash, questions, permissions). The alarm setup and the
 * dashboard stay still: they are places you work, not places you arrive in.
 *
 * @param intensity kept for call-site compatibility; clouds are drawn solid, so this scales size
 *        presence rather than opacity.
 */
@Composable
fun DriftingClouds(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    durationMillis: Int = 36_000,
) {
    val reduced = rememberReducedMotion()

    // With reduced motion on, nothing here needs to tick: the drift is skipped entirely instead of
    // running an infinite transition whose value is then ignored.
    val t = if (reduced) 0.35f else rememberCloudDrift(durationMillis)
    val presence = intensity.coerceIn(0.4f, 1f)

    Canvas(modifier = modifier.fillMaxSize()) {
        CloudLayers.forEachIndexed { index, cloud ->
            val w = size.width * cloud.widthFraction * presence
            val h = w * 0.58f

            // Wrap from 1.55 → 0, so a cloud leaves on the left and re-enters on the right.
            val span = 1.55f
            val raw = cloud.startFraction - t * cloud.travelFraction
            val wrapped = ((raw % span) + span) % span
            val x = (wrapped - 0.28f) * size.width
            val sway = if (reduced) 0f else 4f * sin((t * 6.283f) + index)
            val y = size.height * cloud.yFraction + sway

            val rock = if (reduced) 0f else sin((t * 6.283f * 2f) + index) * 1.6f
            rotate(degrees = rock, pivot = Offset(x, y)) {
                drawPuffyCloud(
                    center = Offset(x, y),
                    width = w,
                    height = h,
                    color = MorroColors.CloudWhite,
                    flip = cloud.flip,
                )
            }
        }
    }
}

/**
 * A cloud: a flat base with four puffs, closed with one outline.
 *
 * The base is deliberately straight — that flat bottom is what makes the shape read as a drawn
 * cloud instead of a cluster of circles.
 */
private fun DrawScope.drawPuffyCloud(
    center: Offset,
    width: Float,
    height: Float,
    color: Color,
    flip: Boolean,
) {
    val left = center.x - width / 2f
    val right = center.x + width / 2f
    val baseY = center.y + height * 0.26f

    fun px(fraction: Float): Float = left + width * if (flip) (1f - fraction) else fraction

    val path = Path().apply {
        moveTo(px(0.04f), baseY)

        // Left puff, rising off the base.
        cubicTo(
            px(0.00f), baseY - height * 0.20f,
            px(0.04f), baseY - height * 0.44f,
            px(0.16f), baseY - height * 0.42f
        )
        // Second puff, the tallest.
        cubicTo(
            px(0.18f), baseY - height * 0.78f,
            px(0.40f), baseY - height * 0.92f,
            px(0.50f), baseY - height * 0.62f
        )
        // Third puff.
        cubicTo(
            px(0.62f), baseY - height * 0.90f,
            px(0.82f), baseY - height * 0.76f,
            px(0.84f), baseY - height * 0.44f
        )
        // Fourth puff, settling onto the base.
        cubicTo(
            px(0.95f), baseY - height * 0.40f,
            px(1.00f), baseY - height * 0.14f,
            px(0.96f), baseY
        )
        // Flat base.
        lineTo(px(0.04f), baseY)
        close()
    }

    drawPath(path = path, color = color)
}

@Composable
private fun rememberCloudDrift(durationMillis: Int): Float {
    val transition = rememberInfiniteTransition(label = "clouds")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing)
        ),
        label = "cloudDrift"
    )
    return progress
}
