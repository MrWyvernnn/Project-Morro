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
        assertEquals(listOf(0.0f to 200L), transitions)
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
        assertEquals(listOf(0.0f to 200L, 1.0f to 600L), transitions)
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
        assertEquals(listOf(0.0f, 1.0f, 0.0f), transitions)
    }

    @Test
    fun `leaving puzzle state stops watchdog without restoring`() {
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
        assertEquals(listOf(0.0f), transitions)
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
