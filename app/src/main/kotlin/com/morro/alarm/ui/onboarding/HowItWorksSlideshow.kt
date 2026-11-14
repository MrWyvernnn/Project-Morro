package com.morro.alarm.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.theme.MorroColors
import kotlinx.coroutines.launch

/**
 * Prompt 4 - three slides explaining how Morro works, between the questions and the dashboard.
 *
 * The idea: Morro's loop is a rhythm, not a feature list. Each slide is one beat - you set it, you
 * prove it, you hear it back - and each is drawn as a single moving diagram rather than a stock icon.
 * The background, tokens and progress language are inherited from the questions screen, so the intro
 * reads as one continuous thing instead of three separate screens.
 *
 * Swipe or tap to advance. Skip is available until the last slide; Get Started closes the intro and
 * writes the completion gate.
 */

private object SlideTokens {
    val accent = MorroColors.AccentTactical
    val ink = MorroColors.TextPrimary
    val inkSecondary = MorroColors.TextSecondary
    val inkTertiary = MorroColors.TextDisabled
    val dotIdle = MorroColors.BorderSubtle
    val ground = MorroColors.Canvas
}

private data class IntroSlide(
    val title: String,
    val body: String,
    val subtitle: String,
    val art: SlideArt,
)

private val slides = listOf(
    IntroSlide(
        title = "SET YOUR ALARM",
        body = "Pick your wake-up time and choose a friction mission.",
        subtitle = "Voice promise, QR scan, photo hunt, math puzzle, or affirmation.",
        art = SlideArt.Clock
    ),
    IntroSlide(
        title = "WAKE UP & PROVE IT",
        body = "When the alarm fires, complete your mission to silence it.",
        subtitle = "No snooze. No dismiss button. Just action.",
        art = SlideArt.PhoneAlarm
    ),
    IntroSlide(
        title = "HEAR YOURSELF",
        body = "Your own voice plays back your commitment.",
        subtitle = "Then record tomorrow's promise. The cycle continues.",
        art = SlideArt.Waveform
    )
)

@Composable
fun HowItWorksSlideshow(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { slides.size })

    // One clock for every drawing, so the three illustrations breathe together.
    val transition = rememberInfiniteTransition(label = "slideIllustrations")
    val rawPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "illustrationPhase"
    )
    val phase = if (reducedMotion) 0.4f else rawPhase

    val isLast = pagerState.currentPage == slides.lastIndex

    fun advance() {
        if (isLast) {
            onFinished()
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                val next = pagerState.currentPage + 1
                if (reducedMotion) pagerState.scrollToPage(next) else pagerState.animateScrollToPage(next)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AmbientGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MORRO",
                    color = SlideTokens.accent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!isLast) {
                    TextButton(
                        onClick = { onFinished() },
                        colors = ButtonDefaults.textButtonColors(contentColor = SlideTokens.inkTertiary)
                    ) {
                        Text(
                            text = "SKIP",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                // A light drag is enough to move on; the illustrations stay composed either way.
                pageSpacing = 0.dp
            ) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { advance() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SlideIllustration(art = slide.art, phase = phase)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = slide.title,
                        style = TextStyle(
                            color = SlideTokens.ink,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            letterSpacing = 0.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = slide.body,
                        style = TextStyle(
                            color = SlideTokens.inkSecondary,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            letterSpacing = 0.3.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = slide.subtitle,
                        style = TextStyle(
                            color = SlideTokens.inkTertiary,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            letterSpacing = 0.3.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Dots(
                count = slides.size,
                active = pagerState.currentPage
            )

            // The button's slot is reserved from the first slide so nothing shifts when it appears.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isLast,
                    enter = fadeIn(animationSpec = tween(durationMillis = 220)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 160))
                ) {
                    Button(
                        onClick = { onFinished() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SlideTokens.accent,
                            contentColor = SlideTokens.ground
                        )
                    ) {
                        Text(
                            text = "Get Started",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 16.sp,
                            letterSpacing = 0.3.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // A tick when the person lands on a new slide, on the same frame the page settles.
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}

/** Three dots: the active one stretches into a pill, so position reads even without colour. */
@Composable
private fun Dots(count: Int, active: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 0 until count) {
            val isActive = index == active
            val width by animateDpAsState(
                targetValue = if (isActive) 22.dp else 8.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
                label = "dotWidth"
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(if (isActive) SlideTokens.accent else SlideTokens.dotIdle)
            )
        }
    }
}
