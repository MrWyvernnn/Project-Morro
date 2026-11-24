package com.morro.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * WakeUpCheckReceiver: the three beats of the wake-up check.
 *
 * - [WakeUpCheck.ACTION_CHECK]: post the "Still up?" question. The escalation is already scheduled,
 *   so this only has to ask.
 * - [WakeUpCheck.ACTION_I_AM_UP]: the person confirmed; everything is dropped.
 * - [WakeUpCheck.ACTION_ALERT]: 90 seconds with no answer. The morning's single re-ring is spent,
 *   then the ring is started through [AlarmReceiver] so the full-screen takeover, the foreground
 *   service and the lock-screen behaviour are exactly the same as at the scheduled time.
 */
class WakeUpCheckReceiver : BroadcastReceiver() {

    private companion object {
        private const val TAG = "WakeUpCheckReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WakeUpCheck.ACTION_CHECK -> {
                if (!WakeUpCheck.isPending(context)) return
                WakeUpCheck.showQuestion(context)
            }

            WakeUpCheck.ACTION_I_AM_UP -> WakeUpCheck.resolve(context)

            WakeUpCheck.ACTION_ALERT -> {
                if (!WakeUpCheck.consumeAlert(context)) return
                reRingOnce(context)
            }
        }
    }

    /**
     * Starts the one re-ring.
     *
     * The extra is carried on the broadcast for `AlarmReceiver` and mirrored in prefs, because a
     * full-screen intent built by the foreground service can reach `AlarmActivity` without it. Both
     * paths have to know this ring must not reschedule anything.
     */
    private fun reRingOnce(context: Context) {
        try {
            context.sendBroadcast(
                Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_TRIGGER_ALARM
                    putExtra(WakeUpCheck.EXTRA_RERING, true)
                }
            )
        } catch (e: Exception) {
            // Never fail silently: a re-ring that cannot start still leaves the notification and the
            // log, and the next scheduled alarm is untouched.
            Log.e(TAG, "Could not start the wake-up re-ring", e)
        }
    }
}
