package com.morro.alarm.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.components.DriftingClouds
import com.morro.alarm.ui.components.MorroGhostMascot
import com.morro.alarm.ui.components.MorroSkyScrim
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.motion.staggeredReveal
import com.morro.alarm.ui.theme.MorroColors
import kotlinx.coroutines.delay

/** Long enough to read the line twice, short enough to stay inside a greeting. */
private const val PersonalizeMillis = 1_900
private const val ReducedHoldMillis = 240L

/**
 * The beat between the questions and the plans: the mascot from the opening screen, smiling, and a
 * rail that fills while Morro "reads" the answers.
 *
 * The answers are already written to storage by the questions screen, so this is a paced transition,
 * not a computation. It is still tappable, and with reduced motion the rail simply arrives full and
 * the screen leaves immediately — nobody is ever held behind an animation they asked not to see.
 *
 * The rail is drawn in the draw phase: it reads the animated value while painting instead of during
 * composition, so filling it does not recompose the screen on every frame.
 */
@Composable
fun PersonalizingScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    var finished by remember { mutableStateOf(false) }
    val progress = remember { Animatable(if (reduced) 1f else 0f) }

    fun finish() {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        if (reduced) {
            delay(ReducedHoldMillis)
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(PersonalizeMillis, easing = MorroMotion.MoveEasing)
            )
            delay(150L)
        }
        finish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MorroColors.Canvas)
            .windowInsetsPadding(WindowInsets.systemBars)
            // A greeting, not a gate: one tap moves on, exactly like the opening screen.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { finish() }
            ),
        contentAlignment = Alignment.Center
    ) {
        DriftingClouds(intensity = 0.65f)
        // The label and the rail sit on the same black band as every other intro screen.
        MorroSkyScrim()

        Column(
            modifier = Modifier
                // Cap first, then fill: the other order pins the column to the full width.
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MorroGhostMascot(
                modifier = Modifier
                    .size(width = 124.dp, height = 138.dp)
                    .staggeredReveal(0),
                happy = true
            )

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "Personalizing your experience",
                modifier = Modifier.staggeredReveal(1),
                color = MorroColors.TextPrimary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Reading the answers you just gave.",
                modifier = Modifier.staggeredReveal(2),
                color = MorroColors.TextSecondary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .staggeredReveal(3)
            ) {
                val radius = size.height / 2f
                drawRoundRect(
                    color = MorroColors.BorderSubtle,
                    cornerRadius = CornerRadius(radius, radius)
                )
                if (progress.value > 0f) {
                    drawRoundRect(
                        color = MorroColors.TextPrimary.copy(alpha = 0.75f),
                        size = Size(size.width * progress.value, size.height),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "One promise, one wake-up.",
                color = MorroColors.TextTertiary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp
            )
        }
    }
}
