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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.theme.morroColors

/**
 * MorroGhostMascot — Adorable charcoal-black ghost mascot with variant eye states.
 *
 * Variants:
 * - `happy = false` (default): neutral open eyes for Pain Hook / Contract screens
 * - `happy = true`: upward-curving happy eyes ^^ for the Placebo Loader
 *
 * Visuals:
 * - A soft breathing radial glow (AccentTactical) behind the ghost, fading into
 *   the canvas background.
 * - Gentle floating + breathing idle animation so the ghost feels alive.
 * - Eye color: AccentTactical
 *
 * Design note: zero visible box behind the Ghost mascot — only the radial gradient glow
 * bleeds into the canvas.
 */
@Composable
fun MorroGhostMascot(
    modifier: Modifier = Modifier,
    happy: Boolean = false
) {
    val colors = morroColors()
    val infiniteTransition = rememberInfiniteTransition(label = "ghostMascot")

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

    // Breathing idle animation: subtle scale pulse (0.96 -> 1.04) over 4 seconds
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, delayMillis = 0),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "ghostBreath"
    )

    // Happy variant: gentle bounce up (more cheerful)
    val happyOffset by infiniteTransition.animateFloat(
        initialValue = if (happy) 0f else -6f,
        targetValue = if (happy) -8f else 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (happy) 1800 else 3000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "ghostBounce"
    )

    Box(
        modifier = modifier.size(80.dp, 100.dp)
            // Ambient breathing radial glow behind the ghost: AccentTactical fading into canvas.
            .drawBehind {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val glowRadius = (size.width * 1.6f).coerceAtLeast(1f)
                val breathAlpha = 0.18f + (0.06f * breathScale)
                val glowBrush = Brush.radialGradient(
                    colors = listOf(
                        colors.accentTactical.copy(alpha = breathAlpha),
                        colors.accentTactical.copy(alpha = 0.04f),
                        colors.canvas.copy(alpha = 0.0f)
                    ),
                    center = Offset(centerX, centerY),
                    radius = glowRadius * breathScale
                )
                drawRect(
                    brush = glowBrush,
                    topLeft = Offset(0f, 0f),
                    size = size
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f + (if (happy) happyOffset else floatOffset)

            // Apply breathing scale around the ghost's center.
            scale(scaleX = breathScale, scaleY = breathScale, pivot = Offset(centerX, centerY)) {
                drawGhostBody(centerX, centerY, width, height, colors)

                if (happy) {
                    drawHappyEyes(centerX, centerY, colors)
                } else {
                    drawGhostEyes(centerX, centerY, colors)
                }
            }
        }
    }
}

private fun DrawScope.drawGhostBody(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    colors: com.morro.alarm.ui.theme.MorroColorPalette
) {
    // Charcoal-black silhouette, visible on light canvas
    val bodyColor = Color(0xFF1A1A2E)
    val bodyWidth = width * 0.72f
    val bodyHeight = height * 0.78f

    val path = Path().apply {
        // Top of head (rounded dome)
        moveTo(centerX - bodyWidth / 2, centerY - bodyHeight / 4)
        cubicTo(
            centerX - bodyWidth / 2, centerY - bodyHeight / 2,
            centerX, centerY - bodyHeight / 2,
            centerX, centerY - bodyHeight / 2
        )
        cubicTo(
            centerX + bodyWidth / 2, centerY - bodyHeight / 2,
            centerX + bodyWidth / 2, centerY - bodyHeight / 4,
            centerX + bodyWidth / 2, centerY - bodyHeight / 4
        )
        // Right side wavy bottom
        cubicTo(
            centerX + bodyWidth / 2 + 4, centerY + bodyHeight / 8,
            centerX + bodyWidth / 4, centerY + bodyHeight / 2,
            centerX, centerY + bodyHeight / 2
        )
        // Left side wavy bottom
        cubicTo(
            centerX - bodyWidth / 4, centerY + bodyHeight / 2,
            centerX - bodyWidth / 2 - 4, centerY + bodyHeight / 8,
            centerX - bodyWidth / 2, centerY - bodyHeight / 4
        )
        close()
    }
    drawPath(path, color = bodyColor)
}

/** Neutral eyes — open dots for Pain Hook and Contract screens. */
private fun DrawScope.drawGhostEyes(centerX: Float, centerY: Float, colors: com.morro.alarm.ui.theme.MorroColorPalette) {
    val eyeColor = colors.accentTactical
    val eyeRadius = 4.dp.toPx()
    val eyeGlowRadius = eyeRadius * 1.5f
    val eyeSpacing = 16.dp.toPx()

    val leftEyeX = centerX - eyeSpacing / 2
    val eyeY = centerY - 8.dp.toPx()
    val rightEyeX = centerX + eyeSpacing / 2

    // Glow + crisp dot for each eye
    listOf(leftEyeX, rightEyeX).forEach { x ->
        drawCircle(color = eyeColor.copy(alpha = 0.3f), radius = eyeGlowRadius, center = Offset(x, eyeY))
        drawCircle(color = eyeColor, radius = eyeRadius, center = Offset(x, eyeY))
    }
}

/** Happy eyes — upward curved arcs like ^^ for the Placebo Loader. */
private fun DrawScope.drawHappyEyes(centerX: Float, centerY: Float, colors: com.morro.alarm.ui.theme.MorroColorPalette) {
    val eyeColor = colors.accentTactical
    val eyeSpacing = 16.dp.toPx()
    val eyeY = centerY - 8.dp.toPx()
    val arcWidth = 12.dp.toPx()
    val arcHeight = 6.dp.toPx()

    val leftEyeX = centerX - eyeSpacing / 2
    val rightEyeX = centerX + eyeSpacing / 2

    listOf(leftEyeX, rightEyeX).forEach { cx ->
        // Glow
        drawCircle(
            color = eyeColor.copy(alpha = 0.15f),
            radius = arcWidth * 0.8f,
            center = Offset(cx, eyeY)
        )
        // Happy arc ^^ shape using Path
        val path = Path().apply {
            moveTo(cx - arcWidth / 2, eyeY + arcHeight / 2)
            quadraticTo(cx, eyeY - arcHeight / 2, cx + arcWidth / 2, eyeY + arcHeight / 2)
        }
        drawPath(
            path = path,
            color = eyeColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}