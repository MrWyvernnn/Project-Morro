package com.morro.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * The wake-up check: one quiet question a few minutes after the alarm was dismissed.
 *
 * Dismissing an alarm is not the same as being awake. Morro already refuses to unlock until the
 * mission is done, but a person can still solve the gate half-asleep and go back to bed. Three
 * minutes after dismissal the app asks "Still up?"; if that goes unanswered for 90 seconds the alarm
 * rings once more.
 *
 * The hard limits are the point:
 * - exactly one re-ring, ever, per morning;
 * - no snooze and no loop — a second silence is a decision, not something to fight;
 * - the check is a question, not an alarm: its own channel, no full-screen intent, no sound.
 *
 * State is written with `commit()`: these are the values a reboot in the middle of the window has to
 * survive, which is also why [restoreAfterBoot] exists.
 */
object WakeUpCheck {

    private const val TAG = "WakeUpCheck"
    private const val PREFS_NAME = "morro_prefs"

    private const val KEY_ENABLED = "wake_check_enabled"
    private const val KEY_PENDING = "wake_check_pending"
    private const val KEY_ALERT_AT = "wake_check_alert_at"
    private const val KEY_ARMED_AT = "wake_check_armed_at"
    private const val KEY_RINGING = "wake_check_ringing"

    const val ACTION_CHECK = "com.morro.alarm.ACTION_WAKE_CHECK"
    const val ACTION_ALERT = "com.morro.alarm.ACTION_WAKE_CHECK_ALERT"
    const val ACTION_I_AM_UP = "com.morro.alarm.ACTION_I_AM_UP"

    /** Set on the re-ring broadcast and mirrored in prefs, so either launch path can see it. */
    const val EXTRA_RERING = "wake_check_rerring"

    const val CHANNEL_ID = "morro_wake_up_check"
    const val NOTIFICATION_ID = 1005

    /** Distinct from the alarm's 1001 slot: arming these must never replace the daily alarm. */
    private const val REQUEST_CHECK = 1003
    private const val REQUEST_ALERT = 1004

    internal const val CHECK_DELAY_MILLIS = 3 * 60_000L
    internal const val ALERT_DELAY_MILLIS = 90_000L

    /**
     * A second check inside this window belongs to the same morning. Re-arming the alarm (the next
     * day, twenty-something hours later) falls outside it, so the cap resets on its own.
     */
    internal const val ONE_PER_MORNING_WINDOW_MILLIS = 20 * 60 * 60_000L

    /**
     * Whether a new check may be armed, given when the last one was claimed.
     *
     * Any zero or 20-hour-plus gap is a new morning. A last-claim timestamp in the future means the
     * device clock moved backwards; that claim is not trustworthy as a cap, so the morning is
     * treated as free rather than blocking the check until the clock catches up.
     */
    internal fun morningIsFree(lastArmedAt: Long, now: Long): Boolean {
        if (lastArmedAt == 0L) return true
        val elapsed = now - lastArmedAt
        return elapsed < 0 || elapsed >= ONE_PER_MORNING_WINDOW_MILLIS
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) resolve(context)
    }

    fun isPending(context: Context): Boolean = prefs(context).getBoolean(KEY_PENDING, false)

    /**
     * True while the one permitted re-ring is on screen.
     *
     * Stored as a start time rather than a flag: if the process is force-stopped mid-re-ring, a flag
     * would stay on and make the *next* morning's alarm behave like a re-ring, which would leave
     * that day and the one after with no alarm. A stale timestamp simply expires.
     */
    fun isRingInProgress(context: Context): Boolean {
        val since = prefs(context).getLong(KEY_RINGING, 0L)
        return since != 0L && System.currentTimeMillis() - since < RE_RING_WINDOW_MILLIS
    }

    internal const val RE_RING_WINDOW_MILLIS = 15 * 60_000L

    /**
     * Arms the check 3 minutes out and the escalation 90 seconds after it.
     *
     * Returns false when the check is off, when one is already armed or spent this morning, or when
     * the system refused the scheduling — all three are legitimate "no check today", so callers do
     * not treat this as an error.
     *
     * The cap is *written before the alarms are scheduled*, not after. If the process died in the
     * gap between scheduling and recording, the escalation would be live with no record that this
     * morning's single re-ring was already promised — and a second dismissal would arm a second
     * one. Writing first makes the failure mode "no check today", which is the safe direction.
     */
    fun arm(context: Context): Boolean {
        if (!isEnabled(context)) return false

        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        if (!morningIsFree(prefs.getLong(KEY_ARMED_AT, 0L), now)) {
            Log.d(TAG, "Wake-up check already claimed this morning; not arming another")
            return false
        }

        val checkAt = now + CHECK_DELAY_MILLIS
        val alertAt = checkAt + ALERT_DELAY_MILLIS

        // Claim the morning first, then schedule. Both values are needed by restoreAfterBoot and by
        // the re-ring guard, so they are written together and synchronously.
        val claimed = prefs.edit()
            .putLong(KEY_ARMED_AT, now)
            .putBoolean(KEY_PENDING, true)
            .putLong(KEY_ALERT_AT, alertAt)
            .putLong(KEY_RINGING, 0L)
            .commit()
        if (!claimed) {
            Log.w(TAG, "Could not record the wake-up check; not arming an untracked one")
            return false
        }

        if (!schedule(context, REQUEST_CHECK, ACTION_CHECK, checkAt)) {
            releaseClaim(context)
            return false
        }
        if (!schedule(context, REQUEST_ALERT, ACTION_ALERT, alertAt)) {
            // Half a check is worse than none: the question would never be asked. The claim is
            // released because nothing was armed, so a later dismissal can still try again.
            cancelPending(context, REQUEST_CHECK, ACTION_CHECK)
            releaseClaim(context)
            return false
        }

        Log.d(TAG, "Wake-up check armed for +3 min")
        return true
    }

    /**
     * Gives back a claim whose alarms could not be scheduled.
     *
     * Only used on the failure path: an unarmed check must not consume the morning, otherwise a
     * single refused `setAlarmClock` would silently disable the follow-up until the next day.
     */
    private fun releaseClaim(context: Context) {
        prefs(context).edit()
            .putLong(KEY_ARMED_AT, 0L)
            .putBoolean(KEY_PENDING, false)
            .remove(KEY_ALERT_AT)
            .commit()
    }

    /**
     * Called when the escalation is due.
     *
     * Returns true exactly once per armed check: it clears the pending flag before anything else
     * happens, so a crash or a duplicate broadcast cannot ring twice.
     */
    fun consumeAlert(context: Context): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_PENDING, false)) return false

        prefs.edit()
            .putBoolean(KEY_PENDING, false)
            .putLong(KEY_RINGING, System.currentTimeMillis())
            .commit()
        cancelNotification(context)
        Log.i(TAG, "Wake-up check unanswered; re-ringing once")
        return true
    }

    /** The person confirmed, opened the app, or solved the re-ring: drop everything. */
    fun resolve(context: Context) {
        cancelPending(context, REQUEST_CHECK, ACTION_CHECK)
        cancelPending(context, REQUEST_ALERT, ACTION_ALERT)
        prefs(context).edit()
            .putBoolean(KEY_PENDING, false)
            .putLong(KEY_RINGING, 0L)
            .remove(KEY_ALERT_AT)
            .commit()
        cancelNotification(context)
    }

    /**
     * A reboot inside the window must not orphan the question.
     *
     * The escalation is re-armed for its original time, and the question is shown again: the person
     * still has to answer it, and the 90 seconds are still counted from when it was first asked.
     */
    fun restoreAfterBoot(context: Context) {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_PENDING, false)) {
            // A re-ring that was interrupted by the reboot is spent, not pending.
            if (prefs.getLong(KEY_RINGING, 0L) != 0L) {
                prefs.edit().putLong(KEY_RINGING, 0L).apply()
            }
            return
        }

        val alertAt = prefs.getLong(KEY_ALERT_AT, 0L)
        if (alertAt <= System.currentTimeMillis()) {
            Log.w(TAG, "Wake-up check window expired during the reboot")
            resolve(context)
            return
        }

        if (schedule(context, REQUEST_ALERT, ACTION_ALERT, alertAt)) {
            showQuestion(context)
        } else {
            resolve(context)
        }
    }

    /** Posts the "Still up?" notification. Its own channel, no full-screen intent, no sound. */
    fun showQuestion(context: Context) {
        ensureChannel(context)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val iAmUp = PendingIntent.getBroadcast(
            context,
            REQUEST_CHECK,
            Intent(context, WakeUpCheckReceiver::class.java).apply { action = ACTION_I_AM_UP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Still up?")
            .setContentText("Tap to confirm. Otherwise the alarm rings once more.")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(openApp)
            .addAction(0, "I'm up", iAmUp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Without POST_NOTIFICATIONS there is no question to ask, but the escalation is already
            // scheduled and still rings, so the feature degrades to "rings again" rather than
            // failing silently.
            Log.w(TAG, "Could not post the wake-up check notification", e)
        }
    }

    /**
     * The question's own channel: high importance so it is visible, no sound, no vibration, and
     * deliberately no Do-Not-Disturb bypass.
     *
     * Bypassing DND needs notification-policy access, which Morro never asks for — adding a
     * permission just to make a reminder louder than the person's own DND setting is the wrong
     * trade. So under DND the question may be invisible; what saves the morning is the escalation,
     * which is an `AlarmManager` alarm-clock alarm and rings through the alarm audio stream. That
     * stream is exempt from DND on every supported version, which is why the fallback is "rings
     * again" rather than "does nothing".
     *
     * This is the honest limit of the feature, and it is the one thing on this screen that has to
     * be measured on a real phone with DND on before it can be called verified.
     */
    private fun ensureChannel(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Wake-up check",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Asks whether you are still awake after dismissing an alarm"
            // A question should not make noise; the escalation is what makes noise.
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun schedule(context: Context, requestCode: Int, action: String, atMillis: Long): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }

        val check = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, WakeUpCheckReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), check)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule $action", e)
            false
        }
    }

    private fun cancelPending(context: Context, requestCode: Int, action: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, WakeUpCheckReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel $action", e)
        }
    }

    private fun cancelNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear the wake-up check notification", e)
        }
    }
}
