package com.morro.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pure decision helper for swipe-from-Recents ring recovery.
 *
 * Returns `true` only when a ring is legitimately still in progress ([isRinging]) AND is not
 * already actively ringing on screen ([ringAlreadyActive]).
 */
fun recoveryNeeded(isRinging: Boolean, ringAlreadyActive: Boolean): Boolean =
    isRinging && !ringAlreadyActive

/**
 * AlarmForegroundService: Keeps the alarm process alive during ringing.
 *
 * - Runs with `android:stopWithTask="false"` and returns `START_REDELIVER_INTENT`.
 * - Persists `isAlarmRinging`, `alarmId`, and `ringFloorVolume` synchronously via `commit()`.
 * - Guards `STREAM_ALARM` with a monotonic 500ms floor watchdog (never raises above user floor).
 * - Schedules an idempotent recovery alarm on [onTaskRemoved] if swiped from Recents.
 *
 * Honest reliability limit: this survives a swipe from Recents. It does NOT survive a Force Stop
 * from Android Settings, which cancels every scheduled alarm across the app; [MainActivity]'s
 * launch-time stale-alarm re-arm recovers after a Force Stop.
 */
class AlarmForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "morro_alarm_service"
        const val NOTIFICATION_ID = 1002
        const val TAG = "AlarmForegroundService"
        const val ACTION_STOP_FLOOR_WATCHDOG = "com.morro.alarm.ACTION_STOP_FLOOR_WATCHDOG"
        const val EXTRA_ALARM_ID = "com.morro.alarm.EXTRA_ALARM_ID"

        private const val PREFS_NAME = "morro_prefs"
        private const val KEY_IS_ALARM_RINGING = "is_alarm_ringing"
        private const val KEY_RING_ALARM_ID = "ring_alarm_id"
        private const val KEY_RING_FLOOR_VOLUME = "ring_floor_volume"
        private const val KEY_RING_FLOOR_ROUTE = "ring_floor_route"
        private const val POLL_INTERVAL_MS = 500L

        @Volatile
        private var watchdogReleasedForCurrentRing = false

        fun isPersistedRingActive(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_IS_ALARM_RINGING, false)

        fun persistedRingAlarmId(context: Context): Long =
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_RING_ALARM_ID, 0L)

        /**
         * Signals that the wake-up gate has been solved so the volume floor watchdog stops polling
         * immediately and never writes afterwards. (Active ring state remains until dismissed/snoozed.)
         */
        fun stopFloorWatchdog(context: Context) {
            watchdogReleasedForCurrentRing = true
            try {
                context.startService(
                    Intent(context, AlarmForegroundService::class.java).apply {
                        action = ACTION_STOP_FLOOR_WATCHDOG
                    }
                )
            } catch (_: Exception) {
            }
        }

        /**
         * Clears persisted ring state synchronously (`commit()`), cancels any pending swipe-recovery
         * trigger, and stops the volume watchdog. Called ONLY on legitimate dismiss or snooze.
         */
        fun markRingDismissedOrSnoozed(context: Context) {
            watchdogReleasedForCurrentRing = true
            AlarmScheduler.cancelRecoveryAlarm(context)
            try {
                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_IS_ALARM_RINGING, false)
                    .remove(KEY_RING_ALARM_ID)
                    .remove(KEY_RING_FLOOR_VOLUME)
                    .remove(KEY_RING_FLOOR_ROUTE)
                    .commit()
            } catch (_: Exception) {
            }
            try {
                context.startService(
                    Intent(context, AlarmForegroundService::class.java).apply {
                        action = ACTION_STOP_FLOOR_WATCHDOG
                    }
                )
            } catch (_: Exception) {
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var notification: Notification? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchdogJob: Job? = null
    private var audioManager: AudioManager? = null
    private var deviceCallback: AudioDeviceCallback? = null

    @Volatile
    private var ringFloorVolume: Int = -1

    @Volatile
    private var lastRouteSignature: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_FLOOR_WATCHDOG) {
            cancelFloorWatchdog()
            return START_NOT_STICKY
        }

        // Promote to foreground immediately so the OS does not kill a re-delivered service.
        val notification = notification ?: buildNotification().also { notification = it }
        val promoted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to startForeground with mediaPlayback type, retrying plainly", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
                true
            } catch (retry: Exception) {
                Log.e(TAG, "Could not promote service to foreground", retry)
                false
            }
        }

        if (!promoted) {
            cancelFloorWatchdog()
            stopSelf()
            return START_NOT_STICKY
        }

        val incomingAlarmId = intent?.getLongExtra(EXTRA_ALARM_ID, 0L) ?: 0L
        val effectiveAlarmId = if (incomingAlarmId != 0L) {
            incomingAlarmId
        } else {
            persistedRingAlarmId(this).takeIf { it != 0L } ?: System.currentTimeMillis()
        }

        // Commit synchronously: a swipe-from-Recents can terminate the process immediately after.
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_ALARM_RINGING, true)
            .putLong(KEY_RING_ALARM_ID, effectiveAlarmId)
            .commit()

        watchdogReleasedForCurrentRing = false
        startFloorWatchdogIfNeeded()
        // START_REDELIVER_INTENT ensures Android restarts with the original alarm intent & extras.
        return START_REDELIVER_INTENT
    }

    /**
     * Triggered when the user swipes Morro from Recents.
     *
     * Honest limit: this survives a Recents swipe (arming a 1s recovery trigger on requestCode 1007
     * when `isAlarmRinging` is still true). It does NOT survive Force Stop from Settings, which
     * cancels all alarms; MainActivity's launch-time re-arm recovers after Force Stop.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val ringing = isPersistedRingActive(this)
        if (recoveryNeeded(isRinging = ringing, ringAlreadyActive = false)) {
            val alarmId = persistedRingAlarmId(this).takeIf { it != 0L } ?: System.currentTimeMillis()
            val scheduled = AlarmScheduler.scheduleRecoveryAlarm(this, delayMs = 1_000L, alarmId = alarmId)
            Log.w(TAG, "Task removed mid-ring (alarmId=$alarmId); recovery scheduled=$scheduled")
        }
    }

    override fun onDestroy() {
        cancelFloorWatchdog()
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Starts the 500ms monotonic `STREAM_ALARM` floor watchdog if not already running.
     *
     * Note on composition: this watchdog monitors and restores the system `STREAM_ALARM` index
     * (`AudioManager.getStreamVolume` / `setStreamVolume(..., 0)`). [GraceWindow] and
     * [GateSilenceController] attenuate only Morro's `MediaPlayer` gain (`player.setVolume(0.30f)`)
     * without changing the system stream index, so player ducking never triggers a floor restore
     * and the two defenses compose cleanly without fighting.
     *
     * Limitation (per-device awareness): `AudioManager.getStreamVolume(STREAM_ALARM)` only reports
     * the currently active output route. If headphones/Bluetooth connect mid-ring, [AudioDeviceCallback]
     * and route signature checks re-sample the active route's current volume as the new conservative
     * floor rather than forcing a loud speaker level onto a headset.
     */
    private fun startFloorWatchdogIfNeeded() {
        if (watchdogJob?.isActive == true || watchdogReleasedForCurrentRing) return
        val am = audioManager ?: return

        captureOrRestoreFloor(am)
        registerRouteCallback(am)

        watchdogJob = serviceScope.launch {
            try {
                var nextTickAt = SystemClock.elapsedRealtime() + POLL_INTERVAL_MS
                while (isActive && !watchdogReleasedForCurrentRing) {
                    val now = SystemClock.elapsedRealtime()
                    val waitMs = (nextTickAt - now).coerceAtLeast(0L)
                    if (waitMs > 0L) delay(waitMs)
                    if (!isActive || watchdogReleasedForCurrentRing) break

                    enforceFloorOnTick(am)

                    val after = SystemClock.elapsedRealtime()
                    nextTickAt += POLL_INTERVAL_MS
                    if (nextTickAt <= after) nextTickAt = after + POLL_INTERVAL_MS
                }
            } finally {
                unregisterRouteCallback()
            }
        }
    }

    private fun captureOrRestoreFloor(am: AudioManager) {
        val currentRoute = AlarmVolumeGuard.outputRouteSignature(am)
        val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val persistedFloor = prefs.getInt(KEY_RING_FLOOR_VOLUME, -1)
        val persistedRoute = prefs.getString(KEY_RING_FLOOR_ROUTE, null)

        val chosenFloor = if (persistedFloor >= 0 && persistedRoute == currentRoute) {
            persistedFloor.coerceIn(0, max)
        } else {
            current.coerceIn(0, max)
        }

        ringFloorVolume = chosenFloor
        lastRouteSignature = currentRoute
        prefs.edit()
            .putInt(KEY_RING_FLOOR_VOLUME, chosenFloor)
            .putString(KEY_RING_FLOOR_ROUTE, currentRoute)
            .commit()
    }

    private fun handleRouteChanged(am: AudioManager) {
        val newRoute = AlarmVolumeGuard.outputRouteSignature(am)
        val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val safeFloor = current.coerceIn(0, max)
        ringFloorVolume = safeFloor
        lastRouteSignature = newRoute
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RING_FLOOR_VOLUME, safeFloor)
            .putString(KEY_RING_FLOOR_ROUTE, newRoute)
            .commit()
        Log.d(TAG, "Audio route changed; re-sampled conservative alarm floor=$safeFloor/$max")
    }

    private fun enforceFloorOnTick(am: AudioManager) {
        if (watchdogReleasedForCurrentRing) return
        val currentRoute = AlarmVolumeGuard.outputRouteSignature(am)
        if (currentRoute != lastRouteSignature) {
            handleRouteChanged(am)
            return
        }
        val current = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val target = floorRestoreTarget(current = current, floor = ringFloorVolume, max = max)
        if (target != null) {
            try {
                am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore STREAM_ALARM floor to $target", e)
            }
        }
    }

    private fun registerRouteCallback(am: AudioManager) {
        if (deviceCallback != null) return
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (addedDevices.any { it.isSink } && !watchdogReleasedForCurrentRing) {
                    handleRouteChanged(am)
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (removedDevices.any { it.isSink } && !watchdogReleasedForCurrentRing) {
                    handleRouteChanged(am)
                }
            }
        }
        deviceCallback = callback
        am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun unregisterRouteCallback() {
        val cb = deviceCallback ?: return
        deviceCallback = null
        try {
            audioManager?.unregisterAudioDeviceCallback(cb)
        } catch (_: Exception) {
        }
    }

    private fun cancelFloorWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
        unregisterRouteCallback()
        ringFloorVolume = -1
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setBypassDnd(true)
                description = "Keeps the alarm alive during ringing"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            2002,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Morro Alarm")
            .setContentText("Alarm is active")
            .setSmallIcon(R.drawable.ic_morro_notification)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "morro:alarm_wakelock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10-minute hard cap
        }
        Log.d(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WakeLock: ${e.message}")
        }
        wakeLock = null
    }
}
