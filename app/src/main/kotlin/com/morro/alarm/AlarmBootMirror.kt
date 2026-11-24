package com.morro.alarm

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * A copy of the armed alarm, kept where Android can still read it before the first unlock.
 *
 * Everything Morro normally stores — `morro_prefs`, and every file in `filesDir` — lives in
 * credential-encrypted storage. That storage is locked from the moment the phone boots until the
 * person unlocks it. A receiver that runs earlier and reads those prefs does not fail loudly; it
 * reads defaults, decides the alarm is switched off, and schedules nothing. That silent "nothing"
 * is the exact bug this file exists to prevent.
 *
 * Device-protected storage is encrypted with a device key instead of the user's credential, so it is
 * readable at `LOCKED_BOOT_COMPLETED`. Only the four values a re-arm needs are mirrored here — the
 * hours, the minute, whether the alarm is armed, and the pending trigger. No mission data, no memo
 * paths, no personal answers.
 *
 * Writes use `commit()` rather than `apply()`: the process may be killed by a reboot immediately
 * afterwards, and an asynchronous write that has not reached disk is a missing alarm.
 */
object AlarmBootMirror {

    private const val TAG = "AlarmBootMirror"
    private const val PREFS_NAME = "morro_boot"

    private const val KEY_ARMED = "armed"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
    private const val KEY_NEXT_TRIGGER = "next_trigger"

    private fun prefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Copies the current armed state out of credential storage.
     *
     * Called after every arm, re-arm, snooze and cancel, so the mirror is never older than the last
     * thing the person did. A few extra writes per day cost nothing; a stale mirror costs a morning.
     */
    fun mirrorFrom(context: Context) {
        val editor = prefs(context).edit()

        if (!AlarmScheduler.isArmed(context)) {
            if (!editor.clear().commit()) {
                Log.w(TAG, "Could not clear the boot mirror")
            }
            return
        }

        editor.putBoolean(KEY_ARMED, true)

        val saved = AlarmScheduler.getSavedAlarmTime(context)
        if (saved == null) {
            editor.remove(KEY_HOUR)
            editor.remove(KEY_MINUTE)
        } else {
            editor.putInt(KEY_HOUR, saved.first)
            editor.putInt(KEY_MINUTE, saved.second)
        }

        val nextTrigger = AlarmScheduler.getNextAlarmTime(context)
        if (nextTrigger == null) {
            editor.remove(KEY_NEXT_TRIGGER)
        } else {
            editor.putLong(KEY_NEXT_TRIGGER, nextTrigger)
        }

        if (!editor.commit()) {
            Log.w(TAG, "Could not write the boot mirror")
        }
    }

    /** Drops the mirror. Only meaningful before an arm, since a cancelled alarm has nothing to keep. */
    fun clear(context: Context) {
        if (!prefs(context).edit().clear().commit()) {
            Log.w(TAG, "Could not clear the boot mirror")
        }
    }

    fun isArmed(context: Context): Boolean = prefs(context).getBoolean(KEY_ARMED, false)

    /** The hour and minute the person chose, or null when the mirror was never written. */
    fun savedTime(context: Context): Pair<Int, Int>? {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_HOUR) || !prefs.contains(KEY_MINUTE)) return null
        val hour = prefs.getInt(KEY_HOUR, -1)
        val minute = prefs.getInt(KEY_MINUTE, -1)
        return if (hour in 0..23 && minute in 0..59) Pair(hour, minute) else null
    }

    /** The pending absolute trigger, or null when only the daily time is known. */
    fun nextTrigger(context: Context): Long? {
        val value = prefs(context).getLong(KEY_NEXT_TRIGGER, -1L)
        return if (value == -1L) null else value
    }
}
