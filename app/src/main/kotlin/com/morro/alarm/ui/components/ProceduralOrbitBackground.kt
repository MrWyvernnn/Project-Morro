package com.morro.alarm.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * ProceduralOrbitBackground — Ambient orbital animation behind onboarding screens.
 *
 * Features:
 * - Concentric orbital rings rotating slowly (18s loop)
 * - Breathing alpha (0.05f → 0.15f) for subtle pulsing
 * - 2–3 orbiting particle dots as nodes around the deep void core
 * - Zero external assets, pure Canvas drawing
 * - Positioned slightly above viewport midpoint for visual intrigue
 */
@Composable
fun ProceduralOrbitBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbitAnimation")

    // Slow rotation: 18-second loop
    val rotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, delayMillis = 0)
        ),
        label = "ringRotation"
    )

    // Breathing alpha: 0.05f → 0.15f → 0.05f (6 seconds per cycle)
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, delayMillis = 0),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )

    Canvas(modifier = modifier) {
        // Center slightly above midpoint for visual intrigue
        val centerX = size.width / 2f
        val centerY = size.height / 2.2f

        // Draw concentric orbital rings
        drawOrbitalRings(centerX, centerY, rotationDegrees, alpha)

        // Draw orbiting particle dots
        drawOrbitingParticles(centerX, centerY, rotationDegrees, alpha)
    }
}

private fun DrawScope.drawOrbitalRings(
    centerX: Float,
    centerY: Float,
    rotationDegrees: Float,
    alpha: Float
) {
    val orbitColor = Color.White.copy(alpha = alpha * 0.05f) // Subtle warm white orbits

    // Ring 1: Large outer ring
    drawOrbit(
        centerX = centerX,
        centerY = centerY,
        radiusDp = 120.dp.toPx(),
        strokeColor = orbitColor,
        rotation = rotationDegrees
    )

    // Ring 2: Medium ring
    drawOrbit(
        centerX = centerX,
        centerY = centerY,
        radiusDp = 80.dp.toPx(),
        strokeColor = orbitColor.copy(alpha = alpha * 0.07f),
        rotation = -rotationDegrees * 0.7f
    )

    // Ring 3: Small inner ring
    drawOrbit(
        centerX = centerX,
        centerY = centerY,
        radiusDp = 40.dp.toPx(),
        strokeColor = orbitColor.copy(alpha = alpha * 0.10f),
        rotation = rotationDegrees * 1.2f
    )

    // Deep void core: subtle pulsing circle at center (no glow)
    // Removed to keep it minimal
}

private fun DrawScope.drawOrbit(
    centerX: Float,
    centerY: Float,
    radiusDp: Float,
    strokeColor: Color,
    rotation: Float
) {
    // Draw orbit as a series of dashes to simulate a ring path
    val dashCount = 32
    val dashLength = 4.dp.toPx()
    val gapLength = 6.dp.toPx()

    repeat(dashCount) { i ->
        val angle = (i * 360f / dashCount + rotation) * PI / 180f
        val x1 = centerX + (radiusDp * cos(angle)).toFloat()
        val y1 = centerY + (radiusDp * sin(angle)).toFloat()

        val nextAngle = ((i + 1) * 360f / dashCount + rotation) * PI / 180f
        val x2 = centerX + (radiusDp * cos(nextAngle)).toFloat()
        val y2 = centerY + (radiusDp * sin(nextAngle)).toFloat()

        drawLine(
            color = strokeColor,
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun DrawScope.drawOrbitingParticles(
    centerX: Float,
    centerY: Float,
    rotationDegrees: Float,
    alpha: Float
) {
    val particleColor = Color(0xFF00F0FF).copy(alpha = alpha * 0.8f)
    val particleRadius = 3.dp.toPx()

    // Particle 1: Outer ring orbit
    val angle1 = (rotationDegrees + 45f) * PI / 180f
    val radius1 = 120.dp.toPx()
    drawCircle(
        color = particleColor,
        radius = particleRadius,
        center = Offset(
            centerX + (radius1 * cos(angle1)).toFloat(),
            centerY + (radius1 * sin(angle1)).toFloat()
        )
    )

    // Particle 2: Middle ring orbit (counter-rotating)
    val angle2 = (-rotationDegrees * 0.8f + 180f) * PI / 180f
    val radius2 = 80.dp.toPx()
    drawCircle(
        color = particleColor.copy(alpha = alpha * 0.6f),
        radius = particleRadius * 0.8f,
        center = Offset(
            centerX + (radius2 * cos(angle2)).toFloat(),
            centerY + (radius2 * sin(angle2)).toFloat()
        )
    )

    // Particle 3: Inner ring orbit (fast)
    val angle3 = (rotationDegrees * 1.5f + 270f) * PI / 180f
    val radius3 = 40.dp.toPx()
    drawCircle(
        color = particleColor.copy(alpha = alpha * 0.7f),
        radius = particleRadius * 0.6f,
        center = Offset(
            centerX + (radius3 * cos(angle3)).toFloat(),
            centerY + (radius3 * sin(angle3)).toFloat()
        )
    )
}
