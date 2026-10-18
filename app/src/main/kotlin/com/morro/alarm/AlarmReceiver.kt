package com.morro.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * AlarmReceiver: Receives alarm broadcast from AlarmManager.
 * Launches AlarmActivity (full-screen over lock screen) and starts alarm sound.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Start alarm sound
        AlarmSoundPlayer.start(context)

        // Launch full-screen alarm activity
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(alarmIntent)
    }
}
