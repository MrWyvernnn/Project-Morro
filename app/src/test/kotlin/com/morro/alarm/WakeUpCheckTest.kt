package com.morro.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-per-morning cap is the only thing standing between "asks once" and "keeps ringing", so
 * its arithmetic is pinned down here instead of being read off a timestamp by eye.
 *
 * The cap is a 20-hour window rather than a calendar day: the next alarm is a full day out, so any
 * later check belongs to a new morning, and no date handling is needed to get that right.
 *
 * The second half covers [restorePlan], which decides what a reboot in the middle of the 90-second
 * window has to do. That path runs once in a blue moon and its failure is invisible until the morning
 * it matters, so the four cases are pinned individually.
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

    // ── restorePlan ──────────────────────────────────────────────────────────

    /** A check armed 3 minutes ago: the question is due now, the escalation 90 seconds out. */
    private val armedAt = now - 3 * minute
    private val checkAt = armedAt + WakeUpCheck.CHECK_DELAY_MILLIS
    private val alertAt = checkAt + WakeUpCheck.ALERT_DELAY_MILLIS

    @Test
    fun `a window that is already over is expired`() {
        assertEquals(
            RestorePlan.EXPIRED,
            restorePlan(questionShownAt = 0L, checkAt = checkAt, alertAt = alertAt, now = alertAt)
        )
        assertEquals(
            RestorePlan.EXPIRED,
            restorePlan(questionShownAt = 0L, checkAt = checkAt, alertAt = alertAt, now = alertAt + minute)
        )
    }

    @Test
    fun `a claim with no recorded deadline is expired`() {
        assertEquals(
            RestorePlan.EXPIRED,
            restorePlan(questionShownAt = 0L, checkAt = 0L, alertAt = 0L, now = now)
        )
    }

    @Test
    fun `a reboot before the question is due leaves it where it was`() {
        // Rebooted one minute after dismissal, two minutes before the question was due. Asking now
        // would move it earlier and hand out a window the person never asked for.
        val justAfterDismissal = armedAt + minute
        assertEquals(
            RestorePlan.ASK_LATER,
            restorePlan(
                questionShownAt = 0L,
                checkAt = checkAt,
                alertAt = alertAt,
                now = justAfterDismissal
            )
        )
    }

    @Test
    fun `a question that was already asked keeps its original deadline`() {
        val shownAt = checkAt
        assertEquals(
            RestorePlan.RESCHEDULE_ORIGINAL,
            restorePlan(
                questionShownAt = shownAt,
                checkAt = checkAt,
                alertAt = alertAt,
                now = alertAt - 20_000L
            )
        )
    }

    @Test
    fun `a shown question wins even when its due time looks like it is still ahead`() {
        // Clock moved backwards between asking and rebooting. The question was asked; the fact that
        // the wall clock now says otherwise must not make the app ask it a second time.
        assertEquals(
            RestorePlan.RESCHEDULE_ORIGINAL,
            restorePlan(
                questionShownAt = checkAt,
                checkAt = checkAt + hour,
                alertAt = alertAt + hour,
                now = now
            )
        )
    }

    @Test
    fun `a question that came due unseen gets a whole window`() {
        // The reboot swallowed the moment the question was due, so the person was never asked. The
        // window starts now instead of being handed back part-spent.
        assertEquals(
            RestorePlan.REARM_FULL_WINDOW,
            restorePlan(questionShownAt = 0L, checkAt = checkAt, alertAt = alertAt, now = now)
        )
    }

    @Test
    fun `the due instant itself counts as unseen`() {
        assertEquals(
            RestorePlan.REARM_FULL_WINDOW,
            restorePlan(questionShownAt = 0L, checkAt = checkAt, alertAt = alertAt, now = checkAt)
        )
    }

    @Test
    fun `a missing start time is treated as unseen`() {
        assertEquals(
            RestorePlan.REARM_FULL_WINDOW,
            restorePlan(questionShownAt = 0L, checkAt = 0L, alertAt = alertAt, now = now)
        )
    }
}
