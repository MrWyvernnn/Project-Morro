package com.morro.alarm

import kotlin.math.roundToInt

/**
 * Pure timing for the 30-second grace window that QR and photo gates open with.
 *
 * The window is driven by an absolute `elapsedRealtime` deadline, not by a count of one-second
 * ticks. A tick counter drifts whenever a tick is late, and before this file the loop decremented
 * a number every second, so a delayed coroutine made the window longer than 30 seconds while the
 * banner claimed 30. A deadline cannot drift: however late a tick runs, it is measured against the
 * same instant.
 *
 * Kept out of `AlarmActivity` so the arithmetic is testable without an Android runtime.
 */
internal object GraceWindow {

    /** The full grace window. Volume is ducked for exactly this long. */
    const val DURATION_MS = 30_000L

    /**
     * What the alarm is ducked to during the window.
     *
     * Shared with [GateSilenceController.DUCKED_VOLUME] on purpose: the grace window and the
     * cognitive gate are mutually exclusive, but they write the same player, and a user must not
     * hear 20% one morning and 30% the next depending on which gate their mission opened.
     */
    const val VOLUME = GateSilenceController.DUCKED_VOLUME

    /** [VOLUME] as a whole percentage, for the on-screen banner. */
    val volumePercent: Int get() = (VOLUME * 100).roundToInt()

    /** The window in whole seconds, for the initial banner value. */
    const val DURATION_SECONDS: Int = (DURATION_MS / 1_000L).toInt()

    /**
     * Seconds still showing on the banner, rounded up so the banner says `1` for the last second
     * instead of `0` while the alarm is still ducked. Never negative and never above the window.
     */
    fun secondsRemaining(deadlineMs: Long, nowMs: Long): Int {
        val remainingMs = deadlineMs - nowMs
        if (remainingMs <= 0L) return 0
        val seconds = ((remainingMs + 999L) / 1_000L).toInt()
        return seconds.coerceIn(0, DURATION_SECONDS)
    }

    /**
     * How long the countdown coroutine may sleep before it must look at the clock again.
     *
     * At most one second, so the banner keeps ticking, and never past the deadline, so a late wake
     * cannot overshoot the restore. `0` means the window is over.
     */
    fun nextTickDelayMs(deadlineMs: Long, nowMs: Long): Long {
        val remainingMs = (deadlineMs - nowMs).coerceAtLeast(0L)
        return minOf(remainingMs, 1_000L)
    }
}
