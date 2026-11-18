package com.morro.alarm.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The first-run order is a product rule, not an implementation detail: the plans may never be shown
 * before the personalizing beat, and a returning user must never see the intro again.
 */
class IntroSequenceTest {

    /** Every step defaults to "done", so a test only names the ones it cares about. */
    private fun step(
        splash: Boolean = true,
        signIn: Boolean = true,
        questions: Boolean = true,
        personalize: Boolean = true,
        paywall: Boolean = true,
        permissions: Boolean = true,
    ): IntroStep = nextIntroStep(
        splashDone = splash,
        signInDone = signIn,
        questionsDone = questions,
        personalizeDone = personalize,
        paywallDone = paywall,
        permissionsDone = permissions,
    )

    @Test
    fun `a returning user goes straight to the dashboard`() {
        assertEquals(IntroStep.Dashboard, step())
    }

    @Test
    fun `a fresh install starts at the splash`() {
        assertEquals(IntroStep.Splash, step(splash = false, signIn = false))
    }

    @Test
    fun `the whole sequence runs in order`() {
        var splash = false
        var signIn = false
        var questions = false
        var personalize = false
        var paywall = false
        var permissions = false
        val seen = mutableListOf<IntroStep>()

        // Stand in for the UI: render the current step, then mark it done, as MainActivity does.
        while (true) {
            val current = nextIntroStep(splash, signIn, questions, personalize, paywall, permissions)
            seen += current
            when (current) {
                IntroStep.Splash -> splash = true
                IntroStep.SignIn -> signIn = true
                IntroStep.Questions -> questions = true
                IntroStep.Personalizing -> personalize = true
                IntroStep.Paywall -> paywall = true
                IntroStep.Permissions -> permissions = true
                IntroStep.Dashboard -> break
            }
        }

        assertEquals(
            listOf(
                IntroStep.Splash,
                IntroStep.SignIn,
                IntroStep.Questions,
                IntroStep.Personalizing,
                IntroStep.Paywall,
                IntroStep.Permissions,
                IntroStep.Dashboard
            ),
            seen
        )
    }

    @Test
    fun `finishing the questions personalizes before plans are offered`() {
        assertEquals(IntroStep.Personalizing, step(personalize = false, paywall = false))
    }

    @Test
    fun `plans never appear before personalizing, even if the flag is set`() {
        // A stale or out-of-order paywall flag must not jump the queue.
        assertEquals(IntroStep.Personalizing, step(personalize = false, paywall = true))
    }

    @Test
    fun `permissions are requested after the plans`() {
        assertEquals(IntroStep.Permissions, step(permissions = false))
    }

    @Test
    fun `an unfinished permission step keeps the dashboard closed`() {
        assertEquals(IntroStep.Permissions, step(permissions = false, paywall = true))
    }
}
