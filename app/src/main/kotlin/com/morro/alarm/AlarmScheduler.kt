package com.morro.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * AlarmScheduler: Handles scheduling alarm-clock-style alarms.
 * Uses AlarmManager.setAlarmClock() so the system shows the alarm icon
 * in the status bar and bypasses aggressive battery optimization (e.g. Nothing OS).
 * Stores next_alarm_time + formatted string in SharedPreferences (morro_prefs).
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val PREFS_NAME = "morro_prefs"
    private const val KEY_NEXT_ALARM_TIME = "next_alarm_time"
    private const val KEY_NEXT_ALARM_FORMATTED = "next_alarm_formatted"
    private const val KEY_ALARM_ARMED = "alarm_armed"
    const val KEY_ALARM_HOUR = "alarm_hour"
    const val KEY_ALARM_MINUTE = "alarm_minute"
    private const val ALARM_REQUEST_CODE = 1001

    /**
     * Schedules the daily wake-up alarm for the given hour and minute.
     * Calculates the next occurrence (today if time is in future, else tomorrow).
     * Saves the hour and minute in SharedPreferences.
     */
    fun scheduleDailyAlarm(context: Context, hour: Int, minute: Int): Boolean {
        val scheduled = scheduleAlarm(context, nextOccurrenceMillis(hour, minute))
        // Only persist hour/minute if the alarm was actually scheduled.
        // If setAlarmClock() failed (e.g. exact alarm permission revoked),
        // reading these prefs would falsely show the alarm as "armed" when it isn't.
        if (scheduled) {
            context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE).edit()
                .putInt(KEY_ALARM_HOUR, hour)
                .putInt(KEY_ALARM_MINUTE, minute)
                .apply()
            // The hour/minute write above is what a locked boot needs, so the mirror is refreshed
            // last, once every value it copies is already in place.
            AlarmBootMirror.mirrorFrom(context)
        }
        return scheduled
    }

    /**
     * The next wall-clock occurrence of [hour]:[minute], in the device's current time zone.
     *
     * Recomputed rather than stored so a time-zone change, a manual clock change, or a reboot all
     * land on the same answer: the saved hour/minute are a wall-clock rule, not an instant.
     */
    internal fun nextOccurrenceMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }.timeInMillis

    /**
     * Retrieves the saved daily alarm hour and minute, or null if not yet configured.
     */
    fun getSavedAlarmTime(context: Context): Pair<Int, Int>? {
        val prefs = context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_ALARM_HOUR) || !prefs.contains(KEY_ALARM_MINUTE)) {
            return null
        }
        val hour = prefs.getInt(KEY_ALARM_HOUR, -1)
        val minute = prefs.getInt(KEY_ALARM_MINUTE, -1)
        return if (hour in 0..23 && minute in 0..59) Pair(hour, minute) else null
    }

    fun scheduleAlarm(context: Context, triggerTimeMillis: Long): Boolean {
        val finalTriggerTime = rollForwardIfPast(triggerTimeMillis)
        if (!armAlarmClock(context, finalTriggerTime)) return false
        savePrefs(context, finalTriggerTime)
        return true
    }

    /**
     * Arms a trigger without writing anything to credential-protected storage.
     *
     * This exists for exactly one caller: [BootReceiver] handling `LOCKED_BOOT_COMPLETED`, which
     * runs before the first unlock. Normal `SharedPreferences` and `filesDir` live in
     * credential-encrypted storage and are unreadable (or unwritable) at that moment, so the usual
     * [scheduleAlarm] path would either persist nothing or fail. The trigger comes from
     * [AlarmBootMirror] instead, and the authoritative prefs are untouched until the normal
     * `BOOT_COMPLETED` pass runs after unlock.
     *
     * Note what this does *not* fix: the ring itself. `AlarmReceiver`, `AlarmForegroundService` and
     * `AlarmActivity` are not direct-boot aware, so Android defers the ring until the person
     * unlocks. This path re-arms the alarm across a locked boot; it does not make it ring before
     * unlock. See [BootReceiver] for the full explanation.
     */
    fun armWithoutPersisting(context: Context, triggerTimeMillis: Long): Boolean =
        armAlarmClock(context, rollForwardIfPast(triggerTimeMillis))

    /**
     * Every scheduling call lands here, so the exact-alarm guard and the PendingIntent identity
     * exist in one place and cannot drift between the persistent and locked-boot paths.
     */
    private fun armAlarmClock(context: Context, finalTriggerTime: Long): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // API 31+ exact alarm permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context, 0, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Alarm-clock style: shows in status bar, resists Doze/battery optimization.
        // setAlarmClock() is the only scheduling path used — no setExact/setWindow fallbacks,
        // so the alarm icon always appears and Doze bypass is granted on Android 12–15.
        val clockInfo = AlarmManager.AlarmClockInfo(finalTriggerTime, showPendingIntent)
        return try {
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
            true
        } catch (e: Exception) {
            // Defensive: never crash the caller if scheduling fails (e.g. revoked exact-alarm
            // permission, PendingIntent issue, or system server overload). Return false so the
            // UI can surface a plain explanation instead of failing silently.
            Log.w(TAG, "Failed to schedule alarm via setAlarmClock()", e)
            false
        }
    }

    /**
     * A past trigger is a bug in the caller, not a reason to hand AlarmManager an instant that
     * fires immediately. Advancing by whole days keeps the wall-clock time the person chose.
     */
    private fun rollForwardIfPast(triggerTimeMillis: Long): Long {
        val now = System.currentTimeMillis()
        if (triggerTimeMillis > now) return triggerTimeMillis
        return Calendar.getInstance().apply {
            timeInMillis = triggerTimeMillis
            while (timeInMillis <= now) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis
    }

    /**
     * Arms a one-off snooze.
     *
     * The occurrence that just rang consumed the single alarm slot, so a snooze that cannot be
     * scheduled leaves nothing pending at all. In that case the daily alarm is re-armed instead of
     * silently giving the person no alarm until they next open the app.
     *
     * @return true when the snooze itself was armed.
     */
    fun snoozeAlarm(context: Context, minutes: Int = 10): Boolean {
        val triggerTime = System.currentTimeMillis() + minutes * 60_000L
        if (scheduleAlarm(context, triggerTime)) {
            // A snooze is the most likely pending trigger at the moment of a reboot, so the mirror
            // has to know about it too, not just the daily time.
            AlarmBootMirror.mirrorFrom(context)
            return true
        }
        Log.w(TAG, "Snooze could not be armed; falling back to the daily alarm")
        getSavedAlarmTime(context)?.let { (hour, minute) ->
            scheduleDailyAlarm(context, hour, minute)
        }
        return false
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            // Ignore
        }
        clearPrefs(context)
        AlarmBootMirror.clear(context)
    }

    /** True when the person armed the alarm and has not cancelled it since. */
    fun isArmed(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALARM_ARMED, false)

    fun getNextAlarmTime(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val v = prefs.getLong(KEY_NEXT_ALARM_TIME, -1L)
        return if (v == -1L) null else v
    }

    private fun savePrefs(context: Context, timeMillis: Long) {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formatted = fmt.format(Date(timeMillis))
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_ALARM_TIME, timeMillis)
                .putString(KEY_NEXT_ALARM_FORMATTED, formatted)
                .putBoolean(KEY_ALARM_ARMED, true)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist alarm prefs", e)
        }
    }

    private fun clearPrefs(context: Context) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_NEXT_ALARM_TIME)
                .remove(KEY_NEXT_ALARM_FORMATTED)
                // An explicit cancel must never be resurrected by the launch-time safety net.
                .putBoolean(KEY_ALARM_ARMED, false)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear alarm prefs", e)
        }
    }
}
