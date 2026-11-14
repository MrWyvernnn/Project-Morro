package com.morro.alarm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroTypography
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun MorroAnalogClock(
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    calendar: Calendar = Calendar.getInstance()
) {
    val colors = morroColors()
    val typo = morroTypography()

    val hour = calendar.get(Calendar.HOUR)
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    val hourFloat = hour + minute / 60f
    val minuteFloat = minute + second / 60f

    val dateFormat = remember { SimpleDateFormat("EEE, MMM d • HH:mm:ss", Locale.getDefault()) }
    val dateStr = remember(calendar) { dateFormat.format(calendar.time).uppercase() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                val radius = size.toPx() / 2f

                // Dial background: Surface with border
                drawCircle(
                    color = colors.surface,
                    radius = radius,
                    center = center
                )
                drawCircle(
                    color = colors.borderSubtle,
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )

                // 12 Hour Markers and 60 Minute Micro-dots
                for (i in 0 until 60) {
                    val angle = i * 6f
                    val isHour = i % 5 == 0
                    val isMajorHour = i % 15 == 0

                    val innerRadius = if (isMajorHour) {
                        radius - 16.dp.toPx()
                    } else if (isHour) {
                        radius - 13.dp.toPx()
                    } else {
                        radius - 8.dp.toPx()
                    }
                    val outerRadius = radius - 6.dp.toPx()

                    val radAngle = Math.toRadians((angle - 90).toDouble())

                    val startX = center.x + innerRadius * kotlin.math.cos(radAngle).toFloat()
                    val startY = center.y + innerRadius * kotlin.math.sin(radAngle).toFloat()
                    val endX = center.x + outerRadius * kotlin.math.cos(radAngle).toFloat()
                    val endY = center.y + outerRadius * kotlin.math.sin(radAngle).toFloat()

                    val strokeWidth = if (isMajorHour) 3.dp.toPx() else if (isHour) 1.5.dp.toPx() else 1.dp.toPx()
                    val tickColor = if (isHour) colors.textPrimary else colors.textDisabled

                    if (!isHour) {
                        drawCircle(
                            color = tickColor,
                            radius = 1.dp.toPx(),
                            center = Offset(endX, endY)
                        )
                    } else {
                        drawLine(
                            color = tickColor,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Hour Hand (TextPrimary, rounded cap, 50% radius)
                rotate(degrees = hourFloat * 30f, pivot = center) {
                    drawLine(
                        color = colors.textPrimary,
                        start = center,
                        end = Offset(center.x, center.y - radius * 0.5f),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Minute Hand (TextPrimary, rounded cap, 72% radius)
                rotate(degrees = minuteFloat * 6f, pivot = center) {
                    drawLine(
                        color = colors.textPrimary,
                        start = center,
                        end = Offset(center.x, center.y - radius * 0.72f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Second Hand (Thin sweeping AccentTactical needle with counterweight tail)
                rotate(degrees = second * 6f, pivot = center) {
                    drawLine(
                        color = colors.accentTactical,
                        start = Offset(center.x, center.y + radius * 0.2f),
                        end = Offset(center.x, center.y - radius * 0.82f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Center Cap Nut (AccentTactical with black center pin)
                drawCircle(
                    color = colors.accentTactical,
                    radius = 4.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = Color.Black,
                    radius = 1.5.dp.toPx(),
                    center = center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = dateStr,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textDisabled,
            letterSpacing = 1.5.sp
        )
    }
}
