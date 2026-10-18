package com.morro.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager

/**
 * AlarmSoundPlayer: Plays alarm sound on the alarm stream.
 * Uses MediaPlayer with USAGE_ALARM so it plays at alarm volume, not notification volume.
 * Singleton to ensure only one instance plays at a time.
 */
object AlarmSoundPlayer {

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Start playing alarm sound.
     * Uses the system default alarm sound on the alarm audio stream.
     */
    fun start(context: Context) {
        // Stop any existing playback
        stop()

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)

                // CRITICAL: Use USAGE_ALARM so sound plays on alarm stream, not notification stream
                // This ensures it plays at alarm volume and respects Do Not Disturb settings for alarms
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )

                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Log error but don't crash - visual alarm still works
            e.printStackTrace()
        }
    }

    /**
     * Stop alarm sound and release MediaPlayer resources
     */
    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }
}
