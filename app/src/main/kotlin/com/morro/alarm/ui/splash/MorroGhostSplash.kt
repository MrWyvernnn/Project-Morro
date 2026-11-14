package com.morro.alarm.ui.splash

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

// Splash palette. Kept local so the splash never depends on theme internals.
private val SplashCanvas = Color(0xFF000000)
private val SplashBody = Color(0xFFFFF8F0)
private val SplashEye = Color(0xFF17171C)
private val SplashBlush = Color(0xFFFF6B9D)
private val SplashHalo = Color(0xFFFFC94A)
private val SplashTextPrimary = Color(0xFFFFFFFF)
private val SplashTextSecondary = Color(0xFFB3FFFFFF)
private val SplashGlowPink = Color(0xFFFF6B9D)
private val SplashGlowOrange = Color(0xFFFF4500)

private const val EntranceMillis = 600f
private const val TextRevealMillis = 500f
private const val FrameMillis = 16L
private const val SplashHoldMillis = 2300L
private const val ReducedMotionHoldMillis = 150L

/**
 * First-launch splash: an AMOLED-black stage with the Morro ghost mascot, the wordmark,
 * and the tagline. It dismisses itself after roughly 2.3s, or immediately on tap.
 *
 * Everything is drawn with Compose primitives - there are no image assets and no engine code
 * is touched. Back navigation and the alarm flow are unaffected: this composable only reports
 * that it is finished through [onDismiss].
 */
@Composable
fun MorroGhostSplash(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()

    var entrance by remember { mutableFloatStateOf(if (reducedMotion) 1f else 0f) }
    var textReveal by remember { mutableFloatStateOf(if (reducedMotion) 1f else 0f) }
    var floatOffset by remember { mutableFloatStateOf(0f) }
    var dismissed by remember { mutableStateOf(false) }

    fun finish() {
        if (!dismissed) {
            dismissed = true
            onDismiss()
        }
    }

    // Entrance, then the text reveal. Both are eased with the same smooth curve.
    LaunchedEffect(Unit) {
        if (reducedMotion) return@LaunchedEffect

        runTimeline(EntranceMillis) { progress -> entrance = easeInOutCubic(progress) }
        delay(320L)
        runTimeline(TextRevealMillis) { progress -> textReveal = easeInOutCubic(progress) }
    }

    // Gentle idle float, skipped entirely when animations are turned off.
    LaunchedEffect(Unit) {
        if (reducedMotion) return@LaunchedEffect
        var phase = 0f
        while (true) {
            floatOffset = sin(phase) * 4f
            phase += 0.12f
            delay(24L)
        }
    }

    // Auto-dismiss. Reduced motion collapses the hold to a short beat.
    LaunchedEffect(Unit) {
        delay(if (reducedMotion) ReducedMotionHoldMillis else SplashHoldMillis)
        finish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SplashCanvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Ambient warmth behind the mascot, plus the glossy halo behind the wordmark.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SplashGlowPink.copy(alpha = 0.10f * entrance),
                                SplashGlowOrange.copy(alpha = 0.05f * entrance),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height * 0.42f),
                            radius = size.minDimension * 0.75f,
                        ),
                        size = size,
                    )
                },
        )

        // Tap anywhere to skip.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = { finish() }),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.height(96.dp))

            Canvas(
                modifier = Modifier
                    .size(196.dp, 216.dp)
                    .offset(y = floatOffset.dp)
                    .alpha(entrance),
            ) {
                drawGhost(
                    canvasSize = size,
                    scale = 0.90f + 0.10f * entrance,
                    blinkProgress = 0f,
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            Box(
                modifier = Modifier
                    .alpha(textReveal)
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    SplashGlowPink.copy(alpha = 0.28f),
                                    SplashGlowOrange.copy(alpha = 0.14f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.minDimension * 0.95f,
                            ),
                            size = size,
                        )
                    },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Morro",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        color = SplashTextPrimary,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "The Commitment Alarm",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        color = SplashTextSecondary,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.06f.sp,
                    )
                }
            }
        }
    }
}

/**
 * Runs a tween from 0f to 1f in [FrameMillis] steps over [durationMillis], reporting the raw
 * progress. Deliberately simple: one coroutine per animation keeps the state updates readable
 * and avoids depending on animation APIs that vary between Compose versions.
 */
private suspend fun runTimeline(
    durationMillis: Float,
    onFrame: (Float) -> Unit,
) {
    val step = FrameMillis.toFloat() / durationMillis
    var progress = 0f
    while (progress < 1f) {
        progress = (progress + step).coerceIn(0f, 1f)
        onFrame(progress)
        delay(FrameMillis)
    }
}

private fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - ((-2f * t + 2f) * (-2f * t + 2f) * (-2f * t + 2f)) / 2f

/**
 * Draws the mascot: a soft dome head, a wavy scalloped hem, two dark oval eyes and pink blush,
 * all inside the supplied canvas size. [scale] drives the entrance growth.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGhost(
    canvasSize: Size,
    scale: Float,
    blinkProgress: Float,
) {
    val bodyWidth = canvasSize.width * 0.60f * scale
    val headTop = canvasSize.height * 0.14f
    val bodyBottom = canvasSize.height * 0.78f
    val centerX = canvasSize.width / 2f
    val halfWidth = bodyWidth / 2f
    val capHeight = bodyWidth * 0.50f
    val hemHeight = bodyWidth * 0.10f
    val left = centerX - halfWidth
    val right = centerX + halfWidth

    // Warm-yellow halo behind the head.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                SplashHalo.copy(alpha = 0.16f),
                Color.Transparent,
            ),
            center = Offset(centerX, headTop + capHeight * 0.55f),
            radius = bodyWidth * 0.72f,
        ),
        radius = bodyWidth * 0.72f,
        center = Offset(centerX, headTop + capHeight * 0.55f),
    )

    val body = Path().apply {
        // Dome head.
        moveTo(left, headTop + capHeight)
        cubicTo(
            left, headTop,
            left + bodyWidth * 0.14f, headTop,
            centerX, headTop,
        )
        cubicTo(
            right - bodyWidth * 0.14f, headTop,
            right, headTop,
            right, headTop + capHeight,
        )

        // Straight sides into the hem.
        lineTo(right, bodyBottom - hemHeight)

        // Three soft scallops sweeping right to left.
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

    drawPath(path = body, color = SplashBody)

    // Face.
    val eyeCenterY = headTop + capHeight * 0.78f
    val eyeSpacing = bodyWidth * 0.19f
    val eyeWidth = bodyWidth * 0.105f
    val eyeHeight = bodyWidth * 0.17f * (1f - blinkProgress.coerceIn(0f, 0.85f))

    listOf(-1, 1).forEach { side ->
        drawOval(
            color = SplashEye,
            topLeft = Offset(
                centerX + side * eyeSpacing - eyeWidth / 2f,
                eyeCenterY - eyeHeight / 2f,
            ),
            size = Size(eyeWidth, eyeHeight),
        )
    }

    // Blush sits just below the eyes at ~40% alpha so it glows without reading as body text.
    val blushRadius = bodyWidth * 0.075f
    listOf(-1, 1).forEach { side ->
        drawCircle(
            color = SplashBlush.copy(alpha = 0.40f),
            radius = blushRadius,
            center = Offset(
                centerX + side * bodyWidth * 0.29f,
                eyeCenterY + bodyWidth * 0.14f,
            ),
        )
    }
}


/**
 * Reads the system animation scale. When animations are disabled (or scaled to zero) the splash
 * skips the entrance, the idle float and the long hold.
 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        scale == 0f
    }
}
