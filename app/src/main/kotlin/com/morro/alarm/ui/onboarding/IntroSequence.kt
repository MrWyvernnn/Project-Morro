package com.morro.alarm.ui.onboarding

/**
 * The first-run sequence as a value instead of a chain of `!flag` tests inside a `when`.
 *
 * Before this, the order of splash, sign-in, questions, personalizing, plans and permissions lived
 * entirely in the order of branches in `MainActivity`. Reordering them was a silent change, and
 * nothing could assert that the plans never appear before the personalizing beat. A pure function
 * makes the order data: it can be read in one place and tested without a device.
 */
internal enum class IntroStep {
    Splash,
    SignIn,
    Questions,
    Personalizing,
    Paywall,
    Permissions,
    Dashboard,
}

/**
 * The next screen to show, given what the person has already finished.
 *
 * Each argument is "this step is done", so the first false flag wins. The order of the `when` is the
 * contract: Splash, then SignIn, then Questions, then Personalizing, then Paywall, then Permissions,
 * then the dashboard. A returning user has every flag true and lands on Dashboard immediately.
 */
internal fun nextIntroStep(
    splashDone: Boolean,
    signInDone: Boolean,
    questionsDone: Boolean,
    personalizeDone: Boolean,
    paywallDone: Boolean,
    permissionsDone: Boolean,
): IntroStep = when {
    !splashDone -> IntroStep.Splash
    !signInDone -> IntroStep.SignIn
    !questionsDone -> IntroStep.Questions
    !personalizeDone -> IntroStep.Personalizing
    !paywallDone -> IntroStep.Paywall
    !permissionsDone -> IntroStep.Permissions
    else -> IntroStep.Dashboard
}
