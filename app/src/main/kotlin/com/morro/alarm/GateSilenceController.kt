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
 *  - The duck is a duck, not a mute ([DUCKED_VOLUME]), and the gate has one hard silence budget
 *    ([MAX_SILENCE_WINDOW_MS]) that input cannot extend.
 *  - Once that budget is spent the alarm is loud until the gate is solved.
 *
 * Never touches system audio streams — only drives Morro's own MediaPlayer volume.
 */
internal class GateSilenceController(
    val inactivityTimeoutMs: Long = 15_000L,
    val maxSilenceWindowMs: Long = MAX_SILENCE_WINDOW_MS,
    val silencedVolume: Float = DUCKED_VOLUME,
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

    /**
     * Absolute `elapsedRealtime` at which the ducking budget ends, or 0 before [startSession].
     *
     * Computed once when the gate opens — not accumulated and not renewed by input — so the alarm
     * gets loud at this instant no matter how steadily the person taps.
     */
    var hardRestoreAtMs: Long = 0L
        private set

    /** True once the hard deadline has fired. Input cannot duck the alarm again this gate. */
    var budgetSpent: Boolean = false
        private set

    /**
     * Starts the gate's one silence budget.
     *
     * Idempotent: a rotation that restores an existing deadline must not refill the window, so the
     * guard is on the value already being set, not on how many times this is called.
     */
    fun startSession() {
        if (hardRestoreAtMs <= 0L) hardRestoreAtMs = clock() + maxSilenceWindowMs
    }

    /**
     * Restores the gate's audio state saved with the puzzle, and reconciles the player with it.
     *
     * `elapsedRealtime` keeps counting across a process restart (it is time since boot), so an
     * absolute deadline saved in the activity's bundle is still comparable after process death.
     *
     * [silenced] travels with the deadline because the player outlives this controller on a
     * rotation: without it a recreated controller would believe the alarm is loud while the
     * player is still ducked, [checkWatchdog] would return early on `!isSilenced`, and nothing
     * would ever restore full volume.
     *
     * The emit here is deliberately **unguarded** — the one write in this class that is. Steady
     * state is already correct; this write exists to make an uncertain player correct. The
     * `isSilenced` guard belongs on [restoreVolume], which must not write when nothing is ducked.
     */
    fun restoreBudget(hardRestoreAt: Long, spent: Boolean, silenced: Boolean) {
        hardRestoreAtMs = hardRestoreAt
        budgetSpent = spent
        isSilenced = silenced
        onTargetVolume(if (silenced) silencedVolume else fullVolume, 0L)
    }

    fun onUserAction() {
        if (budgetSpent) return
        if (deadlineReached()) {
            spendBudget()
            return
        }
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
     *
     * The wait is capped by the hard deadline as well as by the idle timeout, so the watchdog
     * wakes at the deadline instead of sleeping past it.
     */
    fun checkWatchdog(isPuzzleActive: Boolean): Long? {
        if (!isPuzzleActive) {
            // Leaving the gate must be audible, not just flagged. The flag is bookkeeping; the
            // player only hears the emitted target.
            restoreVolume(restoreRampMs)
            return null
        }
        if (deadlineReached()) {
            spendBudget()
            return null
        }
        if (!isSilenced) return null
        val idleMs = (clock() - lastInputAtMs).coerceAtLeast(0L)
        if (idleMs >= inactivityTimeoutMs) {
            restoreVolume(restoreRampMs)
            return null
        }
        val idleWait = inactivityTimeoutMs - idleMs
        val deadlineWait = if (hardRestoreAtMs > 0L) {
            (hardRestoreAtMs - clock()).coerceAtLeast(0L)
        } else {
            idleWait
        }
        return minOf(idleWait, deadlineWait).coerceIn(50L, inactivityTimeoutMs)
    }

    fun resetImmediate() {
        restoreVolume(0L)
    }

    /** The alarm stops being quiet here, permanently for this gate. */
    private fun spendBudget() {
        budgetSpent = true
        restoreVolume(restoreRampMs)
    }

    private fun deadlineReached(): Boolean =
        hardRestoreAtMs > 0L && clock() >= hardRestoreAtMs

    /**
     * The one place the flag comes down. Emits first, clears second, and does nothing at all when
     * the player was never ducked — a blind emit here would write volume on every teardown.
     */
    private fun restoreVolume(durationMs: Long) {
        if (isSilenced) onTargetVolume(fullVolume, durationMs)
        isSilenced = false
    }

    companion object {
        /** A duck, not a mute: the user still hears the alarm while solving. */
        const val DUCKED_VOLUME = 0.30f

        /** Total silence time one gate may spend, however the input arrives. */
        const val MAX_SILENCE_WINDOW_MS = 60_000L
    }
}
