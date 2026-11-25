package com.morro.alarm

/**
 * Pure, testable controller that silences Morro's alarm player (`0.0f`) the moment
 * the user starts interacting with a cognitive gate, and restores full volume (`1.0f`)
 * if they go idle for [inactivityTimeoutMs] (15 seconds) so they cannot fall back asleep.
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
            isSilenced = false
            return null
        }
        if (!isSilenced) return null
        val idleMs = (clock() - lastInputAtMs).coerceAtLeast(0L)
        if (idleMs >= inactivityTimeoutMs) {
            isSilenced = false
            onTargetVolume(fullVolume, restoreRampMs)
            return null
        }
        return (inactivityTimeoutMs - idleMs).coerceIn(50L, inactivityTimeoutMs)
    }

    fun resetImmediate() {
        isSilenced = false
    }
}
