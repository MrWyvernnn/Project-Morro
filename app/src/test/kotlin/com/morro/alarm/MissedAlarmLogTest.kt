package com.morro.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The inference rules behind the reliability log. These run on plain JVM timestamps: the point of
 * keeping the classification pure is that a wrong rule here would either hide a dead alarm or nag a
 * person whose alarm worked, and neither shows up in a UI test.
 */
class MissedAlarmLogTest {

    private val expected = 1_000_000L

    @Test
    fun `a ring inside the jitter window is on time`() {
        assertNull(lateFireFor(expected, expected))
        assertNull(lateFireFor(expected, expected + 59_999L))
    }

    @Test
    fun `a ring past a minute is late`() {
        assertEquals(
            AlarmEventKind.LATE_FIRE,
            lateFireFor(expected, expected + 60_001L)
        )
    }

    @Test
    fun `a ring that looks early is not late`() {
        // A manual clock change moves the stored trigger ahead of the ring; that is a time-change
        // case, not a late one, and logging it would be a false alarm.
        assertNull(lateFireFor(expected, expected - 3_600_000L))
    }

    @Test
    fun `an unknown expected time is never classified`() {
        assertNull(lateFireFor(null, expected))
    }

    @Test
    fun `an armed alarm whose time passed without a ring is missed`() {
        assertTrue(isNeverFired(armed = true, expectedAtMillis = expected, nowMillis = expected + 1L, lastFiredAtMillis = 0L))
    }

    @Test
    fun `an alarm that has not come due yet is not missed`() {
        assertFalse(isNeverFired(armed = true, expectedAtMillis = expected, nowMillis = expected - 1L, lastFiredAtMillis = 0L))
    }

    @Test
    fun `an alarm that rang is not missed even if the process died before re-arming`() {
        // The ring happened; dismissal may have been interrupted. That is not evidence of a miss.
        assertFalse(
            isNeverFired(
                armed = true,
                expectedAtMillis = expected,
                nowMillis = expected + 600_000L,
                lastFiredAtMillis = expected + 1_000L
            )
        )
    }

    @Test
    fun `a cancelled alarm is never missed`() {
        assertFalse(isNeverFired(armed = false, expectedAtMillis = expected, nowMillis = expected + 1, lastFiredAtMillis = 0L))
    }

    @Test
    fun `the buffer keeps the newest events and drops the oldest`() {
        val events = (1..12).map {
            AlarmEvent(AlarmEventKind.LATE_FIRE, it.toLong(), it.toLong(), "Xiaomi", "Redmi")
        }
        val reduced = events.fold(emptyList<AlarmEvent>()) { acc, event -> appendEvent(acc, event, capacity = 10) }

        assertEquals(10, reduced.size)
        assertEquals(12L, reduced.first().expectedAtMillis)
        assertEquals(3L, reduced.last().expectedAtMillis)
    }

    @Test
    fun `appending keeps the previous history below the new event`() {
        val older = AlarmEvent(AlarmEventKind.NEVER_FIRED, 5L, 6L, "Samsung", "SM-S911B")
        val newer = AlarmEvent(AlarmEventKind.LATE_FIRE, 9L, 10L, "Samsung", "SM-S911B")

        assertEquals(listOf(newer, older), appendEvent(listOf(older), newer))
    }
}
