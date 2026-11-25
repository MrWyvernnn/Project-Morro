package com.morro.alarm

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * AlarmForegroundService: Keeps the alarm process alive during ringing.
 * Acquires a partial wake lock and runs as a foreground service with a notification.
 * This defense layer prevents the OS from killing the alarm mid-ring on aggressive OEM ROMs.
 */
class AlarmForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "morro_alarm_service"
        const val NOTIFICATION_ID = 1002
        const val TAG = "AlarmForegroundService"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var notification: Notification? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A ring can start this service more than once (notification repost, OEM retry). The
        // notification and the PendingIntent are identical every time, so they are built once.
        val notification = notification ?: buildNotification().also { notification = it }
        // A failed promotion must not crash the process: without a foreground notification the
        // service has no legal reason to live, so stop it instead of holding a wake lock.
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
            stopSelf()
            return START_NOT_STICKY
        }
        // START_NOT_STICKY: a restart arrives with a null intent and nothing to ring for.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
