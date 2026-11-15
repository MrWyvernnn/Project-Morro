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
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.theme.MorroColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * MorroGhostMascot — a round, soft, black-on-black ghost with dusty-rose eyes.
 *
 * The silhouette is built from curves only: a wide dome, bulging sides, and three big round
 * scallops along the hem. Nothing on the outline is straight, which is what makes it read as a
 * soft toy rather than a sheet with a zigzag bottom.
 *
 * Depth comes from three neutral layers, not from colour: a warm bloom behind it, a lifted body
 * gradient, and a rim light along the top edge. The eyes are the only dusty rose in the app;
 * everything else on the screen stays monochrome.
 *
 * Motion is a hop cycle with anticipation, stretch in the air and a landing squash, on top of a
 * slow breath. It is deliberately bouncy — the mascot is the one playful thing on the screen — but
 * the amplitudes stay small so it never feels like a loading spinner.
 */
@Composable
fun MorroGhostMascot(
    modifier: Modifier = Modifier,
    happy: Boolean = false
) {
    val reduced = rememberReducedMotionSafely()
    val transition = rememberInfiniteTransition(label = "ghostMascot")

    // One linear clock drives the hop; squash and lift are derived from its phase so they can
    // never desynchronise.
    val hop by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (happy) 1_900 else 2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ghostHop"
    )
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghostBreath"
    )

    val phase = if (reduced) 1f else hop
    val lift = if (reduced) 0f else hopLift(phase)
    val squash = if (reduced) 0f else hopSquash(phase)
    val breathe = if (reduced) 1f else 0.985f + 0.025f * breath
    val blink = if (reduced) 1f else blinkAmount(phase)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f

            // Bloom — warm, wide and faint, so the dark body still has a light behind it.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MorroColors.TextPrimary.copy(alpha = 0.06f),
                        MorroColors.Honey.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, size.height * 0.50f),
                    radius = size.width * 0.95f
                ),
                radius = size.width * 0.95f,
                center = Offset(centerX, size.height * 0.50f)
            )

            scale(
                scaleX = (1f + squash * 0.55f) * breathe,
                scaleY = (1f - squash * 0.75f) * breathe,
                pivot = Offset(centerX, size.height * 0.94f)
            ) {
                drawGhost(
                    canvasSize = size,
                    centerYOffset = lift,
                    bodyColor = MorroColors.SurfaceElevated,
                    eyeColor = MorroColors.EyeRose,
                    happy = happy,
                    blink = blink
                )
            }
        }
    }
}

/** Height of the hop in px for a 0..1 cycle: crouch, launch, float, land, settle. */
private fun hopLift(t: Float): Float = when {
    t < 0.16f -> 0f
    t < 0.60f -> -sin((PI.toFloat()) * (t - 0.16f) / 0.44f) * 13f
    else -> 0f
}

/** Positive squashes the body flat, negative stretches it tall. */
private fun hopSquash(t: Float): Float = when {
    t < 0.16f -> 0.055f * (t / 0.16f)
    t < 0.26f -> -0.045f * ((t - 0.16f) / 0.10f)
    t < 0.50f -> 0f
    t < 0.60f -> 0.07f * ((t - 0.50f) / 0.10f)
    else -> 0.07f * (1f - ((t - 0.60f) / 0.40f))
}

/** A two-frame blink near the end of each cycle. */
private fun blinkAmount(t: Float): Float = when {
    t in 0.86f..0.90f -> 0.12f
    t in 0.90f..0.94f -> 0.12f + 0.88f * ((t - 0.90f) / 0.04f)
    else -> 1f
}

/**
 * Draws the ghost inside [canvasSize].
 *
 * The body is one closed path of cubics: dome, two bulging sides, three round hem scallops, then
 * back up the other side. The fill is a single-neutral vertical tone shift, lit from above, with a
 * hairline rim so it has weight without turning into a white sticker.
 */
private fun DrawScope.drawGhost(
    canvasSize: Size,
    centerYOffset: Float = 0f,
    bodyColor: Color = MorroColors.SurfaceElevated,
    eyeColor: Color = MorroColors.EyeRose,
    happy: Boolean = true,
    blink: Float = 1f,
) {
    val centerX = canvasSize.width / 2f
    val bodyWidth = canvasSize.width * 0.84f
    val halfWidth = bodyWidth / 2f
    val headTop = canvasSize.height * 0.10f + centerYOffset
    val headHeight = bodyWidth * 0.58f
    val hemHeight = bodyWidth * 0.14f
    val bodyBottom = canvasSize.height * 0.90f + centerYOffset
    val left = centerX - halfWidth
    val right = centerX + halfWidth

    // The widest point sits a third of the way down: a pear, not a column.
    val waistY = headTop + headHeight * 0.92f
    val bulge = bodyWidth * 0.055f

    val body = Path().apply {
        moveTo(left, waistY)

        // Left of the dome.
        cubicTo(
            left - bulge * 0.5f, headTop + headHeight * 0.34f,
            left + bodyWidth * 0.10f, headTop,
            centerX, headTop
        )
        // Right of the dome.
        cubicTo(
            right - bodyWidth * 0.10f, headTop,
            right + bulge * 0.5f, headTop + headHeight * 0.34f,
            right, waistY
        )

        // Right flank: a soft outward belly down to the hem.
        cubicTo(
            right + bulge, waistY + (bodyBottom - waistY) * 0.28f,
            right + bulge * 0.7f, bodyBottom - hemHeight * 0.8f,
            right, bodyBottom - hemHeight * 0.45f
        )

        // Three round scallops, right to left. Each is a half circle hanging below the hem line.
        val scallop = bodyWidth / 3f
        var startX = right
        var index = 0
        while (index < 3) {
            val endX = startX - scallop
            val midX = (startX + endX) / 2f
            cubicTo(
                startX - scallop * 0.12f, bodyBottom + hemHeight * 0.55f,
                midX + scallop * 0.16f, bodyBottom + hemHeight * 0.55f,
                midX, bodyBottom + hemHeight * 0.42f
            )
            cubicTo(
                midX - scallop * 0.16f, bodyBottom + hemHeight * 0.55f,
                endX + scallop * 0.12f, bodyBottom + hemHeight * 0.55f,
                endX, bodyBottom - hemHeight * 0.45f
            )
            startX = endX
            index++
        }

        // Left flank back up to the start.
        cubicTo(
            left - bulge * 0.7f, bodyBottom - hemHeight * 0.8f,
            left - bulge, waistY + (bodyBottom - waistY) * 0.28f,
            left, waistY
        )
        close()
    }

    // Body: a single-neutral vertical tone shift, lit from above.
    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            colors = listOf(
                bodyColor,
                Color(0xFF100E0C),
                Color(0xFF070706)
            ),
            startY = headTop,
            endY = bodyBottom + hemHeight
        )
    )

    // Rim light: a hairline of the body's own light, fading toward the hem.
    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            colors = listOf(
                MorroColors.TextPrimary.copy(alpha = 0.36f),
                MorroColors.TextPrimary.copy(alpha = 0.12f),
                MorroColors.TextPrimary.copy(alpha = 0.04f)
            ),
            startY = headTop,
            endY = bodyBottom + hemHeight
        ),
        style = Stroke(width = 1.6.dp.toPx())
    )

    // Face — low and wide, centred on the dome.
    val eyeY = headTop + headHeight * 0.56f
    val eyeSpacing = bodyWidth * 0.185f
    val eyeWidth = bodyWidth * 0.082f
    val eyeHeight = bodyWidth * 0.125f * blink.coerceIn(0.08f, 1f)

    listOf(-1f, 1f).forEach { side ->
        val cx = centerX + side * eyeSpacing

        // Eye bloom: makes the face glow at small sizes.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(eyeColor.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(cx, eyeY),
                radius = eyeWidth * 3.4f
            ),
            radius = eyeWidth * 3.4f,
            center = Offset(cx, eyeY)
        )

        if (happy) {
            val path = Path().apply {
                moveTo(cx - eyeWidth, eyeY + eyeHeight * 0.30f)
                cubicTo(
                    cx - eyeWidth * 0.55f, eyeY - eyeHeight * 0.95f,
                    cx + eyeWidth * 0.55f, eyeY - eyeHeight * 0.95f,
                    cx + eyeWidth, eyeY + eyeHeight * 0.30f
                )
            }
            drawPath(
                path = path,
                color = eyeColor,
                style = Stroke(width = eyeHeight * 0.62f, cap = StrokeCap.Round)
            )
        } else {
            // Round pupils read softer than ovals at this size.
            drawCircle(
                color = eyeColor,
                radius = minOf(eyeWidth, eyeHeight) * 0.80f,
                center = Offset(cx, eyeY)
            )
        }
    }

    // Smile
    val smileWidth = bodyWidth * 0.20f
    val smileY = eyeY + bodyWidth * 0.125f
    val smile = Path().apply {
        moveTo(centerX - smileWidth / 2f, smileY)
        cubicTo(
            centerX - smileWidth * 0.25f, smileY + bodyWidth * 0.070f,
            centerX + smileWidth * 0.25f, smileY + bodyWidth * 0.070f,
            centerX + smileWidth / 2f, smileY
        )
    }
    drawPath(
        path = smile,
        color = MorroColors.Honey.copy(alpha = 0.55f),
        style = Stroke(width = bodyWidth * 0.018f, cap = StrokeCap.Round)
    )
}

/**
 * Local reduced-motion probe. The value is read once per composition, which is enough for a system
 * setting that rarely changes while the app is in front.
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
