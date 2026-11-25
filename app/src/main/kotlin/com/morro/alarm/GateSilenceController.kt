package com.morro.alarm

/**
 * Pure, testable controller that ducks Morro's alarm player while the user interacts with a
 * cognitive gate, and restores full volume when they go idle so they cannot fall back asleep.
 *
 * The invariant this class owns: **whenever the silenced flag clears, the full-volume target has
 * already been emitted.** Clearing the flag without emitting is what turns a duck into a mute, and
 * a silently ringing alarm is worse than no alarm. [resetImmediate] and the inactive branch of
 * [checkWatchdog] therefore restore before they clear, and a controller that was never silenced
 * emits nothing.
 *
 * Never touches system audio streams — only drives Morro's own MediaPlayer volume.
 */
internal class GateSilenceController(
    val inactivityTimeoutMs: Long = 15_000L,
    val silencedVolume: Float = 0.0f,
    val fullVolume: Float = 1.0f,
    private val silenceRampMs: Long = 200L,
    private val restoreRampMs: Long = 600L,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val onTargetVolume: (targetVolume: Float, durationMs: Long) -> Unit
) {
    var isSilenced: Boolean = false
        private set

    var lastInputAtMs: Long = 0L
        private set

    fun onUserAction() {
        lastInputAtMs = clock()
        if (!isSilenced) {
            isSilenced = true
            onTargetVolume(silencedVolume, silenceRampMs)
        }
    }

    /**
     * Evaluates elapsed idle time while in the puzzle gate.
     * Returns the remaining milliseconds to wait if still silenced, or `null` when
     * the watchdog has restored full volume or the gate is no longer active.
     */
    fun checkWatchdog(isPuzzleActive: Boolean): Long? {
        if (!isPuzzleActive) {
            // Leaving the gate must be audible, not just flagged. The flag is bookkeeping; the
            // player only hears the emitted target.
            restoreVolume(restoreRampMs)
            return null
        }
        if (!isSilenced) return null
        val idleMs = (clock() - lastInputAtMs).coerceAtLeast(0L)
        if (idleMs >= inactivityTimeoutMs) {
            restoreVolume(restoreRampMs)
            return null
        }
        return (inactivityTimeoutMs - idleMs).coerceIn(50L, inactivityTimeoutMs)
    }

    fun resetImmediate() {
        restoreVolume(0L)
    }

    /**
     * The one place the flag comes down. Emits first, clears second, and does nothing at all when
     * the player was never ducked — a blind emit here would write volume on every teardown.
     */
    private fun restoreVolume(durationMs: Long) {
        if (isSilenced) onTargetVolume(fullVolume, durationMs)
        isSilenced = false
    }
}
