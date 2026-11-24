package com.morro.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-per-morning cap is the only thing standing between "asks once" and "keeps ringing", so
 * its arithmetic is pinned down here instead of being read off a timestamp by eye.
 *
 * The cap is a 20-hour window rather than a calendar day: the next alarm is a full day out, so any
 * later check belongs to a new morning, and no date handling is needed to get that right.
 */
class WakeUpCheckTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val window = WakeUpCheck.ONE_PER_MORNING_WINDOW_MILLIS

    private val now = 1_800_000_000_000L

    @Test
    fun `a phone that has never run a check is free`() {
        assertTrue(WakeUpCheck.morningIsFree(lastArmedAt = 0L, now = now))
    }

    @Test
    fun `a check armed minutes ago blocks a second one`() {
        assertFalse(WakeUpCheck.morningIsFree(lastArmedAt = now - 5 * minute, now = now))
    }

    @Test
    fun `a check answered hours ago still counts as this morning`() {
        assertFalse(WakeUpCheck.morningIsFree(lastArmedAt = now - 19 * hour, now = now))
    }

    @Test
    fun `the window closes exactly at twenty hours`() {
        assertFalse(WakeUpCheck.morningIsFree(lastArmedAt = now - (window - 1), now = now))
        assertTrue(WakeUpCheck.morningIsFree(lastArmedAt = now - window, now = now))
    }

    @Test
    fun `a claim from the future is not trusted`() {
        // The clock was moved backwards. Treating that stale claim as a live cap would silence the
        // check until real time caught up, so it is dropped instead.
        assertTrue(WakeUpCheck.morningIsFree(lastArmedAt = now + hour, now = now))
    }
}
