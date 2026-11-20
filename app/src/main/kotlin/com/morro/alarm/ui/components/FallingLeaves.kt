package com.morro.alarm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.morro.alarm.ui.motion.rememberReducedMotion
import kotlin.math.sin

/**
 * One leaf on the wire: where it starts, how far it falls per cycle, and how it wobbles.
 *
 * All values are fractions of the screen, so the same list works on any size. `spin` is in full
 * turns per cycle, and alternating direction is what stops fourteen leaves from reading as a
 * marching band.
 */
private data class LeafSpec(
    val x: Float,
    val start: Float,
    val fall: Float,
    val sizeFraction: Float,
    val sway: Float,
    val phase: Float,
    val spin: Float,
    val tint: Color,
)

/** Autumn in four tones: ember, straw, ochre, rust. Flat fills, no gradients. */
private val LeafEmber = Color(0xFFE79056)
private val LeafStraw = Color(0xFFD8B06A)
private val LeafOchre = Color(0xFFC9A15A)
private val LeafRust = Color(0xFFB2643C)

private val Leaves = listOf(
    LeafSpec(0.12f, 0.02f, 0.55f, 0.052f, 0.030f, 0.4f, 0.55f, LeafEmber),
    LeafSpec(0.86f, 0.18f, 0.42f, 0.038f, 0.034f, 1.9f, -0.40f, LeafOchre),
    LeafSpec(0.42f, 0.34f, 0.61f, 0.046f, 0.026f, 3.1f, 0.70f, LeafStraw),
    LeafSpec(0.68f, 0.09f, 0.48f, 0.034f, 0.040f, 4.6f, -0.62f, LeafRust),
    LeafSpec(0.24f, 0.62f, 0.53f, 0.044f, 0.028f, 0.9f, 0.48f, LeafOchre),
    LeafSpec(0.92f, 0.47f, 0.38f, 0.030f, 0.022f, 2.4f, -0.75f, LeafStraw),
    LeafSpec(0.55f, 0.71f, 0.58f, 0.040f, 0.032f, 5.2f, 0.36f, LeafEmber),
    LeafSpec(0.06f, 0.40f, 0.50f, 0.036f, 0.026f, 1.3f, -0.52f, LeafRust),
    LeafSpec(0.78f, 0.83f, 0.45f, 0.048f, 0.030f, 3.8f, 0.66f, LeafOchre),
    LeafSpec(0.34f, 0.12f, 0.57f, 0.032f, 0.036f, 5.7f, -0.34f, LeafStraw),
    LeafSpec(0.62f, 0.55f, 0.40f, 0.036f, 0.024f, 0.2f, 0.58f, LeafEmber),
    LeafSpec(0.18f, 0.88f, 0.62f, 0.042f, 0.034f, 4.1f, -0.70f, LeafRust),
    LeafSpec(0.95f, 0.72f, 0.44f, 0.034f, 0.026f, 2.9f, 0.44f, LeafOchre),
    LeafSpec(0.48f, 0.26f, 0.66f, 0.050f, 0.030f, 6.0f, -0.58f, LeafStraw),
)

/**
 * FallingLeaves — the autumn sky.
 *
 * The same idea as [DriftingClouds] with the axis turned: solid, hand-drawn leaves drifting down,
 * swaying on one shared clock so the layers cannot stutter against each other. No blur, no
 * transparency, no gradient — paper leaves on black, not weather.
 *
 * @param intensity scales leaf size, kept for call-site parity with the clouds.
 */
@Composable
fun FallingLeaves(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    durationMillis: Int = 180_000,
) {
    val reduced = rememberReducedMotion()

    // Reduced motion: hold the leaves still instead of running a transition whose value is ignored.
    val t = if (reduced) 0.38f else rememberLeafDrift(durationMillis)
    val presence = intensity.coerceIn(0.4f, 1f)

    Canvas(modifier = modifier.fillMaxSize()) {
        // 1.2 screens of travel, so a leaf leaves the bottom before it reappears at the top.
        val span = 1.2f

        Leaves.forEach { leaf ->
            val raw = leaf.start + t * leaf.fall
            val wrapped = ((raw % span) + span) % span
            val y = (wrapped - 0.1f) * size.height

            val sway = if (reduced) 0f else sin(t * 6.283f * 2f + leaf.phase) * leaf.sway
            val x = (leaf.x + sway) * size.width

            val length = size.minDimension * leaf.sizeFraction * presence
            val spin = if (reduced) leaf.phase * 20f else t * 360f * leaf.spin + leaf.phase * 20f

            drawLeaf(
                center = Offset(x, y),
                length = length,
                width = length * 0.44f,
                rotation = spin,
                color = leaf.tint,
            )
        }
    }
}

/**
 * A single leaf: two curves meeting at a point, closed.
 *
 * Deliberately one shape, like the clouds — no veins, no stem, no shadow. At this size a simple
 * silhouette reads as a leaf; detail would only look like noise on a black field.
 */
private fun DrawScope.drawLeaf(
    center: Offset,
    length: Float,
    width: Float,
    rotation: Float,
    color: Color,
) {
    rotate(degrees = rotation, pivot = center) {
        val path = Path().apply {
            moveTo(center.x, center.y - length / 2f)
            cubicTo(
                center.x + width, center.y - length * 0.12f,
                center.x + width * 0.50f, center.y + length * 0.30f,
                center.x, center.y + length / 2f
            )
            cubicTo(
                center.x - width * 0.50f, center.y + length * 0.30f,
                center.x - width, center.y - length * 0.12f,
                center.x, center.y - length / 2f
            )
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
private fun rememberLeafDrift(durationMillis: Int): Float {
    val transition = rememberInfiniteTransition(label = "leaves")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing)
        ),
        label = "leafDrift"
    )
    return progress
}
