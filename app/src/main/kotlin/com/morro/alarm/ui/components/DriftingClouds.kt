package com.morro.alarm.ui.components

import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
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
 * Three clouds along the top edge, mirroring each other so the sky is not a row of copies.
 *
 * They are confined to that band on purpose: white clouds are opaque now, and the intro screens are
 * full of small off-white text. A cloud crossing the reading column would make that text
 * unreadable, so the sky clips to the top of the screen and the content stays clear.
 */
private val CloudLayers = listOf(
    CloudSpec(yFraction = 0.005f, startFraction = 0.14f, travelFraction = 0.62f, widthFraction = 0.36f, flip = false),
    CloudSpec(yFraction = 0.048f, startFraction = 0.86f, travelFraction = 0.46f, widthFraction = 0.24f, flip = true),
    CloudSpec(yFraction = 0.082f, startFraction = 1.36f, travelFraction = 0.52f, widthFraction = 0.17f, flip = true),
)

/**
 * DriftingClouds — solid white, hand-drawn clouds moving slowly to the left.
 *
 * These are drawn clouds, not weather: a flat base and four round puffs, one continuous outline,
 * one flat fill. No blur, no transparency, no gradient — a cloud in a children's book rather than
 * a photographed sky. They sit along the top edge, cropped by it, so they read as a drawn sky
 * border and never cross the copy; the whole sky runs on one clock so the layers cannot stutter.
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
    durationMillis: Int = 180_000,
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "clouds")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing)
        ),
        label = "cloudDrift"
    )

    val t = if (reduced) 0.35f else progress
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

            drawPuffyCloud(
                center = Offset(x, y),
                width = w,
                height = h,
                color = MorroColors.TextPrimary,
                flip = cloud.flip,
            )
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
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
