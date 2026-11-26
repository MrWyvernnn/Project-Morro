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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.lerpColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Pre-allocated reusable Path buffers so frame draws allocate zero objects. */
private class GhostPaths {
    val body = Path()
    val shadowLeftArm = Path()
    val shadowRightArm = Path()
    val shadowLeftFold = Path()
    val shadowRightFold = Path()
    val eyeClip = Path()
    val winkArc = Path()
}

/**
 * MorroGhostMascot — the expressive little ghost with raised waving arms, 3-lobe hem,
 * two-tone cel shading, wide glossy eyes, surprised "o" mouth, and hovering ground shadow.
 *
 * Motion:
 *  - Smooth hovering float above a stationary elliptical ground shadow that subtly scales with height.
 *  - Gentle raised-arm wave and 3-lobe cloth ripple driven by harmonic waves.
 *  - Natural blink with micro-saccades and subtle breathing on the "o" mouth.
 *  - Tactile tap interaction: playful spring squish, arm lift, wink, and surprised "ooo!" mouth pop.
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
    val paths = remember { GhostPaths() }

    val tapSquish = remember { Animatable(1f) }
    val tapReact = remember { Animatable(0f) }
    val tapWink = remember { Animatable(0f) }

    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (happy) 3_200 else 4_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ghostFloat"
    )

    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghostBreath"
    )

    val phase = if (reduced) 0f else drift
    val floatFraction = if (reduced) 0f else floatOffset(phase)
    val angle = phase * 2f * PI.toFloat()

    val sway = if (reduced) 0f else sin(angle) * 3.2f
    val floatVelocity = -cos(angle) * 0.042f - 2f * cos(angle * 2f) * 0.010f
    val tilt = if (reduced) 0f else sin(angle) * 2.2f + floatVelocity * 11f

    val baseStretch = if (reduced) 1f else 0.985f + 0.030f * breath
    val currentStretch = (baseStretch * tapSquish.value).coerceIn(0.84f, 1.22f)
    val blink = if (reduced) 1f else blinkAmount(phase, tapWink.value)
    val gaze = if (reduced) 0f else sin(angle) * 0.85f + sin(angle * 3f) * 0.15f

    val tapModifier = if (interactive && !reduced) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                tapSquish.snapTo(0.86f)
                tapSquish.animateTo(1f, MorroSpring.FloatSpringPlayful)
            }
            scope.launch {
                tapReact.snapTo(1f)
                tapReact.animateTo(0f, tween(520, easing = MorroMotion.EnterEasing))
            }
            scope.launch {
                tapWink.animateTo(1f, tween(110))
                tapWink.animateTo(0f, tween(170))
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
                pivot = Offset(centerX, size.height * 0.88f)
            ) {
                drawMascot(
                    paths = paths,
                    canvasSize = size,
                    hoverOffset = size.height * floatFraction,
                    armPhase = angle,
                    hemWave = phase,
                    blink = blink,
                    gaze = gaze,
                    gazeY = gazeBiasY,
                    breathProgress = if (reduced) 0.5f else breath,
                    tapReaction = tapReact.value,
                    winkAmount = tapWink.value,
                )
            }
        }
    }
}

/** Smooth vertical hover oscillation. */
private fun floatOffset(t: Float): Float =
    -sin(t * 2f * PI.toFloat()) * 0.040f - sin(t * 4f * PI.toFloat()) * 0.010f

/** Natural blink curve plus playful tap wink. */
private fun blinkAmount(t: Float, extraWink: Float = 0f): Float {
    if (extraWink > 0f) {
        return (1f - extraWink * 0.90f).coerceIn(0.10f, 1f)
    }
    return when {
        t in 0.72f..0.75f -> 0.12f
        t in 0.75f..0.79f -> 0.12f + 0.88f * ((t - 0.75f) / 0.04f)
        t in 0.87f..0.89f -> 0.38f
        t in 0.89f..0.91f -> 0.38f + 0.62f * ((t - 0.89f) / 0.02f)
        else -> 1f
    }
}

// ── Mascot Colours (Warm paper & ink aligned with Morro tokens) ───────────────
private val BodyBright = Color(0xFFFAF4EA)
private val BodyBase = Color(0xFFF3E8D7)
private val BodyCelShadow = Color(0xFFDAD0C1)
private val OutlineInk = Color(0xFF2B2836)
private val FaceInk = Color(0xFF252332)
private val EyeLowerTint = lerpColor(Color(0xFFDCD2C5), MorroColors.EyeRose, 0.45f)

private fun DrawScope.drawMascot(
    paths: GhostPaths,
    canvasSize: Size,
    hoverOffset: Float,
    armPhase: Float,
    hemWave: Float,
    blink: Float,
    gaze: Float,
    gazeY: Float,
    breathProgress: Float,
    tapReaction: Float,
    winkAmount: Float,
) {
    // Keep the vector character well-proportioned across splash (126x176) and smaller views (76x88).
    val s = minOf(canvasSize.width * 0.92f, canvasSize.height * 0.76f)
    val cx = canvasSize.width / 2f
    val baseTop = (canvasSize.height - s * 1.12f) * 0.5f
    val headTop = baseTop + hoverOffset

    // Helper converting normalized [-0.5..0.5, 0..1] ghost coordinates to canvas pixels
    fun gx(nx: Float): Float = cx + nx * s
    fun gy(ny: Float): Float = headTop + ny * s

    // ── 1. Stationary floating ground shadow (scales slightly as ghost rises/falls) ──
    val hoverNormalized = (hoverOffset / (canvasSize.height * 0.05f)).coerceIn(-1f, 1f)
    val shadowWidth = s * (0.38f + 0.04f * hoverNormalized)
    val shadowHeight = s * 0.052f
    val shadowY = baseTop + s * 1.05f
    drawOval(
        color = MorroColors.TextPrimary.copy(alpha = 0.14f + 0.03f * hoverNormalized),
        topLeft = Offset(cx - shadowWidth / 2f, shadowY - shadowHeight / 2f),
        size = Size(shadowWidth, shadowHeight)
    )

    // ── 2. Arm wave & hem cloth ripple ──
    val armLiftL = -sin(armPhase) * 0.020f - tapReaction * 0.055f
    val armLiftR = -sin(armPhase + 0.55f) * 0.020f - tapReaction * 0.055f
    val hemRippleL = sin(hemWave * 2f * PI.toFloat()) * 0.016f
    val hemRippleC = sin(hemWave * 2f * PI.toFloat() + 1.3f) * 0.018f
    val hemRippleR = sin(hemWave * 2f * PI.toFloat() + 2.6f) * 0.016f

    // ── 3. Main Ghost Contour (dome -> raised wavy arms -> 3-lobe wavy hem) ──
    val body = paths.body.apply {
        rewind()
        // Start at top crown
        moveTo(gx(0.00f), gy(0.00f))

        // Right side of dome down to shoulder dip
        cubicTo(
            gx(0.21f), gy(0.00f),
            gx(0.31f), gy(0.14f),
            gx(0.32f), gy(0.34f)
        )

        // Right raised arm sweeping outward & up in cheerful "boo!" wave
        cubicTo(
            gx(0.38f), gy(0.28f + armLiftR * 0.5f),
            gx(0.45f), gy(0.23f + armLiftR),
            gx(0.49f), gy(0.28f + armLiftR)
        )
        cubicTo(
            gx(0.52f), gy(0.34f + armLiftR),
            gx(0.46f), gy(0.45f + armLiftR * 0.4f),
            gx(0.34f), gy(0.51f)
        )

        // Right skirt flank descending gently outward to bottom corner
        cubicTo(
            gx(0.36f), gy(0.62f),
            gx(0.39f), gy(0.73f),
            gx(0.36f), gy(0.80f + hemRippleR * 0.4f)
        )

        // Right hem lobe -> inner right valley -> deep center tail
        cubicTo(
            gx(0.33f), gy(0.86f + hemRippleR),
            gx(0.23f), gy(0.85f + hemRippleR),
            gx(0.16f), gy(0.80f)
        )
        cubicTo(
            gx(0.10f), gy(0.86f + hemRippleC * 0.5f),
            gx(0.06f), gy(0.94f + hemRippleC),
            gx(0.00f), gy(0.94f + hemRippleC)
        )

        // Center tail -> inner left valley -> left hem lobe
        cubicTo(
            gx(-0.06f), gy(0.94f + hemRippleC),
            gx(-0.10f), gy(0.86f + hemRippleC * 0.5f),
            gx(-0.16f), gy(0.80f)
        )
        cubicTo(
            gx(-0.23f), gy(0.87f + hemRippleL),
            gx(-0.33f), gy(0.88f + hemRippleL),
            gx(-0.36f), gy(0.81f + hemRippleL * 0.4f)
        )

        // Left skirt flank ascending to underarm
        cubicTo(
            gx(-0.39f), gy(0.73f),
            gx(-0.36f), gy(0.62f),
            gx(-0.34f), gy(0.51f)
        )

        // Left raised arm sweeping outward & up
        cubicTo(
            gx(-0.46f), gy(0.45f + armLiftL * 0.4f),
            gx(-0.52f), gy(0.34f + armLiftL),
            gx(-0.49f), gy(0.28f + armLiftL)
        )
        cubicTo(
            gx(-0.45f), gy(0.23f + armLiftL),
            gx(-0.38f), gy(0.28f + armLiftL * 0.5f),
            gx(-0.32f), gy(0.34f)
        )

        // Left side of dome closing at top crown
        cubicTo(
            gx(-0.31f), gy(0.14f),
            gx(-0.21f), gy(0.00f),
            gx(0.00f), gy(0.00f)
        )
        close()
    }

    // Main warm-paper fill
    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            colors = listOf(BodyBright, BodyBase),
            startY = gy(0.0f),
            endY = gy(0.96f)
        )
    )

    // ── 4. Two-Tone Cel Shading (underarms + vertical skirt fold shadows, clipped cleanly) ──
    clipPath(body) {
        val leftArmShade = paths.shadowLeftArm.apply {
            rewind()
            moveTo(gx(-0.51f), gy(0.31f + armLiftL))
            cubicTo(
                gx(-0.45f), gy(0.38f + armLiftL * 0.5f),
                gx(-0.38f), gy(0.43f),
                gx(-0.31f), gy(0.46f)
            )
            lineTo(gx(-0.35f), gy(0.54f))
            lineTo(gx(-0.54f), gy(0.48f))
            close()
        }
        drawPath(path = leftArmShade, color = BodyCelShadow)

        val rightArmShade = paths.shadowRightArm.apply {
            rewind()
            moveTo(gx(0.51f), gy(0.31f + armLiftR))
            cubicTo(
                gx(0.45f), gy(0.38f + armLiftR * 0.5f),
                gx(0.38f), gy(0.43f),
                gx(0.31f), gy(0.46f)
            )
            lineTo(gx(0.35f), gy(0.54f))
            lineTo(gx(0.54f), gy(0.48f))
            close()
        }
        drawPath(path = rightArmShade, color = BodyCelShadow)

        // Left vertical skirt fold shadow
        val leftFold = paths.shadowLeftFold.apply {
            rewind()
            moveTo(gx(-0.26f), gy(0.92f))
            cubicTo(
                gx(-0.26f), gy(0.74f),
                gx(-0.23f), gy(0.64f),
                gx(-0.18f), gy(0.64f)
            )
            cubicTo(
                gx(-0.13f), gy(0.64f),
                gx(-0.12f), gy(0.75f),
                gx(-0.13f), gy(0.92f)
            )
            close()
        }
        drawPath(path = leftFold, color = BodyCelShadow)

        // Right vertical skirt fold shadow
        val rightFold = paths.shadowRightFold.apply {
            rewind()
            moveTo(gx(0.13f), gy(0.92f))
            cubicTo(
                gx(0.12f), gy(0.75f),
                gx(0.13f), gy(0.64f),
                gx(0.18f), gy(0.64f)
            )
            cubicTo(
                gx(0.23f), gy(0.64f),
                gx(0.26f), gy(0.74f),
                gx(0.26f), gy(0.92f)
            )
            close()
        }
        drawPath(path = rightFold, color = BodyCelShadow)
    }

    // ── 5. Crisp Cartoon Contour Outline ──
    val strokeW = (s * 0.044f).coerceAtLeast(2.2f)
    // Hairline sticker contour outer edge so dark outline remains discernible against pure #000000
    drawPath(
        path = body,
        color = MorroColors.TextPrimary.copy(alpha = 0.22f),
        style = Stroke(width = strokeW + 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = body,
        color = OutlineInk,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // ── 6. Wide Expressive Eyes (dark oval + overlapping lower soft circular sheen) ──
    val eyeY = gy(0.32f) + gazeY * (s * 0.025f)
    val eyeSpacing = s * 0.105f
    val eyeRx = s * 0.048f
    val eyeRy = s * 0.064f * blink.coerceIn(0.08f, 1f)
    val eyeShift = s * 0.018f * gaze

    listOf(-1f, 1f).forEach { side ->
        val ecx = cx + side * eyeSpacing + eyeShift
        val isWinkingEye = side > 0 && winkAmount > 0.4f

        if (isWinkingEye) {
            val arc = paths.winkArc.apply {
                rewind()
                moveTo(ecx - eyeRx * 1.05f, eyeY + s * 0.010f)
                cubicTo(
                    ecx - eyeRx * 0.55f, eyeY - s * 0.048f,
                    ecx + eyeRx * 0.55f, eyeY - s * 0.048f,
                    ecx + eyeRx * 1.05f, eyeY + s * 0.010f
                )
            }
            drawPath(
                path = arc,
                color = FaceInk,
                style = Stroke(width = s * 0.028f, cap = StrokeCap.Round)
            )
        } else {
            val eyeLeft = ecx - eyeRx
            val eyeTop = eyeY - eyeRy
            val eyeW = eyeRx * 2f
            val eyeH = eyeRy * 2f

            // Dark ink oval
            drawOval(
                color = FaceInk,
                topLeft = Offset(eyeLeft, eyeTop),
                size = Size(eyeW, eyeH)
            )

            // Signature lower-half circular sheen from reference illustration, clipped to eye oval
            if (blink > 0.32f) {
                val clip = paths.eyeClip.apply {
                    rewind()
                    addOval(androidx.compose.ui.geometry.Rect(eyeLeft, eyeTop, eyeLeft + eyeW, eyeTop + eyeH))
                }
                clipPath(clip) {
                    drawCircle(
                        color = EyeLowerTint,
                        radius = s * 0.045f,
                        center = Offset(
                            ecx + s * 0.004f,
                            eyeY + s * 0.036f * blink
                        )
                    )
                }
            }
        }
    }

    // ── 7. Surprised "o" Mouth ──
    val mouthRx = s * 0.036f * (1f + 0.04f * breathProgress + 0.14f * tapReaction)
    val mouthRy = s * 0.056f * (1f + 0.06f * breathProgress + 0.20f * tapReaction)
    val mouthY = gy(0.475f) + gazeY * (s * 0.015f)
    val mouthX = cx + eyeShift * 0.6f
    drawOval(
        color = FaceInk,
        topLeft = Offset(mouthX - mouthRx, mouthY - mouthRy),
        size = Size(mouthRx * 2f, mouthRy * 2f)
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
