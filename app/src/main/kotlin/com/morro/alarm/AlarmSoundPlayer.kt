package com.morro.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.util.Log

/**
 * AlarmSoundPlayer: Handles playing the alarm sound with USAGE_ALARM stream.
 *
 * Uses MediaPlayer with AudioAttributes.USAGE_ALARM so the sound is routed
 * to the alarm stream and is audible even when the ringer is silenced.
 *
 * Includes fallback to system alarm tone if no custom sound is set.
 */
object AlarmSoundPlayer {

    private const val TAG = "AlarmSoundPlayer"

    private val AUDIO_USAGE = AudioAttributes.USAGE_ALARM
    private val CONTENT_TYPE = AudioAttributes.CONTENT_TYPE_SONIFICATION

    private val audioAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AUDIO_USAGE)
            .setContentType(CONTENT_TYPE)
            .build()

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Starts playing the alarm sound using prepareAsync().
     *
     * @param context Application context
     */
    @Synchronized
    fun start(context: Context, onError: ((String) -> Unit)? = null) {
        stopAndReleasePlayer()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(context, getAlarmSoundUri(context))
                isLooping = true
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                    stopAndReleasePlayer()
                    onError?.invoke("Alarm audio error code $what")
                    true
                }
                setOnPreparedListener { mp ->
                    try {
                        mp.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting alarm MediaPlayer on prepared", e)
                        stopAndReleasePlayer()
                        onError?.invoke("Could not start alarm audio")
                    }
                }
                prepareAsync()
            }
            Log.d(TAG, "Alarm sound prepareAsync called")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start alarm sound", e)
            stopAndReleasePlayer()
            onError?.invoke(e.message ?: "Could not start alarm sound")
        }
    }

    /**
     * Gets the URI for the alarm sound, falling back to system default if none is set.
     */
    private fun getAlarmSoundUri(context: Context): Uri {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }

    /**
     * Sets the volume of the playing alarm sound (clamped between 0.0 and 1.0).
     */
    fun setVolume(volume: Float) {
        try {
            val clamped = volume.coerceIn(0f, 1f)
            mediaPlayer?.setVolume(clamped, clamped)
        } catch (e: Exception) {
            Log.w(TAG, "Error setting volume", e)
        }
    }

    /**
     * Stops and releases the MediaPlayer cleanly.
     */
    @Synchronized
    fun stopAndReleasePlayer() {
        try {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) player.stop()
                } catch (ignored: Exception) {}
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping and releasing MediaPlayer", e)
        }
        mediaPlayer = null
    }

    fun stop() = stopAndReleasePlayer()
    fun release() = stopAndReleasePlayer()

    /**
     * Plays the fallback system alarm tone using prepareAsync().
     *
     * @param context Application context
     */
    @Synchronized
    fun playFallbackAlarmTone(context: Context, onCompletion: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        stopAndReleasePlayer()

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                setOnCompletionListener {
                    Log.d(TAG, "Fallback alarm tone completed")
                    stopAndReleasePlayer()
                    onCompletion?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Fallback alarm error what=$what extra=$extra")
                    stopAndReleasePlayer()
                    onError?.invoke("Fallback alarm error code $what")
                    true
                }
                setOnPreparedListener { mp ->
                    try {
                        mp.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting fallback alarm on prepared", e)
                        stopAndReleasePlayer()
                        onError?.invoke("Could not start fallback alarm")
                    }
                }
                prepareAsync()
            }
            Log.d(TAG, "Fallback alarm tone prepareAsync called")
        } catch (e: Exception) {
            Log.e(TAG, "Could not play fallback alarm tone", e)
            stopAndReleasePlayer()
            onError?.invoke(e.message ?: "Could not play fallback alarm tone")
        }
    }
}

/**
 * Production alias matching architectural specification.
 */
typealias AlarmAudioPlayer = AlarmSoundPlayer
typealias AlarmRingingActivity = AlarmActivity
