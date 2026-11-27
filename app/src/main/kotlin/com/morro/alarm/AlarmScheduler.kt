package com.morro.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
    private const val KEY_ALARM_ARMED = "alarm_armed"
    const val KEY_ALARM_HOUR = "alarm_hour"
    const val KEY_ALARM_MINUTE = "alarm_minute"
    private const val ALARM_REQUEST_CODE = 1001

    /**
     * The catch-up ring's own slot.
     *
     * Distinct from [ALARM_REQUEST_CODE] on purpose: the daily alarm, a snooze and a re-arm all
     * share slot 1001 and `FLAG_UPDATE_CURRENT`, so whichever arms last wins. A catch-up ring must
     * never be the thing that gets replaced, and must never be the thing that replaces.
     */
    private const val CATCH_UP_REQUEST_CODE = 1006

    /**
     * Dedicated slot for [AlarmForegroundService.onTaskRemoved] swipe recovery.
     *
     * Distinct from daily alarm (1001), wake-up check (1003/1004), and catch-up (1006), so arming
     * or cancelling swipe recovery never overwrites tomorrow's alarm or poisons MissedAlarmLog.
     */
    private const val RECOVERY_REQUEST_CODE = 1007
    const val ACTION_RECOVER_RING = "com.morro.alarm.ACTION_RECOVER_RING"
    const val EXTRA_RECOVERY_ALARM_ID = "com.morro.alarm.EXTRA_RECOVERY_ALARM_ID"

    /** Long enough for this process to return, short enough that the ring feels immediate. */
    internal const val CATCH_UP_DELAY_MILLIS = 2_000L

    /**
     * Schedules the daily wake-up alarm for the given hour and minute.
     * Calculates the next occurrence (today if time is in future, else tomorrow).
     * Saves the hour and minute in SharedPreferences.
     */
    fun scheduleDailyAlarm(context: Context, hour: Int, minute: Int): Boolean {
        val scheduled = scheduleAlarm(context, nextOccurrenceMillis(hour, minute))
        // Only persist hour/minute if the alarm was actually scheduled.
        // If setAlarmClock() failed (e.g. exact alarm permission revoked),
        // reading these prefs would falsely show the alarm as "armed" when it isn't.
        if (scheduled) {
            context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE).edit()
                .putInt(KEY_ALARM_HOUR, hour)
                .putInt(KEY_ALARM_MINUTE, minute)
                .apply()
            // The hour/minute write above is what a locked boot needs, so the mirror is refreshed
            // last, once every value it copies is already in place.
            AlarmBootMirror.mirrorFrom(context)
        }
        return scheduled
    }

    /**
     * The next wall-clock occurrence of [hour]:[minute], in the device's current time zone.
     *
     * Recomputed rather than stored so a time-zone change, a manual clock change, or a reboot all
     * land on the same answer: the saved hour/minute are a wall-clock rule, not an instant.
     */
    internal fun nextOccurrenceMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }.timeInMillis

    /**
     * Retrieves the saved daily alarm hour and minute, or null if not yet configured.
     */
    fun getSavedAlarmTime(context: Context): Pair<Int, Int>? {
        val prefs = context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_ALARM_HOUR) || !prefs.contains(KEY_ALARM_MINUTE)) {
            return null
        }
        val hour = prefs.getInt(KEY_ALARM_HOUR, -1)
        val minute = prefs.getInt(KEY_ALARM_MINUTE, -1)
        return if (hour in 0..23 && minute in 0..59) Pair(hour, minute) else null
    }

    fun scheduleAlarm(context: Context, triggerTimeMillis: Long): Boolean {
        val finalTriggerTime = rollForwardIfPast(triggerTimeMillis)
        if (!armAlarmClock(context, finalTriggerTime)) return false
        savePrefs(context, finalTriggerTime)
        return true
    }

    /**
     * Arms the ring for an occurrence that came due and never rang.
     *
     * This is the locked-boot catch-up: the alarm was armed before the first unlock, the person
     * unlocked hours later, and the occurrence is now in the past with nothing in
     * `alarm_last_fired_at` for it. Rolling straight to tomorrow would delete the morning they
     * needed; this rings it instead.
     *
     * It deliberately does **not** write `morro_prefs`: the stored expected trigger must stay on
     * the missed occurrence until the person actually dismisses the ring, so the miss stays visible
     * and the normal dismissal path is the thing that arms tomorrow.
     *
     * Idempotent twice over: it refuses when the occurrence already rang, and it re-checks that
     * immediately before arming, because the deferred broadcast may have been delivered between
     * the caller's decision and this call.
     *
     * @return true when the catch-up was armed.
     */
    fun armCatchUp(context: Context, occurrenceAtMillis: Long): Boolean {
        if (MissedAlarmLog.handledOccurrence(context) == occurrenceAtMillis) {
            Log.i(TAG, "Catch-up skipped: the occurrence already rang")
            return false
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
            // Carried so the ring can be tied to the occurrence it is catching up on.
            putExtra(AlarmReceiver.EXTRA_OCCURRENCE, occurrenceAtMillis)
        }
        val armed = armAlarmClock(
            context,
            System.currentTimeMillis() + CATCH_UP_DELAY_MILLIS,
            CATCH_UP_REQUEST_CODE,
            intent
        )
        if (armed) {
            Log.w(TAG, "Armed a catch-up ring for the missed occurrence at $occurrenceAtMillis")
        }
        return armed
    }

    /**
     * Arms a trigger without writing anything to credential-protected storage.
     *
     * This exists for exactly one caller: [BootReceiver] handling `LOCKED_BOOT_COMPLETED`, which
     * runs before the first unlock. Normal `SharedPreferences` and `filesDir` live in
     * credential-encrypted storage and are unreadable (or unwritable) at that moment, so the usual
     * [scheduleAlarm] path would either persist nothing or fail. The trigger comes from
     * [AlarmBootMirror] instead, and the authoritative prefs are untouched until the normal
     * `BOOT_COMPLETED` pass runs after unlock.
     *
     * It must never persist, and never call [AlarmBootMirror.mirrorFrom]: before the first unlock
     * that function reads `isArmed` from credential storage, gets the default `false`, and clears
     * the device-protected mirror it was just read from — destroying the only copy of the alarm.
     * A contract test over this function's body fails if that ever changes.
     *
     * Note what this does *not* fix: the ring itself. `AlarmReceiver`, `AlarmForegroundService` and
     * `AlarmActivity` are not direct-boot aware, so Android defers the ring until the person
     * unlocks. This path re-arms the alarm across a locked boot; it does not make it ring before
     * unlock. See [BootReceiver] for the full explanation.
     */
    fun armWithoutPersisting(context: Context, triggerTimeMillis: Long): Boolean =
        armAlarmClock(context, rollForwardIfPast(triggerTimeMillis))

    /**
     * Every scheduling call lands here, so the exact-alarm guard exists in one place and cannot
     * drift between the persistent, locked-boot and catch-up paths.
     *
     * The request code and intent are parameters because the catch-up ring needs its own slot and
     * carries an extra; every other caller uses the daily alarm's identity.
     */
    private fun armAlarmClock(
        context: Context,
        finalTriggerTime: Long,
        requestCode: Int = ALARM_REQUEST_CODE,
        triggerIntent: Intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
        },
    ): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // API 31+ exact alarm permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, triggerIntent,
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
            true
        } catch (e: Exception) {
            // Defensive: never crash the caller if scheduling fails (e.g. revoked exact-alarm
            // permission, PendingIntent issue, or system server overload). Return false so the
            // UI can surface a plain explanation instead of failing silently.
            Log.w(TAG, "Failed to schedule alarm via setAlarmClock()", e)
            false
        }
    }

    /**
     * A past trigger is a bug in the caller, not a reason to hand AlarmManager an instant that
     * fires immediately. Advancing by whole days keeps the wall-clock time the person chose.
     */
    private fun rollForwardIfPast(triggerTimeMillis: Long): Long {
        val now = System.currentTimeMillis()
        if (triggerTimeMillis > now) return triggerTimeMillis
        return Calendar.getInstance().apply {
            timeInMillis = triggerTimeMillis
            while (timeInMillis <= now) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis
    }

    /**
     * Arms a one-off snooze.
     *
     * The occurrence that just rang consumed the single alarm slot, so a snooze that cannot be
     * scheduled leaves nothing pending at all. In that case the daily alarm is re-armed instead of
     * silently giving the person no alarm until they next open the app.
     *
     * @return true when the snooze itself was armed.
     */
    fun snoozeAlarm(context: Context, minutes: Int = 10): Boolean {
        AlarmForegroundService.markRingDismissedOrSnoozed(context)
        val triggerTime = System.currentTimeMillis() + minutes * 60_000L
        if (scheduleAlarm(context, triggerTime)) {
            // A snooze is the most likely pending trigger at the moment of a reboot, so the mirror
            // has to know about it too, not just the daily time.
            AlarmBootMirror.mirrorFrom(context)
            return true
        }
        Log.w(TAG, "Snooze could not be armed; falling back to the daily alarm")
        getSavedAlarmTime(context)?.let { (hour, minute) ->
            scheduleDailyAlarm(context, hour, minute)
        }
        return false
    }

    /**
     * Schedules a one-shot recovery broadcast at `now + delayMs` via `setAlarmClock()` when the
     * task is swiped away mid-ring.
     *
     * Does NOT touch `morro_prefs` daily trigger bookkeeping or `AlarmBootMirror`.
     *
     * Honest limit: this survives a swipe from Recents (which triggers `onTaskRemoved`). It does
     * NOT survive Force Stop from Android Settings, because Force Stop kills all scheduled
     * `AlarmManager` triggers across the app — for that case, `MainActivity` re-arms stale alarms
     * on next launch.
     */
    fun scheduleRecoveryAlarm(
        context: Context,
        delayMs: Long = 1_000L,
        alarmId: Long = System.currentTimeMillis()
    ): Boolean {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_RECOVER_RING
            putExtra(EXTRA_RECOVERY_ALARM_ID, alarmId)
        }
        return armAlarmClock(
            context = context,
            finalTriggerTime = System.currentTimeMillis() + delayMs.coerceAtLeast(250L),
            requestCode = RECOVERY_REQUEST_CODE,
            triggerIntent = intent
        )
    }

    /** Cancels any pending swipe-recovery trigger (called on legitimate dismiss or snooze). */
    fun cancelRecoveryAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            alarmManager.cancel(
                PendingIntent.getBroadcast(
                    context,
                    RECOVERY_REQUEST_CODE,
                    Intent(context, AlarmReceiver::class.java).apply {
                        action = ACTION_RECOVER_RING
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } catch (_: Exception) {
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
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

        // A catch-up armed seconds ago must not survive an explicit cancel: the person said no.
        try {
            alarmManager.cancel(
                PendingIntent.getBroadcast(
                    context, CATCH_UP_REQUEST_CODE,
                    Intent(context, AlarmReceiver::class.java).apply {
                        action = AlarmReceiver.ACTION_TRIGGER_ALARM
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } catch (e: Exception) {
            // Ignore
        }

        cancelRecoveryAlarm(context)
        clearPrefs(context)
        AlarmBootMirror.clear(context)
    }

    /** True when the person armed the alarm and has not cancelled it since. */
    fun isArmed(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALARM_ARMED, false)

    fun getNextAlarmTime(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val v = prefs.getLong(KEY_NEXT_ALARM_TIME, -1L)
        return if (v == -1L) null else v
    }

    private fun savePrefs(context: Context, timeMillis: Long) {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formatted = fmt.format(Date(timeMillis))
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_NEXT_ALARM_TIME, timeMillis)
                .putString(KEY_NEXT_ALARM_FORMATTED, formatted)
                .putBoolean(KEY_ALARM_ARMED, true)
                .apply()
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
                // An explicit cancel must never be resurrected by the launch-time safety net.
                .putBoolean(KEY_ALARM_ARMED, false)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear alarm prefs", e)
        }
    }
}
