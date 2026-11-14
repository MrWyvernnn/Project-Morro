package com.morro.alarm.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.theme.MorroColors
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * The dashboard clock.
 *
 * Face #050508 with a very quiet radial lift so it reads as a dial rather than a hole. The rim is
 * a single sweep gradient from orange to pink and back — the only gradient on the screen. Hands are
 * white, the second hand is orange, and the centre pin is a pink dot inside a soft pink glow.
 *
 * The composable draws no date or time text: the dashboard owns one readout, so the two can never
 * disagree.
 */
@Composable
fun MorroAnalogClock(
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    calendar: Calendar = Calendar.getInstance()
) {
    val hour = calendar.get(Calendar.HOUR)
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    val hourFloat = hour + minute / 60f
    val minuteFloat = minute + second / 60f

    // Slow breathing on the centre glow only. Ambient loop, so a tween is correct here.
    val pulse by rememberInfiniteTransition(label = "clockPin").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pinPulse"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Dial: near-black, lifted very slightly towards the centre.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0A0A10), Color(0xFF050508)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Outer glow of the rim, then the rim itself.
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        MorroColors.AccentTactical.copy(alpha = 0.18f),
                        MorroColors.AccentBubbly.copy(alpha = 0.16f),
                        MorroColors.AccentTactical.copy(alpha = 0.18f)
                    ),
                    center = center
                ),
                radius = radius - 2.dp.toPx(),
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        MorroColors.AccentTactical,
                        MorroColors.AccentBubbly,
                        MorroColors.AccentTactical
                    ),
                    center = center
                ),
                radius = radius - 2.dp.toPx(),
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Minute and hour markers, no numerals.
            for (i in 0 until 60) {
                val angle = i * 6f
                val isHour = i % 5 == 0
                val rad = Math.toRadians((angle - 90).toDouble())
                if (isHour) {
                    val inner = radius - 12.dp.toPx()
                    val outer = radius - 6.dp.toPx()
                    drawLine(
                        color = MorroColors.TextSecondary.copy(alpha = 0.55f),
                        start = Offset(
                            center.x + inner * cos(rad).toFloat(),
                            center.y + inner * sin(rad).toFloat()
                        ),
                        end = Offset(
                            center.x + outer * cos(rad).toFloat(),
                            center.y + outer * sin(rad).toFloat()
                        ),
                        strokeWidth = if (i % 15 == 0) 2.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    val dotRadius = radius - 8.dp.toPx()
                    drawCircle(
                        color = MorroColors.TextTertiary.copy(alpha = 0.45f),
                        radius = 0.8.dp.toPx(),
                        center = Offset(
                            center.x + dotRadius * cos(rad).toFloat(),
                            center.y + dotRadius * sin(rad).toFloat()
                        )
                    )
                }
            }

            // Hour hand.
            rotate(degrees = hourFloat * 30f, pivot = center) {
                drawLine(
                    color = Color.White,
                    start = Offset(center.x, center.y + radius * 0.06f),
                    end = Offset(center.x, center.y - radius * 0.48f),
                    strokeWidth = 4.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Minute hand.
            rotate(degrees = minuteFloat * 6f, pivot = center) {
                drawLine(
                    color = Color.White,
                    start = Offset(center.x, center.y + radius * 0.08f),
                    end = Offset(center.x, center.y - radius * 0.70f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Second hand: orange, with a short counterweight.
            rotate(degrees = second * 6f, pivot = center) {
                drawLine(
                    color = MorroColors.AccentTactical,
                    start = Offset(center.x, center.y + radius * 0.18f),
                    end = Offset(center.x, center.y - radius * 0.80f),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Glowing pink pin.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MorroColors.AccentBubbly.copy(alpha = 0.45f + 0.15f * pulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = 18.dp.toPx()
                ),
                radius = 18.dp.toPx(),
                center = center
            )
            drawCircle(color = MorroColors.AccentBubbly, radius = 4.dp.toPx(), center = center)
            drawCircle(color = Color.Black, radius = 1.4.dp.toPx(), center = center)
        }
    }
}
