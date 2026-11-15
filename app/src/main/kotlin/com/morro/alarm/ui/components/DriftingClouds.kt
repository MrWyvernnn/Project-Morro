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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.theme.MorroColors

private data class CloudSpec(
    val yFraction: Float,
    val startFraction: Float,
    val travelFraction: Float,
    val widthFraction: Float,
    val alpha: Float,
)

// Two depths: the far layer is smaller, fainter and slower, so the sky reads as a space rather than
// a wallpaper. All three layers travel left and wrap around, so there is never a visible seam.
private val CloudLayers = listOf(
    CloudSpec(yFraction = 0.16f, startFraction = 0.12f, travelFraction = 0.55f, widthFraction = 0.34f, alpha = 0.055f),
    CloudSpec(yFraction = 0.27f, startFraction = 0.86f, travelFraction = 0.42f, widthFraction = 0.46f, alpha = 0.040f),
    CloudSpec(yFraction = 0.62f, startFraction = 0.44f, travelFraction = 0.70f, widthFraction = 0.56f, alpha = 0.035f),
    CloudSpec(yFraction = 0.78f, startFraction = 0.95f, travelFraction = 0.36f, widthFraction = 0.30f, alpha = 0.030f),
    CloudSpec(yFraction = 0.08f, startFraction = 0.63f, travelFraction = 0.28f, widthFraction = 0.24f, alpha = 0.045f),
)

/**
 * DriftingClouds — a background layer of soft clouds moving slowly left.
 *
 * Used on the intro screens only (splash, questions, permissions). The alarm setup and the
 * dashboard stay still: they are places you work, not places you arrive in.
 *
 * One clock runs the whole sky, so the layers never stutter against each other. Altitude is fixed
 * with a gentle sine so the clouds rise and fall a few pixels, which is what stops the motion
 * reading as a sliding texture. Reduced motion freezes the sky.
 *
 * @param intensity 0..1 multiplier for cloud opacity. Use below 1 behind dense copy.
 */
@Composable
fun DriftingClouds(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    durationMillis: Int = 150_000,
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

    Canvas(modifier = modifier.fillMaxSize()) {
        CloudLayers.forEachIndexed { index, cloud ->
            val w = size.width * cloud.widthFraction
            val h = w * 0.46f

            // Position with wrap: x ranges from -0.4w to 1.15w, entering from the right.
            val span = 1.55f
            val raw = cloud.startFraction - t * cloud.travelFraction
            val wrapped = ((raw % span) + span) % span
            val x = (wrapped - 0.28f) * size.width
            val sway = if (reduced) 0f else 3f * kotlin.math.sin((t * 6.283f) + index)
            val y = size.height * cloud.yFraction + sway

            drawCloud(
                center = Offset(x, y),
                width = w,
                height = h,
                color = MorroColors.TextPrimary.copy(alpha = cloud.alpha * intensity)
            )
        }
    }
}

/**
 * A cloud is five overlapping ellipses on one baseline: three crowns and two shoulders. Drawn flat
 * so it stays a soft shape rather than a puff of blurred circles.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    center: Offset,
    width: Float,
    height: Float,
    color: Color,
) {
    val baseY = center.y + height * 0.22f
    drawOval(
        color = color,
        topLeft = Offset(center.x - width * 0.50f, baseY - height * 0.34f),
        size = Size(width * 1.0f, height * 0.66f)
    )
    drawOval(
        color = color,
        topLeft = Offset(center.x - width * 0.34f, baseY - height * 0.62f),
        size = Size(width * 0.52f, height * 0.86f)
    )
    drawOval(
        color = color,
        topLeft = Offset(center.x - width * 0.06f, baseY - height * 0.74f),
        size = Size(width * 0.44f, height * 0.96f)
    )
    drawOval(
        color = color,
        topLeft = Offset(center.x + width * 0.20f, baseY - height * 0.44f),
        size = Size(width * 0.32f, height * 0.70f)
    )
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
