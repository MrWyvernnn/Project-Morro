package com.morro.alarm

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wall-clock rule behind every re-arm: a saved hour/minute becomes the next instant, in the
 * device's current time zone. Getting this wrong is a missing or doubled alarm, so it is pinned
 * down with fixed instants instead of `System.currentTimeMillis()`.
 */
class AlarmSchedulerTest {

    /** 10 March 2026, 06:00 local time. */
    private fun at(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, 10, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `a time later today stays today`() {
        val next = AlarmScheduler.nextOccurrenceMillis(7, 0, nowMillis = at(6, 0))
        assertEquals(at(7, 0), next)
    }

    @Test
    fun `a time that already passed rolls to tomorrow`() {
        val next = AlarmScheduler.nextOccurrenceMillis(5, 0, nowMillis = at(6, 0))
        assertEquals(at(5, 0) + 24 * 60 * 60 * 1000L, next)
    }

    @Test
    fun `the exact current minute rolls forward instead of firing instantly`() {
        // Equal is not "later today": arming a trigger set to now would ring during the re-arm.
        val next = AlarmScheduler.nextOccurrenceMillis(6, 0, nowMillis = at(6, 0))
        assertTrue(next > at(6, 0))
    }

    @Test
    fun `seconds and milliseconds are zeroed so the ring is on the minute`() {
        val now = at(6, 0) + 42_000L + 731L
        val next = AlarmScheduler.nextOccurrenceMillis(7, 15, nowMillis = now)
        assertEquals(at(7, 15), next)
    }
}
