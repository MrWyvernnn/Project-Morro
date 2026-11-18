package com.morro.alarm.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plans are sample copy, but the shape of the offer is still a rule: three plans, one free plan
 * listed first, and at most one highlighted, so the screen never argues with itself.
 */
class PaywallPlansTest {

    @Test
    fun `three plans are offered`() {
        assertEquals(3, PaywallPlans.size)
    }

    @Test
    fun `the free plan is listed first and chosen by default`() {
        val first = PaywallPlans.first()
        assertEquals("Free", first.name)
        assertEquals("$0", first.price)
    }

    @Test
    fun `exactly one plan is highlighted`() {
        assertEquals(1, PaywallPlans.count { it.popular })
    }

    @Test
    fun `every plan states a name, a price and one line of difference`() {
        PaywallPlans.forEach { plan ->
            assertTrue(plan.name.isNotBlank())
            assertTrue(plan.price.isNotBlank())
            assertTrue(plan.period.isNotBlank())
            assertTrue(plan.blurb.isNotBlank())
        }
    }

    @Test
    fun `no two plans share a name`() {
        assertEquals(
            PaywallPlans.size,
            PaywallPlans.map { it.name }.toSet().size
        )
    }
}
