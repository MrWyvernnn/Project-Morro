package com.morro.alarm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * A dial, not a decoration: near-black face, one hairline rim, honey-white hands. The only warm
 * element is the second hand and a thin arc that traces how much of the current hour has passed —
 * a quiet sign that time is actually moving, which a static dial never gives you.
 *
 * No gradients, no glow, no numerals. The dashboard owns the digital readout, so the dial stays
 * silent and the two can never disagree.
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

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Face: a warm near-black disc, one tone above the canvas.
            drawCircle(color = Color(0xFF080706), radius = radius, center = center)

            // Rim hairline.
            drawCircle(
                color = MorroColors.TextPrimary.copy(alpha = 0.12f),
                radius = radius - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Elapsed-hour arc: 0 at twelve, filling clockwise. The one warm mark on the dial.
            drawArc(
                color = MorroColors.Honey.copy(alpha = 0.55f),
                startAngle = -90f,
                sweepAngle = ((minuteFloat / 60f) * 360f),
                useCenter = false,
                topLeft = Offset(center.x - (radius - 2.dp.toPx()), center.y - (radius - 2.dp.toPx())),
                size = androidx.compose.ui.geometry.Size(
                    (radius - 2.dp.toPx()) * 2f,
                    (radius - 2.dp.toPx()) * 2f
                ),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Markers: hour ticks, minute dots. Two weights, no numerals.
            for (i in 0 until 60) {
                val angle = (i * 6f - 90f) * (Math.PI / 180f).toFloat()
                if (i % 5 == 0) {
                    val inner = radius - 13.dp.toPx()
                    val outer = radius - 7.dp.toPx()
                    drawLine(
                        color = MorroColors.TextPrimary.copy(alpha = if (i % 15 == 0) 0.55f else 0.32f),
                        start = Offset(center.x + inner * cos(angle), center.y + inner * sin(angle)),
                        end = Offset(center.x + outer * cos(angle), center.y + outer * sin(angle)),
                        strokeWidth = if (i % 15 == 0) 1.8.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    val dotRadius = radius - 9.dp.toPx()
                    drawCircle(
                        color = MorroColors.TextPrimary.copy(alpha = 0.16f),
                        radius = 0.7.dp.toPx(),
                        center = Offset(
                            center.x + dotRadius * cos(angle),
                            center.y + dotRadius * sin(angle)
                        )
                    )
                }
            }

            // Hour hand.
            rotate(degrees = hourFloat * 30f, pivot = center) {
                drawLine(
                    color = MorroColors.TextPrimary,
                    start = Offset(center.x, center.y + radius * 0.05f),
                    end = Offset(center.x, center.y - radius * 0.46f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Minute hand.
            rotate(degrees = minuteFloat * 6f, pivot = center) {
                drawLine(
                    color = MorroColors.TextPrimary,
                    start = Offset(center.x, center.y + radius * 0.07f),
                    end = Offset(center.x, center.y - radius * 0.68f),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Second hand: honey, hairline, with a counterweight.
            rotate(degrees = second * 6f, pivot = center) {
                drawLine(
                    color = MorroColors.Honey.copy(alpha = 0.85f),
                    start = Offset(center.x, center.y + radius * 0.16f),
                    end = Offset(center.x, center.y - radius * 0.78f),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Centre pin.
            drawCircle(color = MorroColors.TextPrimary, radius = 3.2.dp.toPx(), center = center)
            drawCircle(color = Color.Black, radius = 1.3.dp.toPx(), center = center)
        }
    }
}
