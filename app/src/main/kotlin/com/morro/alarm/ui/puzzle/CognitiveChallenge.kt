package com.morro.alarm.ui.puzzle

import android.content.Context
import android.content.SharedPreferences

/**
 * The cognitive challenge gates available for the alarm.
 *
 * Each challenge requires waking the mind to dismiss the alarm:
 *  - MATH: Mental arithmetic sums
 *  - CHESS: Find the checkmate
 *  - SUDOKU: Complete the grid
 */
enum class CognitiveChallenge(val label: String, val detail: String) {
    MATH("Math", "Mental arithmetic sums"),
    CHESS("Chess", "Find the checkmate"),
    SUDOKU("Sudoku", "Complete the grid")
}

/**
 * Cognitive difficulty level for puzzle generation and selection.
 *
 *  - STANDARD: Quick morning solve
 *  - HARD: Deep focus challenge
 */
enum class CognitiveDifficulty(val label: String, val detail: String) {
    STANDARD("Standard", "Quick morning solve"),
    HARD("Hard", "Deep focus challenge")
}

/**
 * Persisted preferences for cognitive puzzle challenge type and difficulty level.
 *
 * It lives in the same `morro_prefs` file as the mission and math preferences,
 * so backing up or clearing the app takes all of it together.
 *
 * A missing or unreadable value safely falls back to MATH / STANDARD: the gate must never
 * fail to produce a puzzle because a preference was corrupted or missing.
 *
 * Holds NO Context or Activity reference. Context is passed per call.
 */
object CognitivePrefs {
    private const val PREFS_NAME = "morro_prefs"
    private const val KEY_CHALLENGE = "cognitive_challenge"
    private const val KEY_DIFFICULTY = "cognitive_difficulty"

    // Testing hook allowing tests to inject mock SharedPreferences without Android Context stub crashes
    internal var prefsOpener: ((context: Context, name: String) -> SharedPreferences)? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return prefsOpener?.invoke(context, PREFS_NAME)
            ?: (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Returns the saved [CognitiveChallenge], defaulting to [CognitiveChallenge.MATH]. */
    fun getChallenge(context: Context): CognitiveChallenge {
        return try {
            val prefs = getPrefs(context)
            val name = prefs.getString(KEY_CHALLENGE, null) ?: return CognitiveChallenge.MATH
            CognitiveChallenge.valueOf(name)
        } catch (_: Throwable) {
            CognitiveChallenge.MATH
        }
    }

    /** Persists the chosen [CognitiveChallenge]. */
    fun setChallenge(context: Context, challenge: CognitiveChallenge) {
        try {
            val prefs = getPrefs(context)
            prefs.edit().putString(KEY_CHALLENGE, challenge.name).apply()
        } catch (_: Throwable) {
            // Safe no-op on storage failure
        }
    }

    /** Returns the saved [CognitiveDifficulty], defaulting to [CognitiveDifficulty.STANDARD]. */
    fun getDifficulty(context: Context): CognitiveDifficulty {
        return try {
            val prefs = getPrefs(context)
            val name = prefs.getString(KEY_DIFFICULTY, null) ?: return CognitiveDifficulty.STANDARD
            CognitiveDifficulty.valueOf(name)
        } catch (_: Throwable) {
            CognitiveDifficulty.STANDARD
        }
    }

    /** Persists the chosen [CognitiveDifficulty]. */
    fun setDifficulty(context: Context, difficulty: CognitiveDifficulty) {
        try {
            val prefs = getPrefs(context)
            prefs.edit().putString(KEY_DIFFICULTY, difficulty.name).apply()
        } catch (_: Throwable) {
            // Safe no-op on storage failure
        }
    }
}
