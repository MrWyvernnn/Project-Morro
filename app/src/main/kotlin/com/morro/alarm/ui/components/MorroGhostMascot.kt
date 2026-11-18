package com.morro.alarm.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.theme.MorroColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * MorroGhostMascot — a slim, floating ghost with dusty-rose eyes.
 *
 * Silhouette: taller than it is wide, and still curves only. A narrow dome, two convex flanks and
 * three deep round hem scallops. The body is one closed path filled with a single-neutral tone
 * shift and closed with a 1.4dp rim — no bloom, no halo, nothing glowing behind it.
 *
 * Motion: it floats. A slow rise and fall with a second, quicker bob on top, a side-to-side sway
 * with a matching tilt, a breathing stretch, a two-frame blink whose eyes follow the drift, and a
 * hem that ripples as it hovers. No landing squash — the mascot hovers rather than hops, which
 * reads calmer next to the copy it sits above.
 *
 * The silhouette is slim from the middle: both flanks are pulled inward just below the dome, then
 * return to the hem, so the body reads as a waist rather than as a cone.
 */
@Composable
fun MorroGhostMascot(
    modifier: Modifier = Modifier,
    happy: Boolean = false
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "ghostMascot")

    // One linear clock drives the float; sway, tilt and blink are derived from its phase.
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (happy) 3_000 else 4_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ghostFloat"
    )
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghostBreath"
    )

    val phase = if (reduced) 0f else drift
    val floatFraction = if (reduced) 0f else floatOffset(phase)
    val angle = phase * 2f * PI.toFloat()
    val sway = if (reduced) 0f else sin(angle) * 3.6f
    val tilt = if (reduced) 0f else sin(angle) * 2.2f + sin(angle * 2f) * 0.6f
    val stretch = if (reduced) 1f else 0.975f + 0.045f * breath
    val blink = if (reduced) 1f else blinkAmount(phase)
    // Eyes travel with the drift: the mascot looks the way it leans.
    val gaze = if (reduced) 0f else sin(angle)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = sway
                    rotationZ = tilt
                }
        ) {
            val centerX = size.width / 2f

            scale(
                scaleX = (2f - stretch) * stretch,
                scaleY = stretch,
                pivot = Offset(centerX, size.height * 0.94f)
            ) {
                drawGhost(
                    canvasSize = size,
                    centerYOffset = size.height * floatFraction,
                    bodyColor = MorroColors.SurfaceElevated,
                    eyeColor = MorroColors.EyeRose,
                    happy = happy,
                    blink = blink,
                    gaze = gaze,
                    hemWave = phase
                )
            }
        }
    }
}

/**
 * Vertical hover, as a fraction of the canvas height. Positive is down.
 *
 * Two sine waves that both complete whole cycles per loop: the slow breath of the hover, plus a
 * shorter bob so the float never reads as a single mechanical pulse.
 */
private fun floatOffset(t: Float): Float =
    -sin(t * 2f * PI.toFloat()) * 0.048f - sin(t * 4f * PI.toFloat()) * 0.010f

/** A two-frame blink, once per float cycle. */
private fun blinkAmount(t: Float): Float = when {
    t in 0.72f..0.76f -> 0.12f
    t in 0.76f..0.80f -> 0.12f + 0.88f * ((t - 0.76f) / 0.04f)
    else -> 1f
}

/**
 * Draws the ghost inside [canvasSize].
 *
 * The body is deliberately narrow: 0.68 of the canvas width against a dome that is 0.82 of that
 * width tall, so the shape reads as slim rather than squat. Every edge is a cubic; each hem scallop
 * is a near half-circle.
 */
private fun DrawScope.drawGhost(
    canvasSize: Size,
    centerYOffset: Float = 0f,
    bodyColor: Color = MorroColors.SurfaceElevated,
    eyeColor: Color = MorroColors.EyeRose,
    happy: Boolean = true,
    blink: Float = 1f,
    gaze: Float = 0f,
    hemWave: Float = 0f,
) {
    val centerX = canvasSize.width / 2f
    val bodyWidth = canvasSize.width * 0.68f
    val halfWidth = bodyWidth / 2f
    val headTop = canvasSize.height * 0.08f + centerYOffset
    val headHeight = bodyWidth * 0.86f
    val hemHeight = bodyWidth * 0.16f
    val bodyBottom = canvasSize.height * 0.92f + centerYOffset
    val left = centerX - halfWidth
    val right = centerX + halfWidth

    val waistY = headTop + headHeight * 0.94f
    val bulge = bodyWidth * 0.055f
    // Slim from the middle: the flanks sit this far inside the dome's width at the waist.
    val pinch = bodyWidth * 0.075f

    val body = Path().apply {
        moveTo(left, waistY)

        // Left of the dome.
        cubicTo(
            left - bulge * 0.35f, headTop + headHeight * 0.30f,
            left + bodyWidth * 0.10f, headTop,
            centerX, headTop
        )
        // Right of the dome.
        cubicTo(
            right - bodyWidth * 0.10f, headTop,
            right + bulge * 0.35f, headTop + headHeight * 0.30f,
            right, waistY
        )

        // Right flank: in at the waist, then back out to the hem.
        cubicTo(
            right - pinch, waistY + (bodyBottom - waistY) * 0.24f,
            right - pinch, bodyBottom - hemHeight * 1.10f,
            right, bodyBottom - hemHeight * 0.42f
        )

        // Three deep scallops, right to left.
        val scallop = bodyWidth / 3f
        var startX = right
        var index = 0
        while (index < 3) {
            val endX = startX - scallop
            val midX = (startX + endX) / 2f
            // Each scallop laps slightly out of phase, so the hem ripples instead of hanging still.
            val ripple = sin(hemWave * 2f * PI.toFloat() + index * 1.7f) * hemHeight * 0.14f
            cubicTo(
                startX - scallop * 0.10f, bodyBottom + hemHeight * 0.70f + ripple,
                midX + scallop * 0.16f, bodyBottom + hemHeight * 0.72f + ripple,
                midX, bodyBottom + hemHeight * 0.54f
            )
            cubicTo(
                midX - scallop * 0.16f, bodyBottom + hemHeight * 0.72f + ripple,
                endX + scallop * 0.10f, bodyBottom + hemHeight * 0.70f + ripple,
                endX, bodyBottom - hemHeight * 0.42f
            )
            startX = endX
            index++
        }

        // Left flank back up, mirroring the waist pinch.
        cubicTo(
            left + pinch, bodyBottom - hemHeight * 1.10f,
            left + pinch, waistY + (bodyBottom - waistY) * 0.24f,
            left, waistY
        )
        close()
    }

    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            colors = listOf(
                bodyColor,
                Color(0xFF0E0C0A),
                Color(0xFF060605)
            ),
            startY = headTop,
            endY = bodyBottom + hemHeight
        )
    )

    // Rim: the body's own edge catching light, not a glow.
    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            colors = listOf(
                MorroColors.TextPrimary.copy(alpha = 0.30f),
                MorroColors.TextPrimary.copy(alpha = 0.10f),
                MorroColors.TextPrimary.copy(alpha = 0.03f)
            ),
            startY = headTop,
            endY = bodyBottom + hemHeight
        ),
        style = Stroke(width = 1.4.dp.toPx())
    )

    // Face, a little high on the dome so the slim body keeps its proportions.
    val eyeY = headTop + headHeight * 0.52f
    val eyeSpacing = bodyWidth * 0.19f
    val eyeWidth = bodyWidth * 0.10f
    val eyeHeight = bodyWidth * 0.145f * blink.coerceIn(0.08f, 1f)

    val eyeShift = bodyWidth * 0.030f * gaze

    listOf(-1f, 1f).forEach { side ->
        val cx = centerX + side * eyeSpacing + eyeShift

        if (happy) {
            val path = Path().apply {
                moveTo(cx - eyeWidth, eyeY + eyeHeight * 0.32f)
                cubicTo(
                    cx - eyeWidth * 0.55f, eyeY - eyeHeight * 1.05f,
                    cx + eyeWidth * 0.55f, eyeY - eyeHeight * 1.05f,
                    cx + eyeWidth, eyeY + eyeHeight * 0.32f
                )
            }
            drawPath(
                path = path,
                color = eyeColor,
                style = Stroke(width = eyeHeight * 0.60f, cap = StrokeCap.Round)
            )
        } else {
            drawCircle(
                color = eyeColor,
                radius = minOf(eyeWidth, eyeHeight) * 0.88f,
                center = Offset(cx, eyeY)
            )
        }
    }

    // Smile.
    val smileWidth = bodyWidth * 0.22f
    val smileY = eyeY + bodyWidth * 0.15f
    val smile = Path().apply {
        moveTo(centerX - smileWidth / 2f, smileY)
        cubicTo(
            centerX - smileWidth * 0.22f, smileY + bodyWidth * 0.068f,
            centerX + smileWidth * 0.22f, smileY + bodyWidth * 0.068f,
            centerX + smileWidth / 2f, smileY
        )
    }
    drawPath(
        path = smile,
        color = MorroColors.Honey.copy(alpha = 0.55f),
        style = Stroke(width = bodyWidth * 0.020f, cap = StrokeCap.Round)
    )
}

/** Reads the system animator scale once; zero means the user asked for no animation. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
