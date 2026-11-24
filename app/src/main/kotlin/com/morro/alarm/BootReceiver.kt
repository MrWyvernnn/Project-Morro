package com.morro.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver: keeps the armed alarm alive across reboots, clock changes and app updates.
 *
 * - `LOCKED_BOOT_COMPLETED`: runs before the first unlock. Reads [AlarmBootMirror] instead of the
 *   normal prefs and re-arms without writing them. See the note below on what this does and does not
 *   buy.
 * - `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`: restore the stored absolute trigger if it is still in
 *   the future, otherwise recompute the next occurrence from the saved hour/minute.
 * - `TIME_SET` / `TIMEZONE_CHANGED`: the stored absolute trigger is now the wrong wall-clock time,
 *   so recompute it from the saved hour/minute.
 *
 * Every branch re-arms only what was armed. A cancelled alarm keeps its hour/minute for the picker
 * and must stay cancelled, which is why `isArmed` — not the presence of a saved time — decides.
 *
 * ## What the locked-boot branch actually achieves
 *
 * It re-arms the alarm, it does not make it ring before unlock. `AlarmReceiver`,
 * `AlarmForegroundService` and `AlarmActivity` are ordinary components: Android will not run them
 * before the first unlock, and an alarm broadcast aimed at them is deferred until then. So a phone
 * rebooted and left locked at 03:00 will have the alarm scheduled correctly and will ring — at
 * unlock, not at 07:00.
 *
 * Making it ring *at* 07:00 is a different and much larger change: mark the whole ring path
 * `directBootAware`, then audit every storage touch in it (mission choice, QR value, photo pool,
 * memo file) for credential-encrypted access, and give each one a documented answer. That work is
 * deliberately not started here. Calling the current behaviour "rings without unlocking" would be a
 * lie the 6am test would expose.
 */
class BootReceiver : BroadcastReceiver() {

    private companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Before the first unlock: normal prefs and files are unreadable here.
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> rearmBeforeFirstUnlock(context)

            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> rearmAfterUnlock(context)

            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> rescheduleForNewClock(context)
        }
    }

    /**
     * Re-arms from the device-protected mirror, never touching credential storage.
     *
     * The pending absolute trigger wins when it is still in the future, because that preserves a
     * snooze. Otherwise the next occurrence is computed from the mirrored hour/minute in the current
     * time zone.
     */
    private fun rearmBeforeFirstUnlock(context: Context) {
        if (!AlarmBootMirror.isArmed(context)) return

        val now = System.currentTimeMillis()
        val pending = AlarmBootMirror.nextTrigger(context)
        val trigger = if (pending != null && pending > now) {
            pending
        } else {
            AlarmBootMirror.savedTime(context)?.let { (hour, minute) ->
                AlarmScheduler.nextOccurrenceMillis(hour, minute)
            }
        }

        if (trigger == null) {
            // Armed with no mirrored time: the mirror was written by an older version, or the arm
            // failed before it could be copied. Recoverable on the next app open.
            Log.w(TAG, "Armed before first unlock but the mirror has no time to arm")
            return
        }

        if (AlarmScheduler.armWithoutPersisting(context, trigger)) {
            Log.i(TAG, "Re-armed before first unlock; the ring is deferred until the phone unlocks")
        } else {
            rearmFailed()
        }
    }

    private fun rearmAfterUnlock(context: Context) {
        if (!AlarmScheduler.isArmed(context)) return

        val nextAlarmTime = AlarmScheduler.getNextAlarmTime(context)
        if (nextAlarmTime != null && nextAlarmTime > System.currentTimeMillis()) {
            rearm { AlarmScheduler.scheduleAlarm(context, nextAlarmTime) }
        } else {
            // The stored trigger is gone or already past: the ring was lost to a reboot, not
            // cancelled, so the next occurrence is recomputed from the saved time.
            AlarmScheduler.getSavedAlarmTime(context)?.let { (hour, minute) ->
                rearm { AlarmScheduler.scheduleDailyAlarm(context, hour, minute) }
            }
        }
    }

    /**
     * A manual clock change or a flight moves the wall clock under a stored instant, so the alarm is
     * recomputed from the saved hour/minute rather than re-armed at the old epoch.
     */
    private fun rescheduleForNewClock(context: Context) {
        if (!AlarmScheduler.isArmed(context)) return
        AlarmScheduler.getSavedAlarmTime(context)?.let { (hour, minute) ->
            rearm { AlarmScheduler.scheduleDailyAlarm(context, hour, minute) }
        }
    }

    /**
     * Runs one re-arm attempt and says so when it fails.
     *
     * Exact-alarm access can be revoked between the ring and the reboot. That used to be a silent
     * no-op here, which left the person with no alarm and no trace of why.
     */
    private fun rearm(block: () -> Boolean) {
        if (!block()) rearmFailed()
    }

    private fun rearmFailed() {
        Log.w(TAG, "Could not re-arm the alarm; exact alarm access may be revoked")
    }
}
