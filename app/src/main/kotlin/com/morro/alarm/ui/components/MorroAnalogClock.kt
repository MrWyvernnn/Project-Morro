package com.morro.alarm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * A precision timepiece: warm near-black dial, crisp hairline rim, and richly detailed
 * hour markers with distinct cardinal indices (12, 3, 6, 9) and fine minute pips.
 *
 * Warm accents:
 *  - The sweeping elapsed-hour progress arc that tracks the fraction of the current hour passed.
 *  - The hairline honey needle second hand with a counterweight tail.
 *  - The jewel-like center cap nut with honey collar and ink core pin.
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

            // Face: a warm near-black disc
            drawCircle(color = Color(0xFF070605), radius = radius, center = center)

            // Outer rim hairline
            drawCircle(
                color = MorroColors.TextPrimary.copy(alpha = 0.14f),
                radius = radius - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Inner chapter ring connecting the hour markers
            drawCircle(
                color = MorroColors.TextPrimary.copy(alpha = 0.08f),
                radius = radius - 16.dp.toPx(),
                center = center,
                style = Stroke(width = 0.8.dp.toPx())
            )

            // Hour-progress arc: sweeps from twelve (top) clockwise with minute/60
            drawArc(
                color = MorroColors.Honey.copy(alpha = 0.65f),
                startAngle = -90f,
                sweepAngle = ((minuteFloat / 60f) * 360f),
                useCenter = false,
                topLeft = Offset(center.x - (radius - 2.5.dp.toPx()), center.y - (radius - 2.5.dp.toPx())),
                size = Size(
                    (radius - 2.5.dp.toPx()) * 2f,
                    (radius - 2.5.dp.toPx()) * 2f
                ),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )

            // ── Crisp Clock Markings ─────────────────────────────────────────
            // 12 hour batons with prominent cardinal hierarchy + 60 minute pips
            for (i in 0 until 60) {
                val angleDeg = i * 6f - 90f
                val rad = Math.toRadians(angleDeg.toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()

                val isHour = i % 5 == 0
                val isCardinal = i % 15 == 0
                val isTwelve = i == 0

                if (isTwelve) {
                    // Double baton at 12 o'clock for immediate tactile orientation
                    val outerR = radius - 5.dp.toPx()
                    val innerR = radius - 17.dp.toPx()
                    val offsetPerp = 2.2.dp.toPx()
                    val perpX = -sinA
                    val perpY = cosA

                    listOf(-1f, 1f).forEach { side ->
                        val startX = center.x + innerR * cosA + side * offsetPerp * perpX
                        val startY = center.y + innerR * sinA + side * offsetPerp * perpY
                        val endX = center.x + outerR * cosA + side * offsetPerp * perpX
                        val endY = center.y + outerR * sinA + side * offsetPerp * perpY

                        drawLine(
                            color = MorroColors.TextPrimary.copy(alpha = 0.85f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                } else if (isCardinal) {
                    // 3, 6, 9 cardinal markers: bold, confident length
                    val outerR = radius - 5.dp.toPx()
                    val innerR = radius - 17.dp.toPx()
                    drawLine(
                        color = MorroColors.TextPrimary.copy(alpha = 0.80f),
                        start = Offset(center.x + innerR * cosA, center.y + innerR * sinA),
                        end = Offset(center.x + outerR * cosA, center.y + outerR * sinA),
                        strokeWidth = 2.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else if (isHour) {
                    // Standard hour markers (1, 2, 4, 5, 7, 8, 10, 11)
                    val outerR = radius - 6.dp.toPx()
                    val innerR = radius - 14.dp.toPx()
                    drawLine(
                        color = MorroColors.TextPrimary.copy(alpha = 0.55f),
                        start = Offset(center.x + innerR * cosA, center.y + innerR * sinA),
                        end = Offset(center.x + outerR * cosA, center.y + outerR * sinA),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    // Minute pips
                    val dotR = radius - 7.5.dp.toPx()
                    drawCircle(
                        color = MorroColors.TextPrimary.copy(alpha = 0.22f),
                        radius = 0.9.dp.toPx(),
                        center = Offset(center.x + dotR * cosA, center.y + dotR * sinA)
                    )
                }
            }

            // ── Hands ────────────────────────────────────────────────────────
            // Hour hand (confident, rounded pill)
            rotate(degrees = hourFloat * 30f, pivot = center) {
                drawLine(
                    color = MorroColors.TextPrimary,
                    start = Offset(center.x, center.y + radius * 0.06f),
                    end = Offset(center.x, center.y - radius * 0.48f),
                    strokeWidth = 4.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Minute hand (sleek, elegant baton)
            rotate(degrees = minuteFloat * 6f, pivot = center) {
                drawLine(
                    color = MorroColors.TextPrimary,
                    start = Offset(center.x, center.y + radius * 0.08f),
                    end = Offset(center.x, center.y - radius * 0.70f),
                    strokeWidth = 2.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Second hand: honey, hairline needle with counterweight
            rotate(degrees = second * 6f, pivot = center) {
                drawLine(
                    color = MorroColors.Honey.copy(alpha = 0.90f),
                    start = Offset(center.x, center.y + radius * 0.18f),
                    end = Offset(center.x, center.y - radius * 0.82f),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Centre pin: jewel collar in Honey with ink core
            drawCircle(color = MorroColors.Honey, radius = 4.dp.toPx(), center = center)
            drawCircle(color = Color(0xFF0B0908), radius = 1.8.dp.toPx(), center = center)
        }
    }
}
