package com.morro.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GateSilenceControllerTest {

    @Test
    fun `first user tap silences player to zero immediately`() {
        var now = 10_000L
        val transitions = mutableListOf<Pair<Float, Long>>()
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { vol, ms -> transitions.add(vol to ms) }
        )

        controller.onUserAction()

        assertTrue(controller.isSilenced)
        assertEquals(listOf(GateSilenceController.DUCKED_VOLUME to 200L), transitions)
    }

    @Test
    fun `repeated taps keep sound silenced and reset 15s inactivity timer`() {
        var now = 10_000L
        val transitions = mutableListOf<Pair<Float, Long>>()
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { vol, ms -> transitions.add(vol to ms) }
        )

        controller.onUserAction()
        now = 20_000L // +10s
        assertNotNull(controller.checkWatchdog(isPuzzleActive = true))
        assertTrue(controller.isSilenced)

        // Tap at +10s resets the 15s countdown without re-emitting silence ramp
        controller.onUserAction()
        assertEquals(1, transitions.size)

        now = 34_999L // 14.999s after second tap -> still silent
        val remaining = controller.checkWatchdog(isPuzzleActive = true)
        assertNotNull(remaining)
        assertTrue(controller.isSilenced)
        assertEquals(1, transitions.size)

        now = 35_000L // 15.000s after second tap -> restores full volume
        assertNull(controller.checkWatchdog(isPuzzleActive = true))
        assertFalse(controller.isSilenced)
        assertEquals(listOf(GateSilenceController.DUCKED_VOLUME to 200L, 1.0f to 600L), transitions)
    }

    @Test
    fun `tapping again after 15s wake-up re-silences player`() {
        var now = 1_000L
        val transitions = mutableListOf<Float>()
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { vol, _ -> transitions.add(vol) }
        )

        controller.onUserAction()
        now = 16_000L
        controller.checkWatchdog(isPuzzleActive = true)
        assertFalse(controller.isSilenced)

        // User resumes solving
        controller.onUserAction()
        assertTrue(controller.isSilenced)
        assertEquals(listOf(GateSilenceController.DUCKED_VOLUME, 1.0f, GateSilenceController.DUCKED_VOLUME), transitions)
    }

    @Test
    fun `leaving puzzle state restores full volume`() {
        var now = 5_000L
        val transitions = mutableListOf<Float>()
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { vol, _ -> transitions.add(vol) }
        )

        controller.onUserAction()
        now = 25_000L
        assertNull(controller.checkWatchdog(isPuzzleActive = false))
        assertFalse(controller.isSilenced)
        assertEquals(listOf(GateSilenceController.DUCKED_VOLUME, 1.0f), transitions)
    }

    // ── F1: clearing the flag must always be audible ──────────────────────────

    @Test
    fun `resetImmediate restores full volume as its last emission`() {
        var now = 1_000L
        val transitions = mutableListOf<Pair<Float, Long>>()
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { vol, ms -> transitions.add(vol to ms) }
        )

        controller.onUserAction()
        now = 1_200L
        controller.resetImmediate()

        assertFalse(controller.isSilenced)
        assertEquals(1.0f to 0L, transitions.last())
    }

    @Test
    fun `resetImmediate on a controller that was never silenced emits nothing`() {
        val transitions = mutableListOf<Pair<Float, Long>>()
        val controller = GateSilenceController(
            clock = { 1_000L },
            onTargetVolume = { vol, ms -> transitions.add(vol to ms) }
        )

        controller.resetImmediate()

        assertTrue(transitions.isEmpty())
    }

    @Test
    fun `checkWatchdog inactive on a controller that was never silenced emits nothing`() {
        val transitions = mutableListOf<Pair<Float, Long>>()
        val controller = GateSilenceController(
            clock = { 1_000L },
            onTargetVolume = { vol, ms -> transitions.add(vol to ms) }
        )

        assertNull(controller.checkWatchdog(isPuzzleActive = false))

        assertTrue(transitions.isEmpty())
    }

    @Test
    fun `leaving the puzzle restores exactly once, not once per teardown`() {
        var now = 5_000L
        val transitions = mutableListOf<Float>()
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { vol, _ -> transitions.add(vol) }
        )

        controller.onUserAction()
        controller.checkWatchdog(isPuzzleActive = false)
        controller.checkWatchdog(isPuzzleActive = false)
        controller.resetImmediate()

        assertEquals(listOf(GateSilenceController.DUCKED_VOLUME, 1.0f), transitions)
    }

    @Test
    fun `a further watchdog check after the idle restore does not re-emit`() {
        var now = 1_000L
        val transitions = mutableListOf<Float>()
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { vol, _ -> transitions.add(vol) }
        )

        controller.onUserAction()
        now = 16_000L
        assertNull(controller.checkWatchdog(isPuzzleActive = true))
        assertEquals(listOf(GateSilenceController.DUCKED_VOLUME, 1.0f), transitions)

        // The loop in AlarmActivity can tick one more time before it sees null and breaks.
        now = 16_050L
        assertNull(controller.checkWatchdog(isPuzzleActive = true))
        assertEquals(listOf(GateSilenceController.DUCKED_VOLUME, 1.0f), transitions)
    }

    @Test
    fun `clock skew never causes watchdog wait to exceed inactivity timeout`() {
        var now = 10_000L
        val controller = GateSilenceController(
            inactivityTimeoutMs = 15_000L,
            clock = { now },
            onTargetVolume = { _, _ -> }
        )

        controller.onUserAction()
        now = 9_000L // clock went backwards
        val waitMs = controller.checkWatchdog(isPuzzleActive = true)
        assertNotNull(waitMs)
        assertTrue(waitMs!! <= 15_000L)
    }

    // ── F2: a duck, and a deadline input cannot extend ────────────────────────

    @Test
    fun `the default duck leaves the alarm audible`() {
        val transitions = mutableListOf<Float>()
        val controller = GateSilenceController(
            clock = { 1_000L },
            onTargetVolume = { vol, _ -> transitions.add(vol) }
        )

        controller.onUserAction()

        assertEquals(listOf(GateSilenceController.DUCKED_VOLUME), transitions)
        assertTrue(GateSilenceController.DUCKED_VOLUME > 0f)
    }

    @Test
    fun `tapping every fourteen seconds cannot keep the alarm quiet past the deadline`() {
        var now = 0L
        val transitions = mutableListOf<Float>()
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { vol, _ -> transitions.add(vol) }
        )
        controller.startSession()

        // Five minutes of one tap every 14 s. Every tap refreshes the 15 s idle timer, so only the
        // hard deadline can bring the volume back.
        var tapAt = 0L
        while (tapAt < 300_000L) {
            now = tapAt
            controller.onUserAction()
            controller.checkWatchdog(isPuzzleActive = true)
            tapAt += 14_000L
        }

        // The watchdog's last wait is capped by the deadline, so it wakes at 60 s.
        now = 60_000L
        assertNull(controller.checkWatchdog(isPuzzleActive = true))
        assertEquals(1.0f, transitions.last())
        assertTrue(controller.budgetSpent)

        // Further input must not duck it again for the rest of the gate.
        now = 70_000L
        controller.onUserAction()
        now = 84_000L
        controller.onUserAction()
        assertEquals(1.0f, transitions.last())
        assertFalse(controller.isSilenced)
    }

    @Test
    fun `after the hard restore a tap does not re-silence`() {
        var now = 1_000L
        val transitions = mutableListOf<Float>()
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { vol, _ -> transitions.add(vol) }
        )
        controller.startSession()

        controller.onUserAction()
        now = 61_000L
        controller.checkWatchdog(isPuzzleActive = true)
        assertEquals(1.0f, transitions.last())

        now = 61_500L
        controller.onUserAction()

        assertEquals(1.0f, transitions.last())
        assertTrue(controller.budgetSpent)
    }

    @Test
    fun `a tap after the deadline spends the budget without ducking`() {
        var now = 0L
        val transitions = mutableListOf<Float>()
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { vol, _ -> transitions.add(vol) }
        )
        controller.startSession()

        now = 60_000L
        controller.onUserAction()

        assertTrue(transitions.isEmpty())
        assertTrue(controller.budgetSpent)
        assertFalse(controller.isSilenced)
    }

    @Test
    fun `the deadline is computed once and survives a restore`() {
        var now = 1_000L
        val original = GateSilenceController(
            clock = { now },
            onTargetVolume = { _, _ -> }
        )
        original.startSession()
        val deadline = original.hardRestoreAtMs
        assertEquals(1_000L + GateSilenceController.MAX_SILENCE_WINDOW_MS, deadline)

        // Simulate a rotation at 30 s: a new controller restored from the saved bundle.
        now = 30_000L
        val restored = GateSilenceController(
            clock = { now },
            onTargetVolume = { _, _ -> }
        )
        restored.restoreBudget(hardRestoreAt = deadline, spent = false)
        restored.startSession()

        assertEquals(deadline, restored.hardRestoreAtMs)
    }

    @Test
    fun `a spent budget survives a restore`() {
        var now = 1_000L
        val original = GateSilenceController(
            clock = { now },
            onTargetVolume = { _, _ -> }
        )
        original.startSession()
        now = 61_000L
        original.checkWatchdog(isPuzzleActive = true)
        assertTrue(original.budgetSpent)

        val restored = GateSilenceController(
            clock = { now },
            onTargetVolume = { _, _ -> }
        )
        restored.restoreBudget(original.hardRestoreAtMs, original.budgetSpent)
        restored.startSession()
        restored.onUserAction()

        assertFalse(restored.isSilenced)
    }

    @Test
    fun `the idle restore still works before the hard deadline`() {
        var now = 1_000L
        val transitions = mutableListOf<Float>()
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { vol, _ -> transitions.add(vol) }
        )
        controller.startSession()

        controller.onUserAction()
        now = 16_000L
        assertNull(controller.checkWatchdog(isPuzzleActive = true))

        assertEquals(1.0f, transitions.last())
        assertFalse(controller.budgetSpent)
    }

    @Test
    fun `the watchdog wait never overshoots the hard deadline`() {
        var now = 0L
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { _, _ -> }
        )
        controller.startSession()
        now = 56_000L
        controller.onUserAction()

        val wait = controller.checkWatchdog(isPuzzleActive = true)

        assertNotNull(wait)
        assertEquals(4_000L, wait)
    }

    @Test
    fun `resetImmediate clears silenced state`() {
        var now = 1_000L
        val controller = GateSilenceController(
            clock = { now },
            onTargetVolume = { _, _ -> }
        )
        controller.onUserAction()
        assertTrue(controller.isSilenced)

        controller.resetImmediate()
        assertFalse(controller.isSilenced)
    }
}
