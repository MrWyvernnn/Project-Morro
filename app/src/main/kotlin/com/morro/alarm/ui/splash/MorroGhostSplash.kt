package com.morro.alarm.ui.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.components.bezierPoint
import com.morro.alarm.ui.components.drawGhost
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroSpring
import kotlinx.coroutines.delay

private const val SplashHoldMillis = 2_400L
private const val ReducedHoldMillis = 200L

/**
 * The hero splash: a pink sun, an orange ribbon rising out of the bottom-left, a few grass blades
 * on its edge, and the Morro ghost above them.
 *
 * Everything is one Canvas scene drawn back to front, so the layers share a single coordinate
 * space and the ribbon's grass can sit exactly on its edge. Motion is entrance-only (ghost rises
 * 36dp on a momentum spring, text lands after it) plus two slow ambient loops — the ribbon glow
 * breathing and the ghost's bob. Reduced motion collapses all of it to a static frame.
 */
@Composable
fun MorroGhostSplash(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    var dismissed by remember { mutableStateOf(false) }

    val entrance = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val textReveal = remember { Animatable(if (reducedMotion) 1f else 0f) }

    val ambient = rememberInfiniteTransition(label = "splashAmbient")
    val breathe by ambient.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ribbonBreathe"
    )
    val bob by ambient.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ghostBob"
    )

    fun finish() {
        if (!dismissed) {
            dismissed = true
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            entrance.animateTo(1f, MorroSpring.FloatSpringMomentum)
            delay(120L)
            textReveal.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(Unit) {
        delay(if (reducedMotion) ReducedHoldMillis else SplashHoldMillis)
        finish()
    }

    val glowAlpha = if (reducedMotion) 0.16f else 0.10f + 0.08f * breathe
    val ghostLift = if (reducedMotion) 0f else (1f - entrance.value) * 36f + bob * 4f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MorroColors.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawStage(glowAlpha = glowAlpha)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.14f))

            Canvas(
                modifier = Modifier
                    .size(220.dp, 244.dp)
                    .offset(y = ghostLift.dp)
                    .alpha(entrance.value.coerceIn(0f, 1f)),
            ) {
                drawGhost(
                    canvasSize = size,
                    centerYOffset = 0f,
                    bodyColor = Color.White,
                    eyeColor = MorroColors.AccentBubbly,
                    happy = true
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textReveal.value.coerceIn(0f, 1f)),
            ) {
                Text(
                    text = "Morro",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    letterSpacing = (-1).sp,
                    color = MorroColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "The Commitment Alarm",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    letterSpacing = 0.4.sp,
                    color = MorroColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Short pink underline: the only accent the wordmark needs.
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(Brush.horizontalGradient(MorroGradientColors))
                )
            }

            Spacer(modifier = Modifier.weight(0.30f))
        }

        // Tap anywhere to skip the hold.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = { finish() })
        )
    }
}

private val MorroGradientColors = listOf(MorroColors.AccentTactical, MorroColors.AccentBubbly)

/**
 * The stage, painted back to front: sun, ribbon, grass.
 *
 * The ribbon is one cubic bezier; the grass blades sample that same curve so they always stand on
 * its upper edge, whatever the screen size.
 */
private fun DrawScope.drawStage(glowAlpha: Float) {
    val w = size.width
    val h = size.height

    // ── Layer 1: pink sun, upper right ───────────────────────────────────────
    val sunCenter = Offset(w * 0.74f, h * 0.20f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                MorroColors.AccentBubbly.copy(alpha = 0.20f),
                MorroColors.AccentTactical.copy(alpha = 0.09f),
                Color.Transparent
            ),
            center = sunCenter,
            radius = w * 0.52f
        ),
        radius = w * 0.52f,
        center = sunCenter
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                MorroColors.AccentBubblySoft.copy(alpha = 0.24f),
                Color.Transparent
            ),
            center = sunCenter,
            radius = w * 0.17f
        ),
        radius = w * 0.17f,
        center = sunCenter
    )

    // ── Layer 2: the ribbon, bottom-left to middle-right ─────────────────────
    val start = Offset(-w * 0.16f, h * 0.90f)
    val control1 = Offset(w * 0.24f, h * 0.99f)
    val control2 = Offset(w * 0.46f, h * 0.60f)
    val end = Offset(w * 0.98f, h * 0.45f)

    val ribbon = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
    }
    val ribbonBrush = Brush.linearGradient(
        colors = listOf(MorroColors.AccentTactical, MorroColors.AccentBubblySoft, MorroColors.AccentBubbly),
        start = start,
        end = end
    )
    val coreWidth = 7.dp.toPx()

    // Soft outer glow: two wide, low-alpha passes, then the crisp core.
    drawPath(ribbon, ribbonBrush, alpha = glowAlpha * 0.55f, style = Stroke(width = coreWidth * 5f, cap = StrokeCap.Round))
    drawPath(ribbon, ribbonBrush, alpha = glowAlpha, style = Stroke(width = coreWidth * 2.6f, cap = StrokeCap.Round))
    drawPath(ribbon, ribbonBrush, alpha = 0.92f, style = Stroke(width = coreWidth, cap = StrokeCap.Round))

    // ── Layer 3: grass on the ribbon's upper edge ────────────────────────────
    var t = 0.08f
    var index = 0
    while (t < 0.62f) {
        val point = bezierPoint(t, start, control1, control2, end)
        val baseY = point.y - coreWidth * 0.4f
        val bladeHeight = (10f + (index % 4) * 5f).dp.toPx()
        val lean = if (index % 2 == 0) 2.4.dp.toPx() else -2.4.dp.toPx()

        val blade = Path().apply {
            moveTo(point.x - 1.4.dp.toPx(), baseY + 2.dp.toPx())
            quadraticTo(point.x + lean * 0.4f, baseY - bladeHeight * 0.6f, point.x + lean, baseY - bladeHeight)
            quadraticTo(point.x + lean * 1.4f, baseY - bladeHeight * 0.55f, point.x + 1.6.dp.toPx(), baseY + 2.dp.toPx())
            close()
        }
        // Dark silhouette so the blades read against the lit ribbon.
        drawPath(blade, Color(0xFF05050A))

        // Faint rim light on the leading edge.
        val rim = Path().apply {
            moveTo(point.x + lean * 0.9f, baseY - bladeHeight * 0.92f)
            quadraticTo(point.x + lean * 0.4f, baseY - bladeHeight * 0.6f, point.x - 1.2.dp.toPx(), baseY)
        }
        drawPath(
            rim,
            brush = Brush.linearGradient(
                listOf(
                    MorroColors.AccentBubbly.copy(alpha = 0.45f),
                    MorroColors.AccentTactical.copy(alpha = 0.30f)
                )
            ),
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
        )

        t += 0.045f
        index++
    }

    // A soft floor fade so the ribbon dissolves into the black at the bottom.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            startY = h * 0.78f,
            endY = h
        ),
        topLeft = Offset(0f, h * 0.78f),
        size = Size(w, h * 0.22f)
    )
}

/** Reads the system animator scale; zero means the user asked for no animation. */
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
