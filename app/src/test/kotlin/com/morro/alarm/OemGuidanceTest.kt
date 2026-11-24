package com.morro.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manufacturer mapping decides which instructions a person is shown, so it is worth holding
 * still: a Pixel user told to look for "Autostart" would waste a morning, and a Redmi user pointed
 * at stock Android settings would never find the switch that matters.
 */
class OemGuidanceTest {

    @Test
    fun `xiaomi sub-brands all get the xiaomi instructions`() {
        assertEquals(OemFamily.XIAOMI, guidanceFor("Xiaomi").family)
        assertEquals(OemFamily.XIAOMI, guidanceFor("Xiaomi", "Redmi").family)
        assertEquals(OemFamily.XIAOMI, guidanceFor("Xiaomi", "POCO").family)
    }

    @Test
    fun `oppo family is detected across its three brands`() {
        assertEquals(OemFamily.OPPO, guidanceFor("OPPO").family)
        assertEquals(OemFamily.OPPO, guidanceFor("realme").family)
        assertEquals(OemFamily.OPPO, guidanceFor("OnePlus").family)
    }

    @Test
    fun `oneplus is labelled as oneplus rather than the oppo parent`() {
        assertEquals("OnePlus", guidanceFor("OnePlus").brandLabel)
        assertEquals("OPPO / Realme", guidanceFor("realme").brandLabel)
    }

    @Test
    fun `vivo and iqoo share the background startup instructions`() {
        assertEquals(OemFamily.VIVO, guidanceFor("vivo").family)
        assertEquals(OemFamily.VIVO, guidanceFor("vivo", "iQOO").family)
    }

    @Test
    fun `honor is labelled separately from huawei`() {
        assertEquals(OemFamily.HUAWEI, guidanceFor("HUAWEI").family)
        assertEquals("Honor", guidanceFor("HONOR").brandLabel)
        assertEquals("Huawei", guidanceFor("HUAWEI").brandLabel)
    }

    @Test
    fun `samsung guidance points at sleeping apps`() {
        val guidance = guidanceFor("samsung")
        assertEquals(OemFamily.SAMSUNG, guidance.family)
        assertTrue(guidance.steps.any { it.contains("Sleeping apps", ignoreCase = true) })
    }

    @Test
    fun `a stock android phone gets generic battery guidance`() {
        val guidance = guidanceFor("Google", "Pixel")
        assertEquals(OemFamily.GENERIC, guidance.family)
        assertFalse(OemReliability.needsGuidance(guidance))
    }

    @Test
    fun `manufacturers with a real blocker are worth an onboarding card`() {
        listOf("Xiaomi", "realme", "vivo", "HUAWEI", "samsung").forEach {
            assertTrue(it, OemReliability.needsGuidance(guidanceFor(it)))
        }
    }
}
