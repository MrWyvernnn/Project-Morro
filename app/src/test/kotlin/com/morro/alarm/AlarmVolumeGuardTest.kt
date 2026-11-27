package com.morro.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmVolumeGuardTest {

    @Test
    fun `isTooQuiet flags low alarm ratios and ignores invalid max`() {
        assertTrue(isTooQuiet(volume = 2, max = 16))
        assertTrue(isTooQuiet(volume = 0, max = 16))
        assertTrue(isTooQuiet(volume = 6, max = 16))
        assertFalse(isTooQuiet(volume = 7, max = 16))
        assertFalse(isTooQuiet(volume = 16, max = 16))
        assertFalse(isTooQuiet(volume = 0, max = 0))
    }

    @Test
    fun `floorRestoreTarget restores drops and ignores equal or higher levels`() {
        assertEquals(6, floorRestoreTarget(current = 2, floor = 6, max = 16))
        assertEquals(6, floorRestoreTarget(current = 0, floor = 6, max = 16))
        assertNull(floorRestoreTarget(current = 6, floor = 6, max = 16))
        assertNull(floorRestoreTarget(current = 12, floor = 6, max = 16))
    }

    @Test
    fun `floorRestoreTarget clamps floor to max and handles zero max safely`() {
        assertEquals(16, floorRestoreTarget(current = 10, floor = 20, max = 16))
        assertNull(floorRestoreTarget(current = 16, floor = 20, max = 16))
        assertNull(floorRestoreTarget(current = 0, floor = 5, max = 0))
        assertNull(floorRestoreTarget(current = 0, floor = 5, max = -1))
    }
}
