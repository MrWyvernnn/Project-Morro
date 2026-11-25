package com.morro.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"

        /** The one action `AlarmScheduler` and the wake-up re-ring both send. */
        const val ACTION_TRIGGER_ALARM = "com.morro.alarm.ACTION_TRIGGER_ALARM"

        /**
         * The expected instant this delivery belongs to, when the sender knows it.
         *
         * Set on a catch-up ring, where it is load-bearing rather than informative: the receiver
         * treats it as the occurrence being rung instead of trusting the stored trigger, and the
         * duplicate guard compares against it. Matching a PendingIntent ignores extras, so it never
         * changes which alarm slot is replaced.
         */
        const val EXTRA_OCCURRENCE = "com.morro.alarm.EXTRA_OCCURRENCE"

        private const val ACTION_TEST_ALARM = "com.morro.alarm.ACTION_TEST_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "(2) Broadcast received: AlarmReceiver fired")

        val isWakeUpRering = intent.getBooleanExtra(WakeUpCheck.EXTRA_RERING, false)

        // Health log first: the expected trigger is still in prefs at this instant, and the
        // dismissal path re-arms tomorrow's alarm later in the same ring, which would overwrite it.
        // A debug test ring and a wake-up re-ring are not the morning ring, so neither is logged.
        if (intent.action != ACTION_TEST_ALARM && !isWakeUpRering) {
            // A catch-up delivery carries the occurrence it is standing in for, and that is more
            // trustworthy than the stored trigger: if the pending alarm was cancelled or replaced in
            // the seconds between arming the catch-up and this broadcast, prefs no longer describe
            // what is ringing. The extra does.
            val carried = intent.getLongExtra(EXTRA_OCCURRENCE, Long.MIN_VALUE)
            val expected = if (carried != Long.MIN_VALUE) {
                carried
            } else {
                AlarmScheduler.getNextAlarmTime(context)
            }
            val now = System.currentTimeMillis()

            // One occurrence rings once. The locked-boot catch-up exists because a due alarm can be
            // sitting undelivered; if that delivery then arrives too, both paths must agree that
            // this instant has already rung. Read before recordFired() overwrites the evidence.
            if (isDuplicateRing(expected, MissedAlarmLog.handledOccurrence(context))) {
                Log.w(TAG, "Duplicate delivery for $expected, which already rang; not ringing again")
                return
            }

            MissedAlarmLog.recordFired(context, expected, now)
            if (expected != null && lateFireFor(expected, now) != null) {
                MissedAlarmLog.recordLateFire(context, expected, now)
            }
        }

        // Acquire PARTIAL_WAKE_LOCK immediately so the CPU stays alive while we
        // start the foreground service and launch the full-screen alarm activity.
        // Always released in the finally block below (never leaked).
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "morro:alarm_receiver_wakelock"
        )
        wakeLock?.acquire(60 * 1000L)
        Log.d(TAG, "Acquired partial wake lock for 60s")

        try {
            // getLatestMemoPath() stats up to three files. That is disk I/O on the broadcast main
            // thread, on the one path that has to surface the alarm fast, and it only feeds a log
            // line, so it is skipped unless verbose logging is actually on.
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                val memoPath = MemoStorage.getLatestMemoPath(context)
                Log.d(TAG, "(4) File check for commitment memo: path=${memoPath ?: "none found (fallback active)"}")
            }

            // Start foreground service to hold wake-lock while alarm is active.
            // If this fails, we fall back to a high-priority notification below
            // instead of crashing silently.
            var serviceStarted = false
            try {
                Log.d(TAG, "Starting AlarmForegroundService")
                val serviceIntent = Intent(context, AlarmForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                serviceStarted = true
                Log.d(TAG, "AlarmForegroundService started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AlarmForegroundService; falling back to notification only", e)
                postFallbackNotification(context)
            }

            // On Android 14+ (targetSdk 35), calling startActivity() from a BroadcastReceiver
            // in the background throws a "startActivity forbidden in background" exception
            // (Background Activity Launch restriction / BAL). The foreground service's
            // notification carries a setFullScreenIntent() that legally surfaces
            // AlarmActivity over the lock screen — that is the sole launch path.
            if (!serviceStarted) {
                Log.w(TAG, "Foreground service failed to start; alarm may not launch on Android 14+")
            }
            // Safety net: attempt a direct startActivity() even when the foreground
            // service started, in case the full-screen intent notification isn't
            // enough to bring AlarmActivity to front on this device.
            try {
                val intent = Intent(context, com.morro.alarm.AlarmActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    // Carried through so the activity knows this ring must not reschedule anything.
                    if (isWakeUpRering) putExtra(WakeUpCheck.EXTRA_RERING, true)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Direct startActivity() fallback failed: ${e.message}")
                // Posted whether or not the service reported success: it may have died before
                // promoting itself, and a duplicate post is just the same notification refreshed.
                // This is the only surface left when the activity cannot be launched.
                postFallbackNotification(context)
            }
        } catch (e: Exception) {
            // Never fail silently. Anything unexpected above means the activity was not launched
            // and the service may never have run, so this is the last chance to leave a surface
            // the person can tap to reach the alarm.
            Log.e(TAG, "AlarmReceiver encountered an unexpected error", e)
            postFallbackNotification(context)
        } finally {
            // Always release the wake lock so we don't hold the CPU longer than needed.
            try {
                wakeLock?.release()
                Log.d(TAG, "Released partial wake lock")
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing wake lock", e)
            }
        }
    }

    /**
     * Last-resort surface for a ring that could not start the foreground service or the activity.
     *
     * The channel normally comes from [AlarmForegroundService.onCreate]; when that service never
     * runs, the channel does not exist, so it is created here first. Nothing about this path is
     * silent: a failure is logged, and a posted notification is tappable and full-screen.
     */
    private fun postFallbackNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        AlarmForegroundService.CHANNEL_ID,
                        "Alarm Service",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        setBypassDnd(true)
                        description = "Keeps the alarm alive during ringing"
                    }
                )
            }
            val launch = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                2002,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, AlarmForegroundService.CHANNEL_ID)
                .setContentTitle("Morro Alarm")
                .setContentText("Alarm is active — tap to dismiss")
                .setSmallIcon(R.drawable.ic_morro_notification)
                .setContentIntent(pending)
                .setFullScreenIntent(pending, true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .build()
            nm.notify(AlarmForegroundService.NOTIFICATION_ID, notification)
            Log.d(TAG, "Fallback alarm notification posted")
        } catch (e: Exception) {
            Log.e(TAG, "Could not post fallback alarm notification", e)
        }
    }
}

/**
 * True when this delivery is for an occurrence that already rang.
 *
 * Identity, not arithmetic: [handledOccurrenceMillis] is the expected instant of the occurrence
 * that last rang, so this answers "have I already rung for *this* one?" without comparing two
 * wall-clock timestamps. A clock change makes any timestamp comparison lie; it cannot make two
 * different instants equal.
 *
 * A null expected time means nothing is armed at an instant — a debug ring after a cancel — so
 * there is no occurrence to have duplicated.
 */
internal fun isDuplicateRing(expectedAtMillis: Long?, handledOccurrenceMillis: Long): Boolean =
    expectedAtMillis != null && expectedAtMillis == handledOccurrenceMillis
