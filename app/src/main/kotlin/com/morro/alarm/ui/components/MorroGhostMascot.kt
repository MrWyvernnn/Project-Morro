package com.morro.alarm.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.theme.MorroColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * MorroGhostMascot — the friendly ghost: white body, pink eyes, a soft smile and a blush.
 *
 * White on pure black is what makes it read at 140dp as well as 280dp: the silhouette does the
 * work, the pink is only the face. One infinite transition drives both the float and the breath,
 * so the two never drift apart.
 *
 * @param happy when true the smile lifts and the eyes curve up — used on welcoming screens.
 */
@Composable
fun MorroGhostMascot(
    modifier: Modifier = Modifier,
    happy: Boolean = false
) {
    val reduced = rememberReducedMotionSafely()
    val transition = rememberInfiniteTransition(label = "ghostMascot")

    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghostBreath"
    )
    val bob by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (happy) 2_200 else 3_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghostBob"
    )

    val floatPx = if (reduced) 0f else bob * 5f
    val breathe = if (reduced) 1f else 0.985f + 0.03f * breath

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f

            // Pink halo, low and wide: light source, never a coloured panel.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MorroColors.AccentBubbly.copy(alpha = 0.16f),
                        MorroColors.AccentTactical.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, height * 0.52f),
                    radius = width * 1.05f
                ),
                radius = width * 1.05f,
                center = Offset(centerX, height * 0.52f)
            )

            scale(scaleX = breathe, scaleY = breathe, pivot = Offset(centerX, height * 0.55f)) {
                drawGhost(
                    canvasSize = Size(width, height),
                    centerYOffset = floatPx,
                    happy = happy
                )
            }
        }
    }
}

/**
 * Draws the ghost inside [canvasSize]. Body is white with a scalloped hem; eyes and smile are
 * pink so the face stays warm on an AMOLED canvas.
 */
fun DrawScope.drawGhost(
    canvasSize: Size,
    centerYOffset: Float = 0f,
    bodyColor: Color = Color.White,
    eyeColor: Color = MorroColors.AccentBubbly,
    happy: Boolean = true,
) {
    val centerX = canvasSize.width / 2f
    val bodyWidth = canvasSize.width * 0.78f
    val halfWidth = bodyWidth / 2f
    val headTop = canvasSize.height * 0.10f + centerYOffset
    val capHeight = bodyWidth * 0.52f
    val hemHeight = bodyWidth * 0.10f
    val bodyBottom = canvasSize.height * 0.92f + centerYOffset
    val left = centerX - halfWidth
    val right = centerX + halfWidth

    val body = Path().apply {
        moveTo(left, headTop + capHeight)
        cubicTo(left, headTop, left + bodyWidth * 0.16f, headTop, centerX, headTop)
        cubicTo(right - bodyWidth * 0.16f, headTop, right, headTop, right, headTop + capHeight)
        lineTo(right, bodyBottom - hemHeight)

        // Three soft scallops, right to left.
        val scallop = bodyWidth / 3f
        var startX = right
        var dipDown = true
        var index = 0
        while (index < 3) {
            val endX = startX - scallop
            val controlY = if (dipDown) bodyBottom else bodyBottom - hemHeight * 0.55f
            quadraticTo(startX - scallop / 2f, controlY, endX, bodyBottom - hemHeight * 0.35f)
            startX = endX
            dipDown = !dipDown
            index++
        }

        lineTo(left, bodyBottom - hemHeight)
        close()
    }

    drawPath(path = body, color = bodyColor)

    // Face
    val eyeY = headTop + capHeight * 0.52f
    val eyeSpacing = bodyWidth * 0.18f
    val eyeWidth = bodyWidth * 0.085f
    val eyeHeight = bodyWidth * 0.125f

    if (happy) {
        // Curved, closed-happy eyes: two short arcs, read instantly at any size.
        listOf(-1f, 1f).forEach { side ->
            val cx = centerX + side * eyeSpacing
            val path = Path().apply {
                moveTo(cx - eyeWidth, eyeY + eyeHeight * 0.35f)
                quadraticTo(cx, eyeY - eyeHeight * 0.55f, cx + eyeWidth, eyeY + eyeHeight * 0.35f)
            }
            drawPath(path = path, color = eyeColor, style = Stroke(width = eyeWidth * 0.62f, cap = StrokeCap.Round))
        }
    } else {
        listOf(-1f, 1f).forEach { side ->
            drawOval(
                color = eyeColor,
                topLeft = Offset(
                    centerX + side * eyeSpacing - eyeWidth / 2f,
                    eyeY - eyeHeight / 2f
                ),
                size = Size(eyeWidth, eyeHeight)
            )
        }
    }

    // Soft smile.
    val smileWidth = bodyWidth * 0.20f
    val smileY = eyeY + bodyWidth * 0.11f
    val smile = Path().apply {
        moveTo(centerX - smileWidth / 2f, smileY)
        quadraticTo(centerX, smileY + bodyWidth * 0.075f, centerX + smileWidth / 2f, smileY)
    }
    drawPath(
        path = smile,
        color = eyeColor.copy(alpha = 0.85f),
        style = Stroke(width = bodyWidth * 0.022f, cap = StrokeCap.Round)
    )

    // Blush, at 32% so it glows without reading as a second eye.
    listOf(-1f, 1f).forEach { side ->
        drawCircle(
            color = MorroColors.AccentBubbly.copy(alpha = 0.30f),
            radius = bodyWidth * 0.065f,
            center = Offset(
                centerX + side * bodyWidth * 0.29f,
                eyeY + bodyWidth * 0.10f
            )
        )
    }
}

/**
 * Local reduced-motion probe. Kept private so this component has no dependency on the onboarding
 * package; the value is read once per composition, which is enough for a system setting.
 */
@Composable
private fun rememberReducedMotionSafely(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/** Small helper for future illustrations: a point on a cubic bezier. */
internal fun bezierPoint(t: Float, a: Offset, b: Offset, c: Offset, d: Offset): Offset {
    val u = 1f - t
    val x = u * u * u * a.x + 3f * u * u * t * b.x + 3f * u * t * t * c.x + t * t * t * d.x
    val y = u * u * u * a.y + 3f * u * u * t * b.y + 3f * u * t * t * c.y + t * t * t * d.y
    return Offset(x, y)
}

/** Unused-but-exported wobble used by the splash ribbon so the curve feels hand-drawn. */
internal fun wobble(phase: Float): Float = sin(phase * 2f * PI.toFloat()) * 0.5f
