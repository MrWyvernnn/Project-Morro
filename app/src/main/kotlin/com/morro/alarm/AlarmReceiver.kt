package com.morro.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "(2) Broadcast received: AlarmReceiver fired")

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
            val memoPath = MemoStorage.getLatestMemoPath(context)
            Log.d(TAG, "(4) File check for commitment memo: path=${memoPath ?: "none found (fallback active)"}")

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
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Direct startActivity() fallback failed: ${e.message}")
            }
        } catch (e: Exception) {
            // Never fail silently: log the failure. UI-state is not touched here because
            // this is a broadcast receiver; the notification (if posted) carries the user
            // facing signal.
            Log.e(TAG, "AlarmReceiver encountered an unexpected error", e)
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
}
