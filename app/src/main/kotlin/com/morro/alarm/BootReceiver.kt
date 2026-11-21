package com.morro.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver: keeps the armed alarm alive across reboots and clock changes.
 *
 * - BOOT_COMPLETED / MY_PACKAGE_REPLACED: restore the stored absolute trigger if it is still in
 *   the future, otherwise recompute the next occurrence from the saved hour/minute.
 * - TIME_SET / TIMEZONE_CHANGED: the stored absolute trigger is now the wrong wall-clock time, so
 *   recompute it from the saved hour/minute.
 *
 * Every branch re-arms only what was armed. A cancelled alarm keeps its hour/minute for the picker
 * and must stay cancelled, which is why `isArmed` — not the presence of a saved time — decides.
 */
class BootReceiver : BroadcastReceiver() {

    private companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // A cancelled alarm keeps its hour and minute so the picker still shows what was chosen.
        // Without this check, the "recompute from the saved time" branches below put a switched-off
        // alarm back on the calendar on every reboot.
        if (!AlarmScheduler.isArmed(context)) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val nextAlarmTime = AlarmScheduler.getNextAlarmTime(context)
                if (nextAlarmTime != null && nextAlarmTime > System.currentTimeMillis()) {
                    rearm { AlarmScheduler.scheduleAlarm(context, nextAlarmTime) }
                } else {
                    // The stored trigger is gone or already past: the ring was lost to a reboot,
                    // not cancelled, so the next occurrence is recomputed from the saved time.
                    AlarmScheduler.getSavedAlarmTime(context)?.let { (hour, minute) ->
                        rearm { AlarmScheduler.scheduleDailyAlarm(context, hour, minute) }
                    }
                }
            }

            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                AlarmScheduler.getSavedAlarmTime(context)?.let { (hour, minute) ->
                    rearm { AlarmScheduler.scheduleDailyAlarm(context, hour, minute) }
                }
            }
        }
    }

    /**
     * Runs one re-arm attempt and says so when it fails.
     *
     * Exact-alarm access can be revoked between the ring and the reboot. That used to be a silent
     * no-op here, which left the person with no alarm and no trace of why.
     */
    private fun rearm(block: () -> Boolean) {
        if (!block()) Log.w(TAG, "Could not re-arm the alarm; exact alarm access may be revoked")
    }
}
