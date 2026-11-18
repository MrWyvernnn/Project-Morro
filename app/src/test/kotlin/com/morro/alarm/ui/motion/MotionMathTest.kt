package com.morro.alarm.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMathTest {

    @Test
    fun `exit is twenty percent shorter than entrance`() {
        assertEquals(176, MorroMotion.exitMillis(220))
        assertEquals(144, MorroMotion.exitMillis(180))
    }

    @Test
    fun `stagger advances thirty milliseconds per row`() {
        assertEquals(0L, MorroMotion.staggerDelayMs(0))
        assertEquals(30L, MorroMotion.staggerDelayMs(1))
        assertEquals(150L, MorroMotion.staggerDelayMs(5))
    }

    @Test
    fun `stagger is capped at six steps`() {
        assertEquals(150L, MorroMotion.staggerDelayMs(6))
        assertEquals(150L, MorroMotion.staggerDelayMs(40))
    }

    @Test
    fun `rubber band yields progressively and never exceeds the overshoot`() {
        val small = MorroMotion.rubberBand(overshootPx = 40f, dimensionPx = 400f)
        val large = MorroMotion.rubberBand(overshootPx = 400f, dimensionPx = 400f)

        assertTrue(small > 0f)
        assertTrue(large < 400f)
        assertTrue(large / 400f < small / 40f)
    }

    @Test
    fun `rubber band is safe for a zero sized dimension`() {
        assertEquals(0f, MorroMotion.rubberBand(overshootPx = 20f, dimensionPx = 0f), 0.0001f)
    }
}
