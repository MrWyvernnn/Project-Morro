package com.morro.alarm.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.lerpColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * One leaf on the wire: where it starts, how far it falls per cycle, and how it moves.
 *
 * All positions are fractions of the screen, so the same list works at any size. `spin` is full
 * turns per cycle and alternates sign, which is what stops twelve leaves from looking like a
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
    val flutter: Float,
    val tint: Color,
)

/** Autumn in four tones: ember, straw, ochre, rust. Flat fills, no gradients. */
private val LeafEmber = Color(0xFFE79056)
private val LeafStraw = Color(0xFFD8B06A)
private val LeafOchre = Color(0xFFC9A15A)
private val LeafRust = Color(0xFFB2643C)

private val Leaves = listOf(
    LeafSpec(0.12f, 0.02f, 0.55f, 0.052f, 0.030f, 0.4f, 0.55f, 1.7f, LeafEmber),
    LeafSpec(0.86f, 0.18f, 0.42f, 0.038f, 0.034f, 1.9f, -0.40f, 2.3f, LeafOchre),
    LeafSpec(0.42f, 0.34f, 0.61f, 0.046f, 0.026f, 3.1f, 0.70f, 1.3f, LeafStraw),
    LeafSpec(0.68f, 0.09f, 0.48f, 0.034f, 0.040f, 4.6f, -0.62f, 2.9f, LeafRust),
    LeafSpec(0.24f, 0.62f, 0.53f, 0.044f, 0.028f, 0.9f, 0.48f, 1.1f, LeafOchre),
    LeafSpec(0.92f, 0.47f, 0.38f, 0.030f, 0.022f, 2.4f, -0.75f, 2.6f, LeafStraw),
    LeafSpec(0.55f, 0.71f, 0.58f, 0.040f, 0.032f, 5.2f, 0.36f, 1.9f, LeafEmber),
    LeafSpec(0.06f, 0.40f, 0.50f, 0.036f, 0.026f, 1.3f, -0.52f, 2.1f, LeafRust),
    LeafSpec(0.78f, 0.83f, 0.45f, 0.048f, 0.030f, 3.8f, 0.66f, 1.5f, LeafOchre),
    LeafSpec(0.34f, 0.12f, 0.57f, 0.032f, 0.036f, 5.7f, -0.34f, 2.7f, LeafStraw),
    LeafSpec(0.62f, 0.55f, 0.40f, 0.036f, 0.024f, 0.2f, 0.58f, 1.2f, LeafEmber),
    LeafSpec(0.18f, 0.88f, 0.62f, 0.042f, 0.034f, 4.1f, -0.70f, 2.4f, LeafRust),
    LeafSpec(0.95f, 0.72f, 0.44f, 0.034f, 0.026f, 2.9f, 0.44f, 1.6f, LeafOchre),
    LeafSpec(0.48f, 0.26f, 0.66f, 0.050f, 0.030f, 6.0f, -0.58f, 2.2f, LeafStraw),
)

/**
 * A leaf at the origin, one unit tall and about 0.9 wide, pointing up.
 *
 * Allocated once at class load and never rebuilt: every frame draws this same buffer under a
 * transform. That is the difference between 14 shapes per frame and 14 × 60 allocations per second.
 */
private val LeafBody = Path().apply {
    moveTo(0f, -0.5f)
    cubicTo(0.44f, -0.12f, 0.22f, 0.30f, 0f, 0.5f)
    cubicTo(-0.22f, 0.30f, -0.44f, -0.12f, 0f, -0.5f)
    close()
}

/**
 * FallingLeaves — the autumn sky.
 *
 * The same idea as [DriftingClouds] with the axis turned: solid, hand-drawn leaves drifting down,
 * swaying on one shared clock so the layers cannot stutter against each other. No blur, no
 * transparency, no gradient — paper leaves on black, not weather.
 *
 * Performance: the drift clock is a [State] read inside the draw block, so a frame invalidates the
 * draw phase only. The leaves are one cached [Path] drawn under a transform, so a frame allocates
 * nothing. The whole animation stops when the system asks for reduced motion.
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

    // With reduced motion on, no transition is created at all: the leaves are simply held in place.
    val drift: State<Float>? = if (reduced) null else rememberLeafDrift(durationMillis)
    val presence = intensity.coerceIn(0.4f, 1f)

    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw-phase read: no recomposition per frame, only a redraw.
        val t = drift?.value ?: 0.38f
        val span = 1.2f

        Leaves.forEach { leaf ->
            val raw = leaf.start + t * leaf.fall
            val wrapped = ((raw % span) + span) % span
            val y = (wrapped - 0.1f) * size.height
            val length = size.minDimension * leaf.sizeFraction * presence

            // Skip anything whose whole body is outside the viewport: a leaf near the wrap edge
            // costs a clip check instead of a path draw.
            if (y + length < 0f || y - length > size.height) return@forEach

            val sway = if (reduced) 0f else sin(t * 6.283f * 2f + leaf.phase) * leaf.sway
            val x = (leaf.x + sway) * size.width

            val spin = if (reduced) leaf.phase * 20f else t * 360f * leaf.spin + leaf.phase * 20f
            // Turning about its own stem: the width collapses and recovers, which reads as a leaf
            // tumbling in air rather than a flat shape spinning on the screen.
            val flutter = if (reduced) 1f else 0.35f + 0.65f * abs(
                cos(t * 6.283f * leaf.flutter + leaf.phase)
            )

            drawLeaf(
                center = Offset(x, y),
                length = length,
                rotation = spin,
                flutter = flutter,
                tint = leaf.tint,
            )
        }
    }
}

/**
 * Draws one leaf: body, then a single midrib.
 *
 * Both are drawn in the unit space defined by [LeafBody], scaled to [length], so the shape has no
 * resolution-dependent constants and nothing is allocated per frame. The midrib is a darkened step
 * of the leaf's own tone — enough to read at arm's length, quiet enough not to look drawn-on.
 */
private fun DrawScope.drawLeaf(
    center: Offset,
    length: Float,
    rotation: Float,
    flutter: Float,
    tint: Color,
) {
    withTransform({
        translate(center.x, center.y)
        rotate(degrees = rotation, pivot = Offset.Zero)
        scale(scaleX = flutter, scaleY = 1f, pivot = Offset.Zero)
        scale(scaleX = length, scaleY = length, pivot = Offset.Zero)
    }) {
        drawPath(path = LeafBody, color = tint)
        drawLine(
            color = lerpColor(tint, Color.Black, 0.45f),
            start = Offset(0f, -0.42f),
            end = Offset(0f, 0.42f),
            strokeWidth = 0.05f
        )
    }
}

@Composable
private fun rememberLeafDrift(durationMillis: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "leaves")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing)
        ),
        label = "leafDrift"
    )
}
