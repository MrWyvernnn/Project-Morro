package com.morro.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BootReceiver: Reschedules alarm after device reboot.
 * Reads the stored alarm time from SharedPreferences and reschedules via AlarmScheduler.
 * Only reschedules if the alarm time is in the future.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val nextAlarmTime = AlarmScheduler.getNextAlarmTime(context)

            // Only reschedule if alarm exists and is in the future
            if (nextAlarmTime != null && nextAlarmTime > System.currentTimeMillis()) {
                AlarmScheduler.scheduleAlarm(context, nextAlarmTime)
            }
        }
    }
}
