package com.morro.alarm.ui.onboarding

import android.content.Context

/**
 * The single gate that decides whether the intro (splash, questions, slideshow) still has to run.
 *
 * One flag, written once when the slideshow is finished or skipped, so a returning user goes straight
 * to the app. The key keeps its own name, separate from the permissions onboarding's
 * `onboarding_completed`, because the two steps finish at different moments.
 */
object OnboardingGate {
    const val PREFS_NAME = "morro_prefs"
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"

    fun isComplete(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_COMPLETE, false)

    /** Committed, not applied: the next launch must already see this as done. */
    fun markComplete(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .commit()
    }
}
