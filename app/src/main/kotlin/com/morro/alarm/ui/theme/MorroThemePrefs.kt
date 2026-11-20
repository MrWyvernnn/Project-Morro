package com.morro.alarm.ui.theme

import android.content.Context
import android.content.SharedPreferences

/**
 * The two looks Morro ships.
 *
 * Only the accent and the warmth of the surfaces differ — the canvas stays pure black in both, and
 * the structure (cards, hairlines, type scale) is identical. A second theme that also changed the
 * layout would be two products to maintain; this is one product with two lights on it.
 */
enum class MorroThemeChoice(val label: String) {
    /** Honey on black. The original. */
    MIDNIGHT("Midnight"),

    /** Ember orange on black, with leaves instead of clouds. */
    AUTUMN("Autumn"),
}

/**
 * Where the chosen theme is stored.
 *
 * It lives in `morro_prefs` next to everything else, so the preference survives restarts without a
 * second store, and any screen can ask for it without being handed the value.
 */
object ThemePrefs {
    /** Preference key. Public because the wrapper listens for exactly this key. */
    const val KEY = "theme_choice"

    private const val PREFS_NAME = "morro_prefs"

    fun get(context: Context): MorroThemeChoice {
        val stored = prefs(context).getString(KEY, null) ?: return MorroThemeChoice.MIDNIGHT
        // An unknown value (a theme removed in a later version) falls back instead of crashing.
        return MorroThemeChoice.values().firstOrNull { it.name == stored }
            ?: MorroThemeChoice.MIDNIGHT
    }

    fun set(context: Context, choice: MorroThemeChoice) {
        prefs(context).edit().putString(KEY, choice.name).apply()
    }

    fun isAutumn(context: Context): Boolean = get(context) == MorroThemeChoice.AUTUMN

    /**
     * Calls [onChange] whenever the stored theme changes, and returns the call that stops listening.
     *
     * The theme wrapper uses this instead of taking the choice as a parameter, because a change made
     * in Settings has to reach every screen at once — including ones the sheet knows nothing about.
     */
    fun observe(context: Context, onChange: (MorroThemeChoice) -> Unit): () -> Unit {
        val prefs = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) onChange(get(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
