package com.morro.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
    const val KEY_ALARM_HOUR = "alarm_hour"
    const val KEY_ALARM_MINUTE = "alarm_minute"
    private const val ALARM_REQUEST_CODE = 1001

    /** The type of wake-up mission the user has selected */
    enum class MissionType {
        MATH,
        AFFIRMATION,
        VOICE_COMMITMENT
    }

    const val KEY_MISSION_TYPE = "mission_type"
    val DEFAULT_MISSION_TYPE = MissionType.MATH

    /**
     * Checks if the app is allowed to schedule exact alarms (API 31+).
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                alarmManager.canScheduleExactAlarms()
            } catch (e: Exception) {
                true
            }
        } else {
            true
        }
    }

    /**
     * Schedules the daily wake-up alarm for the given hour and minute.
     * Calculates the next occurrence (today if time is in future, else tomorrow).
     * Saves the hour and minute in SharedPreferences.
     */
    fun scheduleDailyAlarm(context: Context, hour: Int, minute: Int): Boolean {
        val now = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val scheduled = scheduleAlarm(context, targetCal.timeInMillis)
        // Only persist hour/minute if the alarm was actually scheduled.
        // If setAlarmClock() failed (e.g. exact alarm permission revoked),
        // reading these prefs would falsely show the alarm as "armed" when it isn't.
        if (scheduled) {
            alarmTimePrefs.edit()
                .putInt(KEY_ALARM_HOUR, hour)
                .putInt(KEY_ALARM_MINUTE, minute)
                .apply()
        }
        return scheduled
    }

    /**
     * Retrieves the saved daily alarm hour and minute, or null if not yet configured.
     */
    fun getSavedAlarmTime(context: Context): Pair<Int, Int>? {
        val prefs = alarmPrefs
        if (!prefs.contains(KEY_ALARM_HOUR) || !prefs.contains(KEY_ALARM_MINUTE)) {
            return null
        }
        val hour = prefs.getInt(KEY_ALARM_HOUR, -1)
        val minute = prefs.getInt(KEY_ALARM_MINUTE, -1)
        return if (hour in 0..23 && minute in 0..59) Pair(hour, minute) else null
    }

    fun scheduleAlarm(context: Context, triggerTimeMillis: Long): Boolean {
        var finalTriggerTime = triggerTimeMillis
        val now = System.currentTimeMillis()
        if (finalTriggerTime <= now) {
            // Fix Past-Time Crashing: if in past, advance by 1 day until in future
            val cal = Calendar.getInstance().apply {
                timeInMillis = finalTriggerTime
                while (timeInMillis <= now) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            finalTriggerTime = cal.timeInMillis
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // API 31+ exact alarm permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.morro.alarm.ACTION_TRIGGER_ALARM"
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
            savePrefs(context, finalTriggerTime)
            true
        } catch (e: Exception) {
            // Defensive: never crash the caller if scheduling fails (e.g. revoked exact-alarm
            // permission, PendingIntent issue, or system server overload). Return false so the
            // UI can surface a plain explanation instead of failing silently.
            Log.w(TAG, "Failed to schedule alarm via setAlarmClock()", e)
            false
        }
    }

    fun snoozeAlarm(context: Context, minutes: Int = 10) {
        val triggerTime = System.currentTimeMillis() + minutes * 60_000L
        scheduleAlarm(context, triggerTime)
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.morro.alarm.ACTION_TRIGGER_ALARM"
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
    }

    fun getNextAlarmTime(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val v = prefs.getLong(KEY_NEXT_ALARM_TIME, -1L)
        return if (v == -1L) null else v
    }

    fun getFormattedTime(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NEXT_ALARM_FORMATTED, null)
    }

    private fun savePrefs(context: Context, timeMillis: Long) {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formatted = fmt.format(Date(timeMillis))
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_ALARM_TIME, timeMillis)
                .putString(KEY_NEXT_ALARM_FORMATTED, formatted)
                .commit()
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
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear alarm prefs", e)
        }
    }
}
