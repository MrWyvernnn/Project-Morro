package com.morro.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grace window is a deadline, not a tick count.
 *
 * These tests exist because the old loop decremented a counter once per second: a late tick made
 * the alarm stay ducked longer than the banner claimed. Every case here is a clock, no coroutines.
 */
class GraceWindowTest {

    private val deadline = 30_000L

    @Test
    fun `the banner starts at the full window`() {
        assertEquals(30, GraceWindow.secondsRemaining(deadline, nowMs = 0L))
        assertEquals(30, GraceWindow.DURATION_SECONDS)
    }

    @Test
    fun `rounds up so the last ducked second still shows one`() {
        assertEquals(1, GraceWindow.secondsRemaining(deadline, nowMs = 29_001L))
        assertEquals(1, GraceWindow.secondsRemaining(deadline, nowMs = 29_999L))
        assertEquals(0, GraceWindow.secondsRemaining(deadline, nowMs = 30_000L))
    }

    @Test
    fun `never reports a negative second after the deadline`() {
        assertEquals(0, GraceWindow.secondsRemaining(deadline, nowMs = 90_000L))
    }

    @Test
    fun `a clock that moves backwards cannot report more than the window`() {
        assertEquals(30, GraceWindow.secondsRemaining(deadline, nowMs = -120_000L))
    }

    @Test
    fun `tick delay is capped at one second for a ticking banner`() {
        assertEquals(1_000L, GraceWindow.nextTickDelayMs(deadline, nowMs = 0L))
        assertEquals(1_000L, GraceWindow.nextTickDelayMs(deadline, nowMs = 15_000L))
    }

    @Test
    fun `tick delay never overshoots the deadline`() {
        assertEquals(400L, GraceWindow.nextTickDelayMs(deadline, nowMs = 29_600L))
        assertEquals(0L, GraceWindow.nextTickDelayMs(deadline, nowMs = 30_000L))
        assertEquals(0L, GraceWindow.nextTickDelayMs(deadline, nowMs = 31_000L))
    }

    @Test
    fun `the grace duck uses the same level as the cognitive gate`() {
        // Two duck authorities write one player. They are mutually exclusive today (the grace
        // window only exists on QR/photo gates, the controller only on the puzzle gate), but the
        // user should not be able to tell which one ducked them.
        assertEquals(GateSilenceController.DUCKED_VOLUME, GraceWindow.VOLUME)
        assertEquals(30, GraceWindow.volumePercent)
    }

    @Test
    fun `the grace duck is audible`() {
        assertTrue(GraceWindow.VOLUME > 0f)
        assertTrue(GraceWindow.VOLUME < 1f)
    }
}
