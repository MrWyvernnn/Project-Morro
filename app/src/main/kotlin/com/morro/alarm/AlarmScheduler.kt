package com.morro.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * AlarmScheduler: Handles scheduling exact alarms via AlarmManager.
 * Uses setExactAndAllowWhileIdle() to fire even in Doze mode.
 * Stores the next alarm time in SharedPreferences so BootReceiver can reschedule after reboot.
 */
object AlarmScheduler {

    private const val PREFS_NAME = "morro_prefs"
    private const val KEY_NEXT_ALARM_TIME = "next_alarm_time"
    private const val ALARM_REQUEST_CODE = 1001

    /**
     * Schedule an exact alarm at the given time.
     * Stores the time in SharedPreferences for boot rescheduling.
     * Validates that the trigger time is in the future.
     */
    fun scheduleAlarm(context: Context, triggerTimeMillis: Long) {
        val now = System.currentTimeMillis()

        // Reject past times — AlarmManager would fire immediately
        if (triggerTimeMillis <= now) {
            throw IllegalArgumentException(
                "Alarm trigger time must be in the future. " +
                "Given: $triggerTimeMillis, now: $now"
            )
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setExactAndAllowWhileIdle to fire even when device is in Doze mode
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            pendingIntent
        )

        // Store alarm time for boot rescheduling
        saveNextAlarmTime(context, triggerTimeMillis)
    }

    /**
     * Cancel the scheduled alarm
     */
    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        clearNextAlarmTime(context)
    }

    /**
     * Save the next alarm time to SharedPreferences
     */
    private fun saveNextAlarmTime(context: Context, timeMillis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_ALARM_TIME, timeMillis)
            .apply()
    }

    /**
     * Get the next alarm time from SharedPreferences
     * Returns null if no alarm is scheduled
     */
    fun getNextAlarmTime(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val time = prefs.getLong(KEY_NEXT_ALARM_TIME, -1L)
        return if (time == -1L) null else time
    }

    /**
     * Clear the stored alarm time
     */
    private fun clearNextAlarmTime(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_NEXT_ALARM_TIME)
            .apply()
    }
}
