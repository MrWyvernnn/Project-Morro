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

    /**
     * When "Still up?" was first put in front of the person, or 0 if it never was.
     *
     * The 90-second window is counted from here, not from when the escalation was scheduled. Without
     * this key a reboot cannot tell "asked, with 20 seconds left" from "never asked, 3 minutes of
     * waiting still owed", and the restore path has to guess — which is how a question gets asked
     * early and a window gets shortened.
     */
    private const val KEY_QUESTION_SHOWN_AT = "wake_check_question_shown_at"

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
            .putLong(KEY_QUESTION_SHOWN_AT, 0L)
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
            .putLong(KEY_QUESTION_SHOWN_AT, 0L)
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
            .putLong(KEY_QUESTION_SHOWN_AT, 0L)
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
            .putLong(KEY_QUESTION_SHOWN_AT, 0L)
            .commit()
        cancelNotification(context)
    }

    /**
     * A reboot inside the window must not orphan the question, and must not shorten its deadline.
     *
     * What actually happened before the reboot decides this, and [KEY_QUESTION_SHOWN_AT] is the only
     * thing that can tell the difference. Without it, a reboot two minutes after dismissal re-posts a
     * question that was not due for another minute, and a reboot twenty seconds after the question
     * was asked can leave the person one second to answer it. [restorePlan] names the four cases; this
     * function only carries them out.
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

        val now = System.currentTimeMillis()
        val armedAt = prefs.getLong(KEY_ARMED_AT, 0L)
        val checkAt = if (armedAt > 0L) armedAt + CHECK_DELAY_MILLIS else 0L
        val alertAt = prefs.getLong(KEY_ALERT_AT, 0L)
        val shownAt = prefs.getLong(KEY_QUESTION_SHOWN_AT, 0L)

        when (restorePlan(shownAt, checkAt, alertAt, now)) {
            RestorePlan.EXPIRED -> {
                Log.w(TAG, "Wake-up check window expired during the reboot")
                resolve(context)
            }

            RestorePlan.ASK_LATER -> {
                // Nothing was asked yet and nothing is owed yet: put both alarms back on their
                // original times and leave the question alone.
                val checkArmed = schedule(context, REQUEST_CHECK, ACTION_CHECK, checkAt)
                if (checkArmed && schedule(context, REQUEST_ALERT, ACTION_ALERT, alertAt)) {
                    Log.d(TAG, "Wake-up check restored; the question is still ahead")
                } else {
                    resolve(context)
                }
            }

            RestorePlan.RESCHEDULE_ORIGINAL -> {
                // Asked already: re-post it (the reboot dropped the notification) and keep the
                // deadline it already had. Re-posting must not move the window.
                if (schedule(context, REQUEST_ALERT, ACTION_ALERT, alertAt)) {
                    showQuestion(context)
                } else {
                    resolve(context)
                }
            }

            RestorePlan.REARM_FULL_WINDOW -> {
                // Due, but never asked. Start the window now so the person gets the whole 90 seconds
                // instead of whatever was left of a window they never saw.
                val freshAlertAt = now + ALERT_DELAY_MILLIS
                val written = prefs.edit().putLong(KEY_ALERT_AT, freshAlertAt).commit()
                if (!written) {
                    Log.w(TAG, "Could not record the restored window; dropping the check")
                    resolve(context)
                    return
                }
                if (schedule(context, REQUEST_ALERT, ACTION_ALERT, freshAlertAt)) {
                    showQuestion(context)
                } else {
                    resolve(context)
                }
            }
        }
    }

    /**
     * Posts the "Still up?" notification. Its own channel, no full-screen intent, no sound.
     *
     * The first post is what starts the 90-second window, so the timestamp is written here rather
     * than by the caller: every route to the question goes through this function, and the deadline
     * must not be resettable by asking again.
     */
    fun showQuestion(context: Context) {
        ensureChannel(context)

        val questionShownAt = prefs(context).getLong(KEY_QUESTION_SHOWN_AT, 0L)
        if (questionShownAt == 0L) {
            val stamped = prefs(context).edit()
                .putLong(KEY_QUESTION_SHOWN_AT, System.currentTimeMillis())
                .commit()
            if (!stamped) {
                // Without a start time the window cannot be reasoned about after a reboot. Asking
                // anyway is still better than staying silent: the escalation is already armed.
                Log.w(TAG, "Could not record when the question was shown")
            }
        }

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

/**
 * What a reboot inside the wake-up check window has to do about the question.
 *
 * Four outcomes, because "re-arm the escalation and ask again" is not one behaviour:
 *
 * - [ASK_LATER] — the question was never shown and is not due yet. Asking now would move it minutes
 *   earlier and hand out a window the person never asked for; the original schedule is intact.
 * - [RESCHEDULE_ORIGINAL] — it was already asked and its deadline is still ahead. The question is
 *   re-posted (a reboot clears notifications) but the deadline is *not* moved: the 90 seconds stay
 *   counted from the first ask.
 * - [REARM_FULL_WINDOW] — it was never asked and its moment has passed. The person is owed the full
 *   90 seconds, so the window starts now rather than being handed back half-spent.
 * - [EXPIRED] — the deadline is gone. Nothing is owed; resolve and stop.
 */
internal enum class RestorePlan {
    EXPIRED,
    ASK_LATER,
    RESCHEDULE_ORIGINAL,
    REARM_FULL_WINDOW,
}

/**
 * The restore decision, pure so the awkward cases can be pinned without a reboot.
 *
 * @param questionShownAt when "Still up?" was first posted, or 0 if it never was.
 * @param checkAt when the question was due, or 0 when the claim has no recorded start.
 * @param alertAt when the escalation is due, or 0 when it was never recorded.
 */
internal fun restorePlan(
    questionShownAt: Long,
    checkAt: Long,
    alertAt: Long,
    now: Long,
): RestorePlan {
    // A missing or already-passed deadline is the same thing: nothing left to ask about.
    if (alertAt <= now) return RestorePlan.EXPIRED
    if (questionShownAt != 0L) return RestorePlan.RESCHEDULE_ORIGINAL
    if (now >= checkAt) return RestorePlan.REARM_FULL_WINDOW
    return RestorePlan.ASK_LATER
}

