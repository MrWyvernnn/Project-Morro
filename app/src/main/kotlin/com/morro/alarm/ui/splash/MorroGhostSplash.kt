package com.morro.alarm.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.components.MorroSky
import com.morro.alarm.ui.components.MorroGhostMascot
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroSpring
import kotlinx.coroutines.delay

// Long enough for the greeting to be read and the mascot to be seen hovering. Still short enough
// that it never feels like a loading screen, and a tap dismisses it immediately.
private const val SplashHoldMillis = 1_800L
private const val ReducedHoldMillis = 200L

/**
 * The opening screen: black, a sky of slow clouds, the mascot just above the wordmark.
 *
 * Three elements, one column, one idea. The mascot is the only bright thing; everything else is
 * type and air. The entrance is a single spring on the mascot and a short fade on the text, and the
 * whole thing is tappable so nobody waits for it.
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
    val signatureProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }

    fun finish() {
        if (!dismissed) {
            dismissed = true
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            entrance.animateTo(1f, MorroSpring.FloatSpringPlayful)
            delay(90L)
            textReveal.animateTo(1f, tween(durationMillis = 380, easing = MorroMotion.EnterEasing))
            signatureProgress.animateTo(1f, tween(durationMillis = 400, easing = MorroMotion.EnterEasing))
        }
    }

    LaunchedEffect(Unit) {
        delay(if (reducedMotion) ReducedHoldMillis else SplashHoldMillis)
        finish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MorroColors.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(onClick = { finish() }),
    ) {
        MorroSky(modifier = Modifier.fillMaxSize(), intensity = 1f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Taller than it is wide: the same body width over a longer canvas stretches the flanks,
            // which is what makes the waist and hip read on the first screen.
            MorroGhostMascot(
                modifier = Modifier
                    .size(width = 126.dp, height = 176.dp)
                    .graphicsLayer {
                        val p = entrance.value.coerceIn(0f, 1f)
                        alpha = p
                        scaleX = 0.92f + 0.08f * p
                        scaleY = 0.92f + 0.08f * p
                        translationY = (1f - p) * 18f
                    },
                happy = true,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(textReveal.value.coerceIn(0f, 1f))
                    .graphicsLayer { translationY = (1f - textReveal.value) * 10f },
            ) {
                Text(
                    text = "Morro",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 34.sp,
                    letterSpacing = (-0.6).sp,
                    color = MorroColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(10.dp))

                // The signature: hand-drawn in real time with progressive ink stroke
                val pathMeasure = remember { PathMeasure() }
                Canvas(modifier = Modifier.width(30.dp).height(6.dp)) {
                    val fullPath = Path().apply {
                        moveTo(1f, size.height * 0.55f)
                        cubicTo(
                            size.width * 0.32f, size.height * 0.20f,
                            size.width * 0.66f, size.height * 0.85f,
                            size.width - 1f, size.height * 0.42f
                        )
                    }
                    val segmentPath = Path()
                    pathMeasure.setPath(fullPath, false)
                    val stopDist = pathMeasure.length * signatureProgress.value.coerceIn(0f, 1f)
                    pathMeasure.getSegment(0f, stopDist, segmentPath, true)

                    drawPath(
                        path = segmentPath,
                        color = MorroColors.Honey.copy(alpha = 0.75f),
                        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "An alarm that asks you for a promise",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    letterSpacing = 0.sp,
                    color = MorroColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

