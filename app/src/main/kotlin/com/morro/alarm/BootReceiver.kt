package com.morro.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BootReceiver: keeps the armed alarm alive across reboots and clock changes.
 *
 * - BOOT_COMPLETED / MY_PACKAGE_REPLACED: restore the stored absolute trigger if it is still in
 *   the future, otherwise recompute the next occurrence from the saved hour/minute.
 * - TIME_SET / TIMEZONE_CHANGED: the stored absolute trigger is now the wrong wall-clock time, so
 *   recompute it from the saved hour/minute. Only do this when an alarm was actually armed; a
 *   cancelled alarm keeps its hour/minute for the picker and must stay cancelled.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val nextAlarmTime = AlarmScheduler.getNextAlarmTime(context)
                if (nextAlarmTime != null && nextAlarmTime > System.currentTimeMillis()) {
                    AlarmScheduler.scheduleAlarm(context, nextAlarmTime)
                } else {
                    AlarmScheduler.getSavedAlarmTime(context)?.let { (hour, minute) ->
                        AlarmScheduler.scheduleDailyAlarm(context, hour, minute)
                    }
                }
            }

            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                if (AlarmScheduler.getNextAlarmTime(context) == null) return
                AlarmScheduler.getSavedAlarmTime(context)?.let { (hour, minute) ->
                    AlarmScheduler.scheduleDailyAlarm(context, hour, minute)
                }
            }
        }
    }
}
