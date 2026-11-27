package com.morro.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingRecoveryTest {

    @Test
    fun `recoveryNeeded returns true only when ringing and not already active`() {
        assertTrue(recoveryNeeded(isRinging = true, ringAlreadyActive = false))
        assertFalse(recoveryNeeded(isRinging = true, ringAlreadyActive = true))
        assertFalse(recoveryNeeded(isRinging = false, ringAlreadyActive = false))
        assertFalse(recoveryNeeded(isRinging = false, ringAlreadyActive = true))
    }
}
