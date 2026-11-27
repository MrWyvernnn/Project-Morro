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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.platform.LocalHapticFeedback
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.lerpColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pre-allocated reusable Path buffers so frame draws allocate zero objects. */
private class GhostPaths {
    val body = Path()
    val shadowLeftArm = Path()
    val shadowRightArm = Path()
    val shadowLeftFold = Path()
    val shadowRightFold = Path()
    val eyeClip = Path()
    val happyEyeArc = Path()
    val mouthPath = Path()
    val sparklePath = Path()
}

/**
 * MorroGhostMascot — cheerful, expressive floating ghost mascot with raised waving arms,
 * 3-lobe hem, two-tone cel shading, rosy cheeks, glossy eyes, happy open smile, and
 * multi-stage interactive Poke reactions.
 *
 * Motion:
 *  - Smooth figure-8 hover above a dynamic elliptical ground shadow.
 *  - Playful asymmetric arm wave, rhythmic hem cloth ripple, and ambient twinkle sparkles.
 *  - Natural blinking with expressive micro-gaze.
 *  - Tactile Poke interaction (from direct tap OR external [pokeSignal] button increment):
 *    cycles through Joy Hop + `^ ^` crescent eyes, Happy Shimmy wiggle, and Celebratory Twirl.
 */
@Composable
fun MorroGhostMascot(
    modifier: Modifier = Modifier,
    happy: Boolean = true,
    interactive: Boolean = true,
    gazeBiasY: Float = 0f,
    pokeSignal: Int = 0,
    onPoked: ((pokeCount: Int) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "ghostMascot")
    val paths = remember { GhostPaths() }
    val currentOnPoked by rememberUpdatedState(onPoked)

    var pokeCount by remember { mutableIntStateOf(0) }
    var lastHandledSignal by remember { mutableIntStateOf(pokeSignal) }

    val tapSquish = remember { Animatable(1f) }
    val tapReact = remember { Animatable(0f) }
    val tapHappyEyes = remember { Animatable(0f) }
    val tapHopY = remember { Animatable(0f) }
    val tapShimmy = remember { Animatable(0f) }
    val tapSpin = remember { Animatable(0f) }

    fun triggerPoke(notifyCallback: Boolean) {
        pokeCount++
        if (notifyCallback) {
            currentOnPoked?.invoke(pokeCount)
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (reduced) return

        val stage = (pokeCount - 1) % 3
        scope.launch {
            tapSquish.snapTo(0.82f)
            tapSquish.animateTo(1f, MorroSpring.FloatSpringPlayful)
        }
        scope.launch {
            tapReact.snapTo(1f)
            tapReact.animateTo(0f, tween(680, easing = MorroMotion.EnterEasing))
        }
        scope.launch {
            tapHappyEyes.snapTo(1f)
            delay(360)
            tapHappyEyes.animateTo(0f, tween(220, easing = MorroMotion.EnterEasing))
        }
        scope.launch {
            tapHopY.snapTo(0f)
            tapHopY.animateTo(-0.14f, tween(130, easing = MorroMotion.ExitEasing))
            tapHopY.animateTo(0f, MorroSpring.FloatSpringPlayful)
        }

        if (stage == 1) {
            scope.launch {
                tapShimmy.snapTo(0f)
                for (target in floatArrayOf(-12f, 11f, -8f, 6f, -3f, 0f)) {
                    tapShimmy.animateTo(target, tween(55))
                }
            }
        } else if (stage == 2) {
            scope.launch {
                tapSpin.snapTo(0f)
                tapSpin.animateTo(360f, tween(540, easing = MorroMotion.EnterEasing))
                tapSpin.snapTo(0f)
            }
        }
    }

    LaunchedEffect(pokeSignal) {
        if (pokeSignal > lastHandledSignal) {
            lastHandledSignal = pokeSignal
            triggerPoke(notifyCallback = false)
        } else {
            lastHandledSignal = pokeSignal
        }
    }

    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (happy) 2_600 else 3_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ghostFloat"
    )

    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghostBreath"
    )

    val phase = if (reduced) 0f else drift
    val floatFraction = if (reduced) 0f else floatOffset(phase) + tapHopY.value
    val angle = phase * 2f * PI.toFloat()

    // Lively figure-8 horizontal sway & buoyant lean
    val sway = if (reduced) 0f else sin(angle) * 5.2f + tapShimmy.value * 0.55f
    val floatVelocity = -cos(angle) * 0.048f - 2f * cos(angle * 2f) * 0.012f
    val tilt = if (reduced) 0f else sin(angle) * 3.8f + floatVelocity * 15f + tapShimmy.value

    val baseStretch = if (reduced) 1f else 0.975f + 0.045f * breath
    val currentStretch = (baseStretch * tapSquish.value).coerceIn(0.80f, 1.24f)
    val blink = if (reduced) 1f else blinkAmount(phase)
    val gaze = if (reduced) 0f else sin(angle) * 0.90f + sin(angle * 2f) * 0.20f

    val tapModifier = if (interactive) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            triggerPoke(notifyCallback = true)
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
                    rotationY = tapSpin.value
                    cameraDistance = 14f * density
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
                    happyEyesAmount = tapHappyEyes.value,
                    happy = happy,
                    reducedMotion = reduced,
                )
            }
        }
    }
}

/** Joyful double-harmonic hover curve. */
private fun floatOffset(t: Float): Float =
    -sin(t * 2f * PI.toFloat()) * 0.052f - sin(t * 4f * PI.toFloat()) * 0.014f

/** Natural blink timing. */
private fun blinkAmount(t: Float): Float {
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
private val CheekBlush = MorroColors.EyeRose
private val TongueRose = lerpColor(MorroColors.EyeRose, Color(0xFFE07A85), 0.55f)

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
    happyEyesAmount: Float,
    happy: Boolean,
    reducedMotion: Boolean,
) {
    // Keep the vector character well-proportioned across splash (126x176) and mini pills (28dp).
    val s = minOf(canvasSize.width * 0.90f, canvasSize.height * 0.75f)
    val cx = canvasSize.width / 2f
    val baseTop = (canvasSize.height - s * 1.12f) * 0.5f
    val headTop = baseTop + hoverOffset

    fun gx(nx: Float): Float = cx + nx * s
    fun gy(ny: Float): Float = headTop + ny * s

    // ── 1. Stationary floating ground shadow (scales slightly as ghost rises/falls) ──
    val hoverNormalized = (hoverOffset / (canvasSize.height * 0.06f)).coerceIn(-1.2f, 1.2f)
    val shadowWidth = s * (0.40f + 0.05f * hoverNormalized).coerceIn(0.24f, 0.48f)
    val shadowHeight = s * 0.052f
    val shadowY = baseTop + s * 1.05f
    drawOval(
        color = MorroColors.TextPrimary.copy(alpha = (0.14f + 0.03f * hoverNormalized).coerceIn(0.05f, 0.20f)),
        topLeft = Offset(cx - shadowWidth / 2f, shadowY - shadowHeight / 2f),
        size = Size(shadowWidth, shadowHeight)
    )

    // ── 2. Joyful arm wave & 3-lobe hem cloth ripple ──
    val waveBurst = 1f + tapReaction * 1.6f
    val armLiftL = -sin(armPhase * 1.5f) * 0.036f * waveBurst - tapReaction * 0.075f
    val armLiftR = -sin(armPhase * 1.5f + 1.15f) * 0.036f * waveBurst - tapReaction * 0.075f
    val hemRippleL = sin(hemWave * 2f * PI.toFloat()) * 0.022f
    val hemRippleC = sin(hemWave * 2f * PI.toFloat() + 1.3f) * 0.024f
    val hemRippleR = sin(hemWave * 2f * PI.toFloat() + 2.6f) * 0.022f

    // ── 3. Main Ghost Contour (dome -> raised wavy arms -> 3-lobe wavy hem) ──
    val body = paths.body.apply {
        rewind()
        moveTo(gx(0.00f), gy(0.00f))

        cubicTo(
            gx(0.21f), gy(0.00f),
            gx(0.31f), gy(0.14f),
            gx(0.32f), gy(0.34f)
        )

        // Right raised happy waving arm
        cubicTo(
            gx(0.38f), gy(0.27f + armLiftR * 0.5f),
            gx(0.46f), gy(0.21f + armLiftR),
            gx(0.50f), gy(0.26f + armLiftR)
        )
        cubicTo(
            gx(0.53f), gy(0.33f + armLiftR),
            gx(0.46f), gy(0.44f + armLiftR * 0.4f),
            gx(0.34f), gy(0.51f)
        )

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

        cubicTo(
            gx(-0.39f), gy(0.73f),
            gx(-0.36f), gy(0.62f),
            gx(-0.34f), gy(0.51f)
        )

        // Left raised happy waving arm
        cubicTo(
            gx(-0.46f), gy(0.44f + armLiftL * 0.4f),
            gx(-0.53f), gy(0.33f + armLiftL),
            gx(-0.50f), gy(0.26f + armLiftL)
        )
        cubicTo(
            gx(-0.46f), gy(0.21f + armLiftL),
            gx(-0.38f), gy(0.27f + armLiftL * 0.5f),
            gx(-0.32f), gy(0.34f)
        )

        cubicTo(
            gx(-0.31f), gy(0.14f),
            gx(-0.21f), gy(0.00f),
            gx(0.00f), gy(0.00f)
        )
        close()
    }

    // Subtle cheerful aura glow on poke
    if (tapReaction > 0.05f && s > 30f) {
        drawCircle(
            color = MorroColors.Honey.copy(alpha = 0.16f * tapReaction),
            radius = s * (0.52f + 0.12f * (1f - tapReaction)),
            center = Offset(cx, gy(0.46f))
        )
    }

    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            colors = listOf(BodyBright, BodyBase),
            startY = gy(0.0f),
            endY = gy(0.96f)
        )
    )

    // ── 4. Two-Tone Cel Shading ──
    clipPath(body) {
        val leftArmShade = paths.shadowLeftArm.apply {
            rewind()
            moveTo(gx(-0.52f), gy(0.29f + armLiftL))
            cubicTo(
                gx(-0.45f), gy(0.37f + armLiftL * 0.5f),
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
            moveTo(gx(0.52f), gy(0.29f + armLiftR))
            cubicTo(
                gx(0.45f), gy(0.37f + armLiftR * 0.5f),
                gx(0.38f), gy(0.43f),
                gx(0.31f), gy(0.46f)
            )
            lineTo(gx(0.35f), gy(0.54f))
            lineTo(gx(0.54f), gy(0.48f))
            close()
        }
        drawPath(path = rightArmShade, color = BodyCelShadow)

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
    val strokeW = (s * 0.044f).coerceAtLeast(1.8f)
    drawPath(
        path = body,
        color = MorroColors.TextPrimary.copy(alpha = 0.22f),
        style = Stroke(width = strokeW + 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = body,
        color = OutlineInk,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // ── 6. Warm Rosy Cheeks ──
    val eyeY = gy(0.32f) + gazeY * (s * 0.025f)
    val eyeSpacing = s * 0.108f
    val eyeRx = s * 0.046f
    val eyeRy = s * 0.060f * blink.coerceIn(0.08f, 1f)
    val eyeShift = s * 0.018f * gaze

    val blushAlpha = (0.48f + 0.34f * tapReaction).coerceIn(0f, 0.85f)
    listOf(-1f, 1f).forEach { side ->
        val cheekCx = cx + side * (s * 0.165f) + eyeShift * 0.8f
        val cheekCy = gy(0.415f) + gazeY * (s * 0.02f)
        drawOval(
            color = CheekBlush.copy(alpha = blushAlpha),
            topLeft = Offset(cheekCx - s * 0.048f, cheekCy - s * 0.026f),
            size = Size(s * 0.096f, s * 0.052f)
        )
    }

    // ── 7. Cheerful Glossy Eyes / Joyful `^ ^` Crescent Laughing Eyes on Poke ──
    val showHappyCrescents = happyEyesAmount > 0.35f
    listOf(-1f, 1f).forEach { side ->
        val ecx = cx + side * eyeSpacing + eyeShift
        if (showHappyCrescents) {
            val arc = paths.happyEyeArc.apply {
                rewind()
                moveTo(ecx - eyeRx * 1.15f, eyeY + s * 0.012f)
                cubicTo(
                    ecx - eyeRx * 0.55f, eyeY - s * 0.056f,
                    ecx + eyeRx * 0.55f, eyeY - s * 0.056f,
                    ecx + eyeRx * 1.15f, eyeY + s * 0.012f
                )
            }
            drawPath(
                path = arc,
                color = FaceInk,
                style = Stroke(width = (s * 0.030f).coerceAtLeast(1.8f), cap = StrokeCap.Round)
            )
        } else {
            val eyeLeft = ecx - eyeRx
            val eyeTop = eyeY - eyeRy
            val eyeW = eyeRx * 2f
            val eyeH = eyeRy * 2f

            drawOval(
                color = FaceInk,
                topLeft = Offset(eyeLeft, eyeTop),
                size = Size(eyeW, eyeH)
            )

            if (blink > 0.32f) {
                val clip = paths.eyeClip.apply {
                    rewind()
                    addOval(Rect(eyeLeft, eyeTop, eyeLeft + eyeW, eyeTop + eyeH))
                }
                clipPath(clip) {
                    drawCircle(
                        color = EyeLowerTint,
                        radius = s * 0.042f,
                        center = Offset(
                            ecx + s * 0.004f,
                            eyeY + s * 0.035f * blink
                        )
                    )
                }
                // Crisp bright catchlight sparkle in top-left of eye
                drawCircle(
                    color = BodyBright,
                    radius = (s * 0.015f).coerceAtLeast(1.1f),
                    center = Offset(ecx - eyeRx * 0.32f, eyeY - eyeRy * 0.34f)
                )
            }
        }
    }

    // ── 8. Happy Warm Open Smile ("D" laugh when happy/poked) ──
    val mouthX = cx + eyeShift * 0.6f
    val mouthY = gy(0.455f) + gazeY * (s * 0.015f)
    if (happy) {
        val mouthW = s * (0.145f + 0.045f * tapReaction + 0.012f * breathProgress)
        val mouthH = s * (0.095f + 0.048f * tapReaction + 0.015f * breathProgress)
        val mouthPath = paths.mouthPath.apply {
            rewind()
            moveTo(mouthX - mouthW / 2f, mouthY - mouthH * 0.16f)
            quadraticTo(mouthX, mouthY - mouthH * 0.06f, mouthX + mouthW / 2f, mouthY - mouthH * 0.16f)
            cubicTo(
                mouthX + mouthW * 0.46f, mouthY + mouthH * 0.92f,
                mouthX - mouthW * 0.46f, mouthY + mouthH * 0.92f,
                mouthX - mouthW / 2f, mouthY - mouthH * 0.16f
            )
            close()
        }
        drawPath(path = mouthPath, color = FaceInk)
        clipPath(mouthPath) {
            drawOval(
                color = TongueRose,
                topLeft = Offset(mouthX - mouthW * 0.34f, mouthY + mouthH * 0.28f),
                size = Size(mouthW * 0.68f, mouthH * 0.56f)
            )
        }
    } else {
        // Gentle calm smile when happy = false
        val mouthPath = paths.mouthPath.apply {
            rewind()
            moveTo(mouthX - s * 0.065f, mouthY)
            quadraticTo(mouthX, mouthY + s * 0.055f, mouthX + s * 0.065f, mouthY)
        }
        drawPath(
            path = mouthPath,
            color = FaceInk,
            style = Stroke(width = (s * 0.026f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
        )
    }

    // ── 9. Joyful Twinkle Sparkles (ambient + burst on Poke) ──
    if (!reducedMotion && s > 44f) {
        val burstSpread = 1f + tapReaction * 0.42f
        val sparkleAlphaBase = (0.36f + 0.55f * tapReaction).coerceIn(0f, 0.95f)
        val tw1 = 0.55f + 0.45f * abs(sin(armPhase))
        val tw2 = 0.55f + 0.45f * abs(cos(armPhase * 1.3f))

        drawFourPointSparkle(
            path = paths.sparklePath,
            center = Offset(gx(-0.44f * burstSpread), gy(0.11f - tapReaction * 0.05f)),
            radius = s * 0.044f * (0.8f + 0.3f * tw1 + 0.35f * tapReaction),
            color = MorroColors.Honey.copy(alpha = sparkleAlphaBase * tw1)
        )
        drawFourPointSparkle(
            path = paths.sparklePath,
            center = Offset(gx(0.45f * burstSpread), gy(0.13f - tapReaction * 0.04f)),
            radius = s * 0.038f * (0.8f + 0.3f * tw2 + 0.35f * tapReaction),
            color = BodyBright.copy(alpha = sparkleAlphaBase * tw2)
        )
        if (tapReaction > 0.08f) {
            drawFourPointSparkle(
                path = paths.sparklePath,
                center = Offset(gx(0.0f), gy(-0.08f - tapReaction * 0.06f)),
                radius = s * 0.048f * tapReaction,
                color = MorroColors.Honey.copy(alpha = 0.85f * tapReaction)
            )
        }
    }
}

private fun DrawScope.drawFourPointSparkle(
    path: Path,
    center: Offset,
    radius: Float,
    color: Color,
) {
    if (radius <= 0.5f || color.alpha <= 0.01f) return
    val inner = radius * 0.28f
    path.apply {
        rewind()
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + inner * 0.35f, center.y - inner * 0.35f, center.x + radius, center.y)
        quadraticTo(center.x + inner * 0.35f, center.y + inner * 0.35f, center.x, center.y + radius)
        quadraticTo(center.x - inner * 0.35f, center.y + inner * 0.35f, center.x - radius, center.y)
        quadraticTo(center.x - inner * 0.35f, center.y - inner * 0.35f, center.x, center.y - radius)
        close()
    }
    drawPath(path = path, color = color)
}
