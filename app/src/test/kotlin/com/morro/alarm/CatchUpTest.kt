package com.morro.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The locked-boot catch-up decision, and the guard that keeps it from ringing twice.
 *
 * Both are pure on purpose. This branch only runs after a reboot the person did not unlock in time,
 * so it is the least-exercised code in the app and the most expensive to get wrong: a false
 * negative is a silent missed morning, a false positive is an alarm at the wrong time.
 */
class CatchUpTest {

    /** 07:00, as an arbitrary but fixed instant. */
    private val due = 1_700_000_000_000L
    private val minute = 60_000L

    /** Nothing has ever rung. */
    private val neverHandled = Long.MIN_VALUE

    // ── catchUpNeeded ────────────────────────────────────────────────────────

    @Test
    fun `a trigger that is still in the future needs no catch-up`() {
        assertFalse(
            catchUpNeeded(
                armed = true,
                expectedAtMillis = due,
                nowMillis = due - minute,
                lastFiredAtMillis = 0L,
                handledOccurrenceMillis = neverHandled
            )
        )
    }

    @Test
    fun `a due trigger that never rang needs a catch-up`() {
        assertTrue(
            catchUpNeeded(
                armed = true,
                expectedAtMillis = due,
                nowMillis = due + 2 * 60 * minute,
                lastFiredAtMillis = 0L,
                handledOccurrenceMillis = neverHandled
            )
        )
    }

    @Test
    fun `a due trigger that already rang needs no catch-up`() {
        // The ring happened and the process died before re-arming, which also leaves a past trigger.
        // That morning is finished, not missed, and ringing it again would be a second alarm.
        assertFalse(
            catchUpNeeded(
                armed = true,
                expectedAtMillis = due,
                nowMillis = due + 2 * 60 * minute,
                lastFiredAtMillis = due + minute,
                handledOccurrenceMillis = due
            )
        )
    }

    @Test
    fun `the occurrence identity alone is enough to show it rang`() {
        // A DST fall-back can land the ring an hour *before* the stored expected instant, so the
        // wall clock says "nothing rang yet" while the identity says "this one already did".
        assertFalse(
            catchUpNeeded(
                armed = true,
                expectedAtMillis = due,
                nowMillis = due + 3 * 60 * minute,
                lastFiredAtMillis = due - 60 * minute,
                handledOccurrenceMillis = due
            )
        )
    }

    @Test
    fun `the wall clock alone is enough for an install upgraded without an identity`() {
        // Installs from before `alarm_handled_occurrence` existed have the timestamp and nothing
        // else. Reading only the identity would ring every one of those mornings a second time.
        assertFalse(
            catchUpNeeded(
                armed = true,
                expectedAtMillis = due,
                nowMillis = due + 3 * 60 * minute,
                lastFiredAtMillis = due + minute,
                handledOccurrenceMillis = neverHandled
            )
        )
    }

    @Test
    fun `a ring for an earlier occurrence does not satisfy this one`() {
        // Yesterday's ring must not make today's occurrence look handled.
        assertTrue(
            catchUpNeeded(
                armed = true,
                expectedAtMillis = due,
                nowMillis = due + minute,
                lastFiredAtMillis = due - 24 * 60 * minute,
                handledOccurrenceMillis = due - 24 * 60 * minute
            )
        )
    }

    @Test
    fun `a cancelled alarm is never caught up`() {
        assertFalse(
            catchUpNeeded(
                armed = false,
                expectedAtMillis = due,
                nowMillis = due + minute,
                lastFiredAtMillis = 0L,
                handledOccurrenceMillis = neverHandled
            )
        )
    }

    @Test
    fun `an alarm with no expected instant is never caught up`() {
        assertFalse(
            catchUpNeeded(
                armed = true,
                expectedAtMillis = null,
                nowMillis = due + minute,
                lastFiredAtMillis = 0L,
                handledOccurrenceMillis = neverHandled
            )
        )
    }

    @Test
    fun `the exact due instant is not yet late`() {
        assertFalse(
            catchUpNeeded(
                armed = true,
                expectedAtMillis = due,
                nowMillis = due,
                lastFiredAtMillis = 0L,
                handledOccurrenceMillis = neverHandled
            )
        )
    }

    // ── isDuplicateRing ──────────────────────────────────────────────────────

    @Test
    fun `a first delivery is never a duplicate`() {
        assertFalse(isDuplicateRing(expectedAtMillis = due, handledOccurrenceMillis = Long.MIN_VALUE))
    }

    @Test
    fun `a second delivery for the same occurrence is a duplicate`() {
        assertTrue(isDuplicateRing(expectedAtMillis = due, handledOccurrenceMillis = due))
    }

    @Test
    fun `a delivery for a different occurrence is not a duplicate`() {
        assertFalse(
            isDuplicateRing(
                expectedAtMillis = due + 24 * 60 * minute,
                handledOccurrenceMillis = due
            )
        )
    }

    @Test
    fun `a delivery with nothing armed is not a duplicate`() {
        assertFalse(isDuplicateRing(expectedAtMillis = null, handledOccurrenceMillis = due))
    }
}
