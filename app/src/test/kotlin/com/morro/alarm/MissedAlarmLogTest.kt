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
    fun `exactly one minute is still on time, and one millisecond past it is late`() {
        // The threshold is a strict "greater than", so this is the boundary that decides whether a
        // person is told their alarm was late. It is pinned exactly rather than approximately.
        assertNull(lateFireFor(expected, expected + 60_000L))
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
    fun `an alarm with no expected time is never missed`() {
        // Nothing was ever armed at an instant, so there is no occurrence to have missed. This is
        // the shape after a cancel and before the next arm.
        assertFalse(
            isNeverFired(
                armed = true,
                expectedAtMillis = null,
                nowMillis = expected + 1L,
                lastFiredAtMillis = 0L
            )
        )
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

    @Test
    fun `a capacity of zero keeps nothing`() {
        val event = AlarmEvent(AlarmEventKind.LATE_FIRE, 1L, 2L, "vivo", "V21")
        assertTrue(appendEvent(listOf(event), event, capacity = 0).isEmpty())
    }

    @Test
    fun `a capacity of one keeps only the newest event`() {
        val older = AlarmEvent(AlarmEventKind.NEVER_FIRED, 1L, 2L, "vivo", "V21")
        val newer = AlarmEvent(AlarmEventKind.LATE_FIRE, 3L, 4L, "vivo", "V21")

        assertEquals(listOf(newer), appendEvent(listOf(older), newer, capacity = 1))
    }

    // ── preferredEventFor ────────────────────────────────────────────────────

    @Test
    fun `nothing is shown when nothing was logged`() {
        assertNull(preferredEventFor(emptyList(), expected))
    }

    @Test
    fun `nothing is shown when no alarm is expected`() {
        val event = AlarmEvent(AlarmEventKind.NEVER_FIRED, expected, expected + 1, "Xiaomi", "Redmi")
        assertNull(preferredEventFor(listOf(event), expectedAtMillis = null))
    }

    @Test
    fun `a miss is shown when that is all there is`() {
        val miss = AlarmEvent(AlarmEventKind.NEVER_FIRED, expected, expected + 1, "Xiaomi", "Redmi")
        assertEquals(miss, preferredEventFor(listOf(miss), expected))
    }

    @Test
    fun `a late ring supersedes the miss logged for the same occurrence`() {
        // The catch-up path writes NEVER_FIRED before it rings, so both records can exist for one
        // morning. Telling the person it never rang while it is ringing would be a lie.
        val miss = AlarmEvent(AlarmEventKind.NEVER_FIRED, expected, expected + 1, "Xiaomi", "Redmi")
        val late = AlarmEvent(AlarmEventKind.LATE_FIRE, expected, expected + 3_600_000, "Xiaomi", "Redmi")

        assertEquals(late, preferredEventFor(listOf(late, miss), expected))
        assertEquals(late, preferredEventFor(listOf(miss, late), expected))
    }

    @Test
    fun `an event for another occurrence is never shown`() {
        val yesterday = AlarmEvent(
            AlarmEventKind.NEVER_FIRED,
            expected - 24 * 60 * 60 * 1000L,
            expected,
            "Xiaomi",
            "Redmi"
        )
        assertNull(preferredEventFor(listOf(yesterday), expected))
    }

    @Test
    fun `the newest record of a kind wins`() {
        val olderLate = AlarmEvent(AlarmEventKind.LATE_FIRE, expected, expected + 60_001, "Xiaomi", "Redmi")
        val newerLate = AlarmEvent(AlarmEventKind.LATE_FIRE, expected, expected + 120_000, "Xiaomi", "Redmi")

        assertEquals(newerLate, preferredEventFor(listOf(newerLate, olderLate), expected))
    }

    // ── the stored ring buffer ───────────────────────────────────────────────

    @Test
    fun `the first record lands in slot zero`() {
        assertEquals(0, nextHead(head = 0, count = 0, capacity = 10))
        assertEquals(listOf(0), slotOrder(head = 0, count = 1, capacity = 10))
    }

    @Test
    fun `the head steps backwards and wraps`() {
        assertEquals(9, nextHead(head = 0, count = 1, capacity = 10))
        assertEquals(0, nextHead(head = 1, count = 10, capacity = 10))
        assertEquals(4, nextHead(head = 0, count = 1, capacity = 5))
    }

    @Test
    fun `reading walks forward from the head`() {
        assertEquals(listOf(9, 0, 1), slotOrder(head = 9, count = 3, capacity = 10))
        assertEquals(listOf(8, 9, 0, 1), slotOrder(head = 8, count = 4, capacity = 10))
    }

    @Test
    fun `an empty buffer has no slots`() {
        assertTrue(slotOrder(head = 3, count = 0, capacity = 10).isEmpty())
    }

    @Test
    fun `the head pointer buffer agrees with the reference ordering for every write`() {
        // 25 writes through a 10-slot head-pointer buffer, checked against the list version that
        // used to be the implementation. This is the test that would catch the two drifting apart.
        val capacity = 10
        var head = 0
        var count = 0
        val slots = mutableMapOf<Int, AlarmEvent>()
        var reference = emptyList<AlarmEvent>()

        repeat(25) { index ->
            val event = AlarmEvent(
                AlarmEventKind.LATE_FIRE,
                expectedAtMillis = index.toLong(),
                observedAtMillis = index.toLong(),
                manufacturer = "Xiaomi",
                model = "Redmi"
            )

            head = nextHead(head, count, capacity)
            slots[head] = event
            count = minOf(count + 1, capacity)
            reference = appendEvent(reference, event, capacity)

            val stored = slotOrder(head, count, capacity).map { slots.getValue(it) }
            assertEquals("write $index", reference, stored)
        }
    }
}
