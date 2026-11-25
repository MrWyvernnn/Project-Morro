package com.morro.alarm.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.abs

/**
 * MorroMotion — the only place motion numbers live.
 *
 * Rules carried by these tokens:
 *  - Entrances ease out (fast start, long settle) so content arrives rather than slides in.
 *  - Exits are 20% shorter than their entrance and accelerate away.
 *  - Everyday UI never overshoots: the press spring is critically damped. Bounce is reserved for a
 *    single success pulse.
 *  - Lists stagger by 30ms per row, capped at six rows, so a long list never takes longer than
 *    180ms to settle.
 *  - Nothing animates while a finger is down except a direct, un-tweened scale snap.
 */
object MorroMotion {
    const val EnterMillis = 220
    const val MoveMillis = 260
    const val ExpanderMillis = 180
    const val PressMillis = 90
    const val ShakeStepMillis = 40
    const val SuccessPulseMillis = 320
    const val ShimmerMillis = 1_200

    /** Horizontal shake pattern (in dp) for wrong cognitive gate attempts. */
    val WrongShakeOffsetsDp = floatArrayOf(4f, -4f, 2.5f, -2.5f, 0f)

    const val StaggerMillis = 30L
    const val MaxStaggerSteps = 6

    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val MoveEasing: Easing = FastOutSlowInEasing

    /** Critically damped: returns to rest without a single frame of overshoot. */
    val PressRelease: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 900f)

    const val PressedScale = 0.97f
    const val RowPressedScale = 0.985f

    /** Exits run at 80% of the matching entrance. */
    fun exitMillis(enterMillis: Int): Int = (enterMillis * 0.8f).toInt()

    /** 30ms per row, capped so the last row never waits more than six steps. */
    fun staggerDelayMs(index: Int): Long =
        index.coerceIn(0, MaxStaggerSteps - 1) * StaggerMillis

    /**
     * Apple's rubber-band curve: resistance grows the further past the bound a drag goes.
     * At the bound the element stops moving 1:1 and starts yielding progressively.
     */
    fun rubberBand(overshootPx: Float, dimensionPx: Float, constant: Float = 0.55f): Float {
        if (dimensionPx <= 0f) return 0f
        return (overshootPx * dimensionPx * constant) /
            (dimensionPx + constant * abs(overshootPx))
    }
}
