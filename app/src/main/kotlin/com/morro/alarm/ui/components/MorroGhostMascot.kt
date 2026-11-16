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
 * MorroGhostMascot — a round, soft, black-on-black ghost with dusty-rose eyes.
 *
 * Silhouette: curves only. A wide dome, two convex flanks and three deep round hem scallops, so
 * there is not one straight edge on the whole shape. The body is a single closed path, filled with
 * a one-neutral tone shift and closed with a 1.6dp rim — no bloom, no halo, no radial glow behind
 * it. On a black screen the shape itself is the light; anything behind it would only make it fuzzy.
 *
 * Motion: a hop with anticipation, airtime, a landing squash and a recovery, plus a slow breath,
 * a two-frame blink and a light side-to-side sway with a matching tilt, so the mascot is never
 * perfectly still. Amplitudes stay small enough to read as alive rather than as a loading spinner.
 */
@Composable
fun MorroGhostMascot(
    modifier: Modifier = Modifier,
    happy: Boolean = false
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "ghostMascot")

    // One linear clock drives the hop; squash, lift and sway are derived from its phase so they
    // can never desynchronise.
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
    val sway = if (reduced) 0f else sin(phase * 2f * PI.toFloat()) * 2.4f
    val tilt = if (reduced) 0f else sin(phase * 2f * PI.toFloat()) * 1.4f
    val breathe = if (reduced) 1f else 0.985f + 0.025f * breath
    val blink = if (reduced) 1f else blinkAmount(phase)

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
    t < 0.60f -> -sin((PI.toFloat()) * (t - 0.16f) / 0.44f) * 17f
    else -> 0f
}

/** Positive squashes the body flat, negative stretches it tall. */
private fun hopSquash(t: Float): Float = when {
    t < 0.16f -> 0.06f * (t / 0.16f)
    t < 0.26f -> -0.05f * ((t - 0.16f) / 0.10f)
    t < 0.50f -> 0f
    t < 0.60f -> 0.075f * ((t - 0.50f) / 0.10f)
    else -> 0.075f * (1f - ((t - 0.60f) / 0.40f))
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
 * One closed path of cubics: dome, flanks, three round scallops. The widest point is a third of
 * the way down, so the body is a pear rather than a column, and each scallop is a near-half-circle
 * so the hem reads as three soft bumps instead of a zigzag.
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
    val bodyWidth = canvasSize.width * 0.86f
    val halfWidth = bodyWidth / 2f
    val headTop = canvasSize.height * 0.09f + centerYOffset
    val headHeight = bodyWidth * 0.64f
    val hemHeight = bodyWidth * 0.17f
    val bodyBottom = canvasSize.height * 0.90f + centerYOffset
    val left = centerX - halfWidth
    val right = centerX + halfWidth

    val waistY = headTop + headHeight * 0.92f
    val bulge = bodyWidth * 0.07f

    val body = Path().apply {
        moveTo(left, waistY)

        // Left of the dome: a wide, continuous shoulder with no corner at the top.
        cubicTo(
            left - bulge * 0.35f, headTop + headHeight * 0.30f,
            left + bodyWidth * 0.09f, headTop,
            centerX, headTop
        )
        // Right of the dome.
        cubicTo(
            right - bodyWidth * 0.09f, headTop,
            right + bulge * 0.35f, headTop + headHeight * 0.30f,
            right, waistY
        )

        // Right flank: a full round belly down to the hem.
        cubicTo(
            right + bulge, waistY + (bodyBottom - waistY) * 0.30f,
            right + bulge * 0.72f, bodyBottom - hemHeight * 0.75f,
            right, bodyBottom - hemHeight * 0.42f
        )

        // Three deep scallops, right to left, each one a near half circle.
        val scallop = bodyWidth / 3f
        var startX = right
        var index = 0
        while (index < 3) {
            val endX = startX - scallop
            val midX = (startX + endX) / 2f
            cubicTo(
                startX - scallop * 0.10f, bodyBottom + hemHeight * 0.72f,
                midX + scallop * 0.16f, bodyBottom + hemHeight * 0.74f,
                midX, bodyBottom + hemHeight * 0.56f
            )
            cubicTo(
                midX - scallop * 0.16f, bodyBottom + hemHeight * 0.74f,
                endX + scallop * 0.10f, bodyBottom + hemHeight * 0.72f,
                endX, bodyBottom - hemHeight * 0.42f
            )
            startX = endX
            index++
        }

        // Left flank back up to the start.
        cubicTo(
            left - bulge * 0.72f, bodyBottom - hemHeight * 0.75f,
            left - bulge, waistY + (bodyBottom - waistY) * 0.30f,
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

    // Rim: the body's own edge catching light. A hairline, not a halo.
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

    // Face — wide, round and a little low on the dome.
    val eyeY = headTop + headHeight * 0.55f
    val eyeSpacing = bodyWidth * 0.19f
    val eyeWidth = bodyWidth * 0.094f
    val eyeHeight = bodyWidth * 0.135f * blink.coerceIn(0.08f, 1f)

    listOf(-1f, 1f).forEach { side ->
        val cx = centerX + side * eyeSpacing

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
            // Round pupils: the rounder the eye, the softer the face.
            drawCircle(
                color = eyeColor,
                radius = minOf(eyeWidth, eyeHeight) * 0.88f,
                center = Offset(cx, eyeY)
            )
        }
    }

    // Smile, drawn with one curve so it stays soft at every size.
    val smileWidth = bodyWidth * 0.205f
    val smileY = eyeY + bodyWidth * 0.13f
    val smile = Path().apply {
        moveTo(centerX - smileWidth / 2f, smileY)
        cubicTo(
            centerX - smileWidth * 0.22f, smileY + bodyWidth * 0.062f,
            centerX + smileWidth * 0.22f, smileY + bodyWidth * 0.062f,
            centerX + smileWidth / 2f, smileY
        )
    }
    drawPath(
        path = smile,
        color = MorroColors.Honey.copy(alpha = 0.55f),
        style = Stroke(width = bodyWidth * 0.017f, cap = StrokeCap.Round)
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
