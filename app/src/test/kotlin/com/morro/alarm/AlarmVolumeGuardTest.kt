package com.morro.alarm

import org.junit.Assert.assertFalse
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
}
