package com.morro.alarm.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quiz is a product decision, not an implementation detail: five questions, and the last two
 * are always the written ones. The list is easy to edit and the rail is generated from it, so these
 * tests hold the shape still instead of trusting a hand-counted constant.
 */
class OnboardingQuestionsTest {

    @Test
    fun `the quiz asks five questions`() {
        assertEquals(5, onboardingQuestions.size)
        assertEquals(onboardingQuestions.size, TotalQuestions)
    }

    @Test
    fun `the last two questions are the written promises`() {
        val tail = onboardingQuestions.takeLast(2)
        assertEquals(
            listOf(PersonalizationStorage.KEY_EXTRA, PersonalizationStorage.KEY_TARGET),
            tail.map { it.key }
        )
        assertTrue("the last two questions must take typed input", tail.all { it.options.isEmpty() })
    }

    @Test
    fun `the earlier questions all offer choices`() {
        val choices = onboardingQuestions.dropLast(2)
        assertTrue(choices.all { it.options.size >= 2 })
        assertTrue(choices.all { it.fallback in it.options })
    }

    @Test
    fun `every question has a unique storage key`() {
        assertEquals(
            onboardingQuestions.size,
            onboardingQuestions.map { it.key }.distinct().size
        )
    }
}
