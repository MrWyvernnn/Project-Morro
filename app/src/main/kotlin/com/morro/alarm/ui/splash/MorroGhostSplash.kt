package com.morro.alarm.ui.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.components.DriftingClouds
import com.morro.alarm.ui.components.MorroGhostMascot
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroSpring
import kotlinx.coroutines.delay

private const val SplashHoldMillis = 2_200L
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
            textReveal.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
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
        DriftingClouds(modifier = Modifier.fillMaxSize(), intensity = 1f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MorroGhostMascot(
                modifier = Modifier
                    .size(width = 124.dp, height = 138.dp)
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

                // The signature: a short, slightly uneven rule that looks drawn rather than ruled.
                Canvas(modifier = Modifier.width(30.dp).height(6.dp)) {
                    val path = Path().apply {
                        moveTo(1f, size.height * 0.55f)
                        cubicTo(
                            size.width * 0.32f, size.height * 0.20f,
                            size.width * 0.66f, size.height * 0.85f,
                            size.width - 1f, size.height * 0.42f
                        )
                    }
                    drawPath(
                        path = path,
                        color = MorroColors.AccentTactical.copy(alpha = 0.65f),
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
