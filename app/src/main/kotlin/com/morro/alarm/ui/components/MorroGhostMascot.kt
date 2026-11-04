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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * MorroGhostMascot — Adorable charcoal-black blob with variant eye states.
 *
 * Variants:
 * - `happy = false` (default): neutral open eyes for Pain Hook
 * - `happy = true`: upward-curving happy eyes ^^ for the Placebo Loader
 *
 * Eye color: warm honey amber MorroTokens.AccentPrimary (#FFD54F).
 */
@Composable
fun MorroGhostMascot(
    modifier: Modifier = Modifier,
    happy: Boolean = false
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

    Box(modifier = modifier.size(80.dp, 100.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f + (if (happy) happyOffset else floatOffset)

            drawGhostBody(centerX, centerY, width, height)

            if (happy) {
                drawHappyEyes(centerX, centerY)
            } else {
                drawGhostEyes(centerX, centerY)
            }
        }
    }
}

private fun DrawScope.drawGhostBody(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float
) {
    // Ghost body intentionally removed. Only floating eyes remain. Zero boxes.
}

/** Neutral eyes — open dots for Pain Hook and Contract screens. */
private fun DrawScope.drawGhostEyes(centerX: Float, centerY: Float) {
    val eyeColor = Color(0xFFFFD54F)
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
private fun DrawScope.drawHappyEyes(centerX: Float, centerY: Float) {
    val eyeColor = Color(0xFFFFD54F)
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
