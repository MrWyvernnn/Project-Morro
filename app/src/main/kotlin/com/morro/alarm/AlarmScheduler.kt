package com.morro.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AlarmScheduler: Handles scheduling alarm-clock-style alarms.
 * Uses AlarmManager.setAlarmClock() so the system shows the alarm icon
 * in the status bar and bypasses aggressive battery optimization (e.g. Nothing OS).
 * Stores next_alarm_time + formatted string in SharedPreferences (morro_prefs).
 */
object AlarmScheduler {

    private const val PREFS_NAME = "morro_prefs"
    private const val KEY_NEXT_ALARM_TIME = "next_alarm_time"
    private const val KEY_NEXT_ALARM_FORMATTED = "next_alarm_formatted"
    private const val ALARM_REQUEST_CODE = 1001

    fun scheduleAlarm(context: Context, triggerTimeMillis: Long) {
        val now = System.currentTimeMillis()
        if (triggerTimeMillis <= now) {
            throw IllegalArgumentException("Alarm must be in future")
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Alarm-clock style: shows in status bar, resists Doze/battery optimization
        val clockInfo = AlarmManager.AlarmClockInfo(triggerTimeMillis, pendingIntent)
        alarmManager.setAlarmClock(clockInfo, pendingIntent)

        savePrefs(context, triggerTimeMillis)
    }

    fun snoozeAlarm(context: Context, minutes: Int = 10) {
        val triggerTime = System.currentTimeMillis() + minutes * 60_000L
        scheduleAlarm(context, triggerTime)
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
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
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_ALARM_TIME, timeMillis)
            .putString(KEY_NEXT_ALARM_FORMATTED, formatted)
            .apply()
    }

    private fun clearPrefs(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_NEXT_ALARM_TIME)
            .remove(KEY_NEXT_ALARM_FORMATTED)
            .apply()
    }
}
