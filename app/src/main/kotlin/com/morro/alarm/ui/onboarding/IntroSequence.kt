package com.morro.alarm.ui.onboarding

/**
 * The first-run sequence as a value instead of a chain of `!flag` tests inside a `when`.
 *
 * Before this, the order of splash, questions, personalizing, plans and permissions lived entirely
 * in the order of branches in `MainActivity`. Reordering them was a silent change, and nothing
 * could assert that the plans never appear before the personalizing beat. A pure function makes the
 * order data: it can be read in one place and tested without a device.
 *
 * There is deliberately no sign-in step. Morro has no accounts: the Play purchase is tied to the
 * Google account already on the device, so asking someone to create one would add a credential to
 * store and an account-deletion obligation under Play policy, and buy nothing in return. A
 * `SignInScreen` still exists in this package — it is not routed, and it should stay that way.
 */
internal enum class IntroStep {
    Splash,
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
 * contract: Splash, then Questions, then Personalizing, then Paywall, then Permissions, then the
 * dashboard. A returning user has every flag true and lands on Dashboard immediately.
 */
internal fun nextIntroStep(
    splashDone: Boolean,
    questionsDone: Boolean,
    personalizeDone: Boolean,
    paywallDone: Boolean,
    permissionsDone: Boolean,
): IntroStep = when {
    !splashDone -> IntroStep.Splash
    !questionsDone -> IntroStep.Questions
    !personalizeDone -> IntroStep.Personalizing
    !paywallDone -> IntroStep.Paywall
    !permissionsDone -> IntroStep.Permissions
    else -> IntroStep.Dashboard
}
