package com.morro.alarm.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * MorroGhostMascot — Adorable charcoal-black blob mascot with bouncing animation.
 *
 * Features:
 * - Charcoal-black body (#1A1A1A) with glowing warm honey-amber eyes (#FFA726)
 * - Gentle floating micro-animation (breathing 6.dp up/down)
 * - Pure Canvas drawing, no external assets
 * - 80dp × 100dp default size
 */
@Composable
fun MorroGhostMascot(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ghostFloating")

    // Gentle floating animation: ±6.dp over 3 seconds
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, delayMillis = 0),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "ghostFloat"
    )

    Box(
        modifier = modifier
            .size(80.dp, 100.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f + floatOffset

            // Ghost body: soft rounded shape
            drawGhostBody(centerX, centerY, width, height)

            // Glowing cyan eyes
            drawGhostEyes(centerX, centerY)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGhostBody(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float
) {
    val ghostColor = Color(0xFF1A1A1A) // Charcoal black blob
    val bodyRadiusX = width * 0.35f
    val bodyRadiusY = height * 0.4f

    // Main rounded ghost head (soft oval)
    drawOval(
        color = ghostColor,
        topLeft = Offset(centerX - bodyRadiusX, centerY - bodyRadiusY * 0.8f),
        size = androidx.compose.ui.geometry.Size(bodyRadiusX * 2, bodyRadiusY * 1.6f)
    )

    // Wave tail: three subtle rounded bumps at the bottom
    val tailY = centerY + bodyRadiusY * 0.7f
    val tailWidth = bodyRadiusX * 0.5f

    // Left tail bump
    drawOval(
        color = ghostColor,
        topLeft = Offset(centerX - bodyRadiusX - tailWidth * 0.3f, tailY),
        size = androidx.compose.ui.geometry.Size(tailWidth, tailWidth * 1.2f)
    )

    // Center tail bump
    drawOval(
        color = ghostColor,
        topLeft = Offset(centerX - tailWidth * 0.5f, tailY),
        size = androidx.compose.ui.geometry.Size(tailWidth, tailWidth * 1.2f)
    )

    // Right tail bump
    drawOval(
        color = ghostColor,
        topLeft = Offset(centerX + bodyRadiusX - tailWidth * 0.7f, tailY),
        size = androidx.compose.ui.geometry.Size(tailWidth, tailWidth * 1.2f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGhostEyes(
    centerX: Float,
    centerY: Float
) {
    val eyeColor = Color(0xFFFFA726) // Warm honey-amber glow
    val eyeRadius = 4.dp.toPx()
    val eyeGlowRadius = eyeRadius * 1.5f
    val eyeSpacing = 16.dp.toPx()

    // Left eye
    val leftEyeX = centerX - eyeSpacing / 2
    val eyeY = centerY - 8.dp.toPx()

    // Glow effect: larger, more transparent circle
    drawCircle(
        color = eyeColor.copy(alpha = 0.3f),
        radius = eyeGlowRadius,
        center = Offset(leftEyeX, eyeY)
    )

    // Crisp eye pupil
    drawCircle(
        color = eyeColor,
        radius = eyeRadius,
        center = Offset(leftEyeX, eyeY)
    )

    // Right eye
    val rightEyeX = centerX + eyeSpacing / 2

    // Glow effect
    drawCircle(
        color = eyeColor.copy(alpha = 0.3f),
        radius = eyeGlowRadius,
        center = Offset(rightEyeX, eyeY)
    )

    // Crisp eye pupil
    drawCircle(
        color = eyeColor,
        radius = eyeRadius,
        center = Offset(rightEyeX, eyeY)
    )
}
