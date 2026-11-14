package com.morro.alarm.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.morro.alarm.ui.theme.MorroColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The three illustrations for the how-it-works slideshow, drawn in code so the app ships no images.
 *
 * One language across all three: hairline geometry in the border token, the single tactical orange
 * doing the work that matters, and a soft pink bloom behind it so the shapes sit in the same light as
 * the rest of onboarding. Each one carries one slow motion that explains the idea - a clock hand
 * sweeping, a ring of alarm waves leaving the phone, a waveform breathing.
 *
 * [phase] runs 0..1 on a loop driven by the slide, so all motion on screen shares one clock. With
 * animations switched off the caller passes a constant and every drawing resolves to its still frame.
 */

internal enum class SlideArt { Clock, PhoneAlarm, Waveform }

@Composable
internal fun SlideIllustration(
    art: SlideArt,
    phase: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(120.dp)) {
        when (art) {
            SlideArt.Clock -> drawClock(phase)
            SlideArt.PhoneAlarm -> drawPhoneAlarm(phase)
            SlideArt.Waveform -> drawWaveform(phase)
        }
    }
}

private val Idle = MorroColors.BorderSubtle
private val Signal = MorroColors.AccentTactical
private val Bloom = MorroColors.AccentBubbly
private val Halo = MorroColors.TextPrimary

/** A clock doing its job: one orange hand sweeping, the hour hand barely moving behind it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClock(phase: Float) {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Bloom.copy(alpha = 0.20f), Color.Transparent),
            center = center,
            radius = radius * 1.25f
        ),
        radius = radius * 1.25f,
        center = center
    )
    drawCircle(color = Idle, radius = radius * 0.86f, center = center, style = Stroke(width = 2f))

    // Twelve ticks, the quarter hours heavier, so the face reads at 120dp.
    for (tick in 0 until 12) {
        val angle = PI / 6.0 * tick
        val inner = radius * 0.74f
        val outer = if (tick % 3 == 0) radius * 0.64f else radius * 0.70f
        drawLine(
            color = Idle,
            start = Offset(center.x + inner * cos(angle).toFloat(), center.y + inner * sin(angle).toFloat()),
            end = Offset(center.x + outer * cos(angle).toFloat(), center.y + outer * sin(angle).toFloat()),
            strokeWidth = if (tick % 3 == 0) 3f else 1.5f,
            cap = StrokeCap.Round
        )
    }

    val minuteAngle = -PI / 2.0 + phase * 2.0 * PI
    val hourAngle = -PI / 2.0 + phase * (PI / 6.0) + PI / 3.0
    drawLine(
        color = Signal,
        start = center,
        end = Offset(
            center.x + radius * 0.52f * cos(minuteAngle).toFloat(),
            center.y + radius * 0.52f * sin(minuteAngle).toFloat()
        ),
        strokeWidth = 3.5f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Signal.copy(alpha = 0.65f),
        start = center,
        end = Offset(
            center.x + radius * 0.32f * cos(hourAngle).toFloat(),
            center.y + radius * 0.32f * sin(hourAngle).toFloat()
        ),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round
    )

    drawCircle(color = Halo, radius = 3f, center = center)
    drawCircle(color = Signal, radius = 5.5f, center = center, style = Stroke(width = 2f))
}

/** A phone with alarm waves leaving it, so the slide shows sound rather than describing it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPhoneAlarm(phase: Float) {
    val width = size.width
    val height = size.height
    val center = Offset(width / 2f, height / 2f)
    val bodyWidth = width * 0.42f
    val bodyHeight = height * 0.68f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Bloom.copy(alpha = 0.18f), Color.Transparent),
            center = center,
            radius = width * 0.6f
        ),
        radius = width * 0.6f,
        center = center
    )

    // Three waves, staggered so one is always leaving while the others dissolve.
    for (wave in 0 until 3) {
        val progress = (phase + wave / 3f) % 1f
        val alpha = (1f - progress) * 0.55f
        val waveRadius = bodyWidth * 0.55f + progress * width * 0.34f
        drawArc(
            color = Signal.copy(alpha = alpha),
            startAngle = -42f,
            sweepAngle = 84f,
            useCenter = false,
            topLeft = Offset(center.x - waveRadius, center.y - waveRadius),
            size = Size(waveRadius * 2f, waveRadius * 2f),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )
        drawArc(
            color = Signal.copy(alpha = alpha),
            startAngle = 138f,
            sweepAngle = 84f,
            useCenter = false,
            topLeft = Offset(center.x - waveRadius, center.y - waveRadius),
            size = Size(waveRadius * 2f, waveRadius * 2f),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )
    }

    drawRoundRect(
        color = Idle,
        topLeft = Offset(center.x - bodyWidth / 2f, center.y - bodyHeight / 2f),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(bodyWidth * 0.22f),
        style = Stroke(width = 2f)
    )
    drawRoundRect(
        color = Idle.copy(alpha = 0.7f),
        topLeft = Offset(center.x - bodyWidth * 0.28f, center.y - bodyHeight * 0.34f),
        size = Size(bodyWidth * 0.56f, bodyHeight * 0.34f),
        cornerRadius = CornerRadius(bodyWidth * 0.08f),
        style = Stroke(width = 1.5f)
    )
    drawRoundRect(
        color = Signal,
        topLeft = Offset(center.x - bodyWidth * 0.20f, center.y + bodyHeight * 0.10f),
        size = Size(bodyWidth * 0.40f, 3f),
        cornerRadius = CornerRadius(1.5f)
    )
    drawCircle(color = Halo.copy(alpha = 0.85f), radius = 2.5f, center = Offset(center.x, center.y + bodyHeight * 0.32f))
}

/** A voice: bars breathing outward from the centre, the way a promise sounds when it is spoken. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveform(phase: Float) {
    val width = size.width
    val height = size.height
    val centerY = height / 2f
    val bars = 25
    val slot = width / bars
    val barWidth = slot * 0.44f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Bloom.copy(alpha = 0.20f), Color.Transparent),
            center = Offset(width / 2f, centerY),
            radius = width * 0.55f
        ),
        radius = width * 0.55f,
        center = Offset(width / 2f, centerY)
    )

    for (bar in 0 until bars) {
        val t = bar / (bars - 1f)
        // Taller through the middle, so the shape has a spine instead of reading as a barcode.
        val envelope = 1f - abs(t - 0.5f) * 1.35f
        val wave = abs(sin((phase * 2f + t * 3f) * PI)).toFloat()
        val barHeight = (height * 0.14f + height * 0.62f * wave * envelope).coerceAtLeast(3f)
        val left = bar * slot + (slot - barWidth) / 2f
        drawRoundRect(
            color = if (bar % 2 == 0) Signal else Signal.copy(alpha = 0.5f),
            topLeft = Offset(left, centerY - barHeight / 2f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f)
        )
    }
}
