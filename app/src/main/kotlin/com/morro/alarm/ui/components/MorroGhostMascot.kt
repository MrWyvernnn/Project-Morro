package com.morro.alarm.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroSpring
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * MorroGhostMascot — an adorable, friendly, living ghost mascot.
 *
 * Silhouette:
 *  - Plush, rounded dome head tapering out gracefully into a cuddly, bell-shaped floating skirt.
 *  - Two sweet floating ghost nub-arms that bob with gentle out-of-phase secondary motion.
 *  - Four wavy, fluid scalloped folds at the hem with dynamic harmonic cloth ripples.
 *
 * Face & Character:
 *  - Soulful, glossy dark-ink eyes with lively dual specular glints that blink naturally.
 *  - Warm rosy cheeks (MorroColors.EyeRose) that breathe and blush deeper on touch.
 *  - Sweet curved smile that perks up with breathing and joy.
 *  - Tactile marshmallow squish and playful wink with MorroSpring.FloatSpringPlayful on tap.
 */
@Composable
fun MorroGhostMascot(
    modifier: Modifier = Modifier,
    happy: Boolean = true,
    interactive: Boolean = true,
    gazeBiasY: Float = 0f,
) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "ghostMascot")

    // Interactive tap squish, blush, and wink response
    val tapSquish = remember { Animatable(1f) }
    val tapBlush = remember { Animatable(0f) }
    val tapWink = remember { Animatable(0f) }

    // Primary hover cycle (3.2 seconds for serene, lively floating)
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (happy) 3_200 else 4_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ghostFloat"
    )

    // Gentle breathing cycle
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghostBreath"
    )

    val phase = if (reduced) 0f else drift
    val floatFraction = if (reduced) 0f else floatOffset(phase)
    val angle = phase * 2f * PI.toFloat()

    // Smooth lateral sway and pitch inertia
    val sway = if (reduced) 0f else sin(angle) * 3.5f
    val floatVelocity = -cos(angle) * 0.045f - 2f * cos(angle * 2f) * 0.012f
    val tilt = if (reduced) 0f else sin(angle) * 2.0f + floatVelocity * 12f

    // Squash & stretch (volume-preserving)
    val baseStretch = if (reduced) 1f else 0.98f + 0.04f * breath
    val currentStretch = (baseStretch * tapSquish.value).coerceIn(0.82f, 1.25f)

    // Natural blinking + tap wink
    val blink = if (reduced) 1f else blinkAmount(phase, tapWink.value)
    // Eyes drift with micro-saccades
    val gaze = if (reduced) 0f else sin(angle) * 0.85f + sin(angle * 3f) * 0.15f

    val tapModifier = if (interactive && !reduced) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                tapSquish.snapTo(0.84f)
                tapSquish.animateTo(1f, MorroSpring.FloatSpringPlayful)
            }
            scope.launch {
                tapBlush.snapTo(1f)
                tapBlush.animateTo(0f, tween(800, easing = MorroMotion.EnterEasing))
            }
            scope.launch {
                tapWink.animateTo(1f, tween(120))
                tapWink.animateTo(0f, tween(180))
            }
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.then(tapModifier)) {
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
                scaleX = (2f - currentStretch) * currentStretch,
                scaleY = currentStretch,
                pivot = Offset(centerX, size.height * 0.92f)
            ) {
                drawGhost(
                    canvasSize = size,
                    centerYOffset = size.height * floatFraction,
                    happy = happy,
                    blink = blink,
                    gaze = gaze,
                    gazeY = gazeBiasY,
                    hemWave = phase,
                    breathProgress = if (reduced) 0.5f else breath,
                    blushBoost = tapBlush.value,
                    winkAmount = tapWink.value
                )
            }
        }
    }
}

/** Vertical hover with dual harmonic bobbing. */
private fun floatOffset(t: Float): Float =
    -sin(t * 2f * PI.toFloat()) * 0.045f - sin(t * 4f * PI.toFloat()) * 0.012f

/** Two-frame natural blink with occasional soft flutter. */
private fun blinkAmount(t: Float, extraWink: Float = 0f): Float {
    if (extraWink > 0f) {
        return (1f - extraWink * 0.92f).coerceIn(0.08f, 1f)
    }
    return when {
        t in 0.70f..0.74f -> 0.10f
        t in 0.74f..0.78f -> 0.10f + 0.90f * ((t - 0.74f) / 0.04f)
        t in 0.86f..0.88f -> 0.35f
        t in 0.88f..0.90f -> 0.35f + 0.65f * ((t - 0.88f) / 0.02f)
        else -> 1f
    }
}

// ── Mascot Palette ────────────────────────────────────────────────────────
private val BodyLight = Color(0xFFFFF8EE)
private val BodyWarm = Color(0xFFF7E6CE)
private val BodyShade = Color(0xFFEAD2AE)
private val FaceInk = Color(0xFF2E2218)

/** Draws the ghost mascot inside [canvasSize]. */
private fun DrawScope.drawGhost(
    canvasSize: Size,
    centerYOffset: Float = 0f,
    happy: Boolean = true,
    blink: Float = 1f,
    gaze: Float = 0f,
    gazeY: Float = 0f,
    hemWave: Float = 0f,
    breathProgress: Float = 0f,
    blushBoost: Float = 0f,
    winkAmount: Float = 0f,
) {
    val centerX = canvasSize.width / 2f
    // Plush, friendly proportions
    val bodyWidth = canvasSize.width * 0.74f
    val halfWidth = bodyWidth / 2f
    val headTop = canvasSize.height * 0.10f + centerYOffset
    val headHeight = bodyWidth * 0.82f
    val bodyBottom = canvasSize.height * 0.90f + centerYOffset
    val hemHeight = bodyWidth * 0.18f

    val left = centerX - halfWidth
    val right = centerX + halfWidth
    val waistY = headTop + headHeight * 0.88f

    // ── Floating ghost arms (stubby nubs that bob gently out-of-phase) ────────
    val armLag = hemWave * 2f * PI.toFloat() - 0.45f
    val armBobLeft = sin(armLag) * (bodyWidth * 0.028f) - (winkAmount * bodyWidth * 0.04f)
    val armBobRight = sin(armLag + 0.3f) * (bodyWidth * 0.028f) + (winkAmount * bodyWidth * 0.04f)

    val leftArm = Path().apply {
        val armRootX = left + bodyWidth * 0.08f
        val armRootY = waistY + bodyWidth * 0.08f + armBobLeft
        val armTipX = left - bodyWidth * 0.15f
        val armTipY = waistY + bodyWidth * 0.22f + armBobLeft

        moveTo(armRootX, armRootY - bodyWidth * 0.10f)
        cubicTo(
            armTipX, armRootY - bodyWidth * 0.08f,
            armTipX - bodyWidth * 0.04f, armTipY + bodyWidth * 0.02f,
            armTipX + bodyWidth * 0.06f, armTipY + bodyWidth * 0.06f
        )
        cubicTo(
            armRootX - bodyWidth * 0.02f, armTipY + bodyWidth * 0.08f,
            armRootX, armRootY + bodyWidth * 0.06f,
            armRootX, armRootY + bodyWidth * 0.06f
        )
        close()
    }

    val rightArm = Path().apply {
        val armRootX = right - bodyWidth * 0.08f
        val armRootY = waistY + bodyWidth * 0.08f + armBobRight
        val armTipX = right + bodyWidth * 0.15f
        val armTipY = waistY + bodyWidth * 0.22f + armBobRight

        moveTo(armRootX, armRootY - bodyWidth * 0.10f)
        cubicTo(
            armTipX, armRootY - bodyWidth * 0.08f,
            armTipX + bodyWidth * 0.04f, armTipY + bodyWidth * 0.02f,
            armTipX - bodyWidth * 0.06f, armTipY + bodyWidth * 0.06f
        )
        cubicTo(
            armRootX + bodyWidth * 0.02f, armTipY + bodyWidth * 0.08f,
            armRootX, armRootY + bodyWidth * 0.06f,
            armRootX, armRootY + bodyWidth * 0.06f
        )
        close()
    }

    // Draw arms with gradient
    val armBrush = Brush.verticalGradient(
        colors = listOf(BodyLight, BodyWarm),
        startY = waistY,
        endY = waistY + bodyWidth * 0.30f
    )
    drawPath(path = leftArm, brush = armBrush)
    drawPath(path = rightArm, brush = armBrush)

    // Arm subtle hairline
    val armStrokeBrush = Brush.verticalGradient(
        colors = listOf(
            MorroColors.Honey.copy(alpha = 0.40f),
            MorroColors.Honey.copy(alpha = 0.20f)
        ),
        startY = waistY,
        endY = waistY + bodyWidth * 0.30f
    )
    drawPath(path = leftArm, brush = armStrokeBrush, style = Stroke(width = 1.3.dp.toPx()))
    drawPath(path = rightArm, brush = armStrokeBrush, style = Stroke(width = 1.3.dp.toPx()))

    // ── Main Ghost Body ──────────────────────────────────────────────────────
    val body = Path().apply {
        moveTo(left, waistY)

        // Rounded friendly dome
        cubicTo(
            left - bodyWidth * 0.02f, headTop + headHeight * 0.25f,
            left + bodyWidth * 0.12f, headTop,
            centerX, headTop
        )
        cubicTo(
            right - bodyWidth * 0.12f, headTop,
            right + bodyWidth * 0.02f, headTop + headHeight * 0.25f,
            right, waistY
        )

        // Right flank: gentle outward bell curve into the skirt
        cubicTo(
            right + bodyWidth * 0.03f, waistY + (bodyBottom - waistY) * 0.35f,
            right + bodyWidth * 0.08f, bodyBottom - hemHeight * 0.6f,
            right + bodyWidth * 0.04f, bodyBottom
        )

        // 4 flowing, rounded hem scallops
        val scallopCount = 4
        val scallopWidth = (bodyWidth * 1.08f) / scallopCount
        var currX = right + bodyWidth * 0.04f

        for (i in 0 until scallopCount) {
            val nextX = currX - scallopWidth
            val midX = (currX + nextX) / 2f
            val ripple = (
                sin(hemWave * 2f * PI.toFloat() + i * 1.5f) * 0.70f +
                sin(hemWave * 4f * PI.toFloat() + i * 2.2f) * 0.30f
            ) * hemHeight * 0.18f

            cubicTo(
                currX - scallopWidth * 0.15f, bodyBottom + hemHeight * 0.75f + ripple,
                midX + scallopWidth * 0.20f, bodyBottom + hemHeight * 0.78f + ripple,
                midX, bodyBottom + hemHeight * 0.55f + ripple
            )
            cubicTo(
                midX - scallopWidth * 0.20f, bodyBottom + hemHeight * 0.78f + ripple,
                nextX + scallopWidth * 0.15f, bodyBottom + hemHeight * 0.75f + ripple,
                nextX, bodyBottom
            )
            currX = nextX
        }

        // Left flank back up to waist
        cubicTo(
            left - bodyWidth * 0.08f, bodyBottom - hemHeight * 0.6f,
            left - bodyWidth * 0.03f, waistY + (bodyBottom - waistY) * 0.35f,
            left, waistY
        )
        close()
    }

    // Ghost body fill
    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            colors = listOf(BodyLight, BodyWarm, BodyShade),
            startY = headTop,
            endY = bodyBottom + hemHeight
        )
    )

    // Soft subtle rim outline
    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            colors = listOf(
                MorroColors.Honey.copy(alpha = 0.55f),
                MorroColors.Honey.copy(alpha = 0.40f),
                MorroColors.Honey.copy(alpha = 0.20f)
            ),
            startY = headTop,
            endY = bodyBottom + hemHeight
        ),
        style = Stroke(width = 1.4.dp.toPx())
    )

    // ── Expressive Face ──────────────────────────────────────────────────────
    val eyeY = headTop + headHeight * 0.46f + gazeY * (bodyWidth * 0.04f)
    val eyeSpacing = bodyWidth * 0.185f
    val eyeWidth = bodyWidth * 0.088f
    val eyeHeight = bodyWidth * 0.125f * blink.coerceIn(0.08f, 1f)
    val eyeShift = bodyWidth * 0.026f * gaze

    listOf(-1f, 1f).forEach { side ->
        val cx = centerX + side * eyeSpacing + eyeShift
        val isWinkingEye = side > 0 && winkAmount > 0.4f

        if (isWinkingEye || (happy && winkAmount > 0.7f)) {
            // Playful winking closed crescent
            val arc = Path().apply {
                moveTo(cx - eyeWidth * 1.1f, eyeY + eyeHeight * 0.15f)
                cubicTo(
                    cx - eyeWidth * 0.6f, eyeY - eyeHeight * 0.75f,
                    cx + eyeWidth * 0.6f, eyeY - eyeHeight * 0.75f,
                    cx + eyeWidth * 1.1f, eyeY + eyeHeight * 0.15f
                )
            }
            drawPath(
                path = arc,
                color = FaceInk,
                style = Stroke(width = eyeWidth * 0.55f, cap = StrokeCap.Round)
            )
        } else {
            // Adorable rounded glossy eye
            drawOval(
                color = FaceInk,
                topLeft = Offset(cx - eyeWidth, eyeY - eyeHeight / 2f),
                size = Size(eyeWidth * 2f, eyeHeight)
            )

            // Specular highlights: primary glossy glint
            if (blink > 0.35f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.95f),
                    radius = eyeWidth * 0.38f,
                    center = Offset(cx + eyeWidth * 0.32f, eyeY - eyeHeight * 0.22f)
                )
                // Secondary cute micro-glint
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = eyeWidth * 0.18f,
                    center = Offset(cx - eyeWidth * 0.30f, eyeY + eyeHeight * 0.20f)
                )
            }
        }
    }

    // ── Rosy Cheeks (MorroColors.EyeRose) ────────────────────────────────────
    val blushAlpha = (0.42f + 0.10f * breathProgress + 0.35f * blushBoost).coerceIn(0f, 0.85f)
    val blushRadius = bodyWidth * (0.085f + 0.008f * breathProgress + 0.015f * blushBoost)
    listOf(-1f, 1f).forEach { side ->
        drawCircle(
            color = MorroColors.EyeRose.copy(alpha = blushAlpha),
            radius = blushRadius,
            center = Offset(
                centerX + side * eyeSpacing * 1.35f + eyeShift,
                eyeY + bodyWidth * 0.105f
            )
        )
    }

    // ── Sweet Smile ──────────────────────────────────────────────────────────
    val smileWidth = bodyWidth * (0.19f + 0.015f * breathProgress + 0.025f * blushBoost)
    val smileY = eyeY + bodyWidth * (0.138f - 0.005f * breathProgress)
    val smileLift = bodyWidth * (0.065f + 0.010f * breathProgress + 0.015f * blushBoost)
    val smile = Path().apply {
        moveTo(centerX - smileWidth / 2f, smileY)
        cubicTo(
            centerX - smileWidth * 0.26f, smileY + smileLift,
            centerX + smileWidth * 0.26f, smileY + smileLift,
            centerX + smileWidth / 2f, smileY
        )
    }
    drawPath(
        path = smile,
        color = FaceInk.copy(alpha = 0.90f),
        style = Stroke(width = bodyWidth * 0.024f, cap = StrokeCap.Round)
    )
}
