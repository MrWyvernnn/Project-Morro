package com.morro.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
     * True between prepareAsync() and the first frame of sound.
     *
     * `isPlaying()` is false in that window, which made a caller asking "is it already ringing?"
     * start a second player on top of a source that was about to play.
     */
    @Volatile
    private var preparing = false

    /**
     * Plays the alarm, walking a fallback chain of system sound URIs.
     *
     * A single TYPE_ALARM URI is not guaranteed to be playable: it can be null when the person has
     * set the alarm sound to "None", and some OEMs hand back a URI whose provider cannot open.
     * The old code caught that and gave up, which is the one fatal outcome for this app — a silent
     * alarm. Each candidate is opened with the same attributes, so routing is unchanged.
     */
    @Synchronized
    fun start(context: Context, onError: ((String) -> Unit)? = null) {
        stopAndReleasePlayer()
        val appContext = context.applicationContext
        var lastFailure: String? = null

        for (uri in alarmSoundUris()) {
            try {
                val player = MediaPlayer()
                player.setAudioAttributes(audioAttributes)
                player.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
                player.setDataSource(appContext, uri)
                player.isLooping = true
                player.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                    stopAndReleasePlayer()
                    onError?.invoke("Alarm audio error code $what")
                    true
                }
                player.setOnPreparedListener { mp ->
                    preparing = false
                    try {
                        val vol = currentVolume
                        mp.setVolume(vol, vol)
                        mp.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting alarm MediaPlayer on prepared", e)
                        stopAndReleasePlayer()
                        onError?.invoke("Could not start alarm audio")
                    }
                }
                // Handed over before prepareAsync(): preparation reports its first error on the
                // main thread, and that listener has to be able to release this exact instance.
                mediaPlayer = player
                preparing = true
                player.prepareAsync()
                Log.d(TAG, "Alarm sound prepareAsync called for $uri")
                return
            } catch (e: Exception) {
                lastFailure = e.message
                Log.w(TAG, "Alarm sound source unusable, trying the next: $uri", e)
                stopAndReleasePlayer()
            }
        }

        Log.e(TAG, "No playable alarm sound source; last failure: $lastFailure")
        onError?.invoke(lastFailure ?: "Could not start alarm sound")
    }

    /** Playable system sound URIs, most alarm-like first, distinct and non-null. */
    private fun alarmSoundUris(): List<Uri> = listOfNotNull(
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        Settings.System.DEFAULT_ALARM_ALERT_URI
    ).distinct()

    @Volatile
    private var currentVolume = 1.0f

    /**
     * Sets the volume of the playing alarm sound (clamped between 0.0 and 1.0).
     */
    @Synchronized
    fun setVolume(volume: Float) {
        if (volume.isNaN()) return
        try {
            val clamped = volume.coerceIn(0f, 1f)
            currentVolume = clamped
            mediaPlayer?.setVolume(clamped, clamped)
        } catch (e: Exception) {
            Log.w(TAG, "Error setting volume", e)
        }
    }

    /**
     * Returns the current volume applied to the player (between 0.0 and 1.0).
     */
    fun getVolume(): Float = currentVolume

    /**
     * Stops and releases the MediaPlayer cleanly.
     */
    @Synchronized
    fun stopAndReleasePlayer() {
        preparing = false
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

    /**
     * True while a tone is live: either preparing or actually sounding.
     *
     * The distinction matters to callers that avoid restarting a tone that is already on its way —
     * a source still being prepared is not silent, it is about to play, and restarting it there
     * throws away the preparation and delays the sound.
     */
    fun isPlaying(): Boolean = try {
        mediaPlayer?.let { it.isPlaying || preparing } == true
    } catch (e: Exception) {
        false
    }

    fun stop() = stopAndReleasePlayer()
    fun release() = stopAndReleasePlayer()

    /**
     * Plays one short system alarm tone, walking the same fallback chain as [start].
     *
     * @param context Application context
     */
    @Synchronized
    fun playFallbackAlarmTone(
        context: Context,
        onCompletion: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        stopAndReleasePlayer()

        val appContext = context.applicationContext
        var fallbackFailure: String? = null
        for (uri in alarmSoundUris()) {
            try {
                val player = MediaPlayer()
                player.setAudioAttributes(audioAttributes)
                player.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
                player.setDataSource(appContext, uri)
                player.setOnCompletionListener {
                    Log.d(TAG, "Fallback alarm tone completed")
                    stopAndReleasePlayer()
                    onCompletion?.invoke()
                }
                player.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Fallback alarm error what=$what extra=$extra")
                    stopAndReleasePlayer()
                    onError?.invoke("Fallback alarm error code $what")
                    true
                }
                player.setOnPreparedListener { mp ->
                    preparing = false
                    try {
                        currentVolume = 1.0f
                        mp.setVolume(1.0f, 1.0f)
                        mp.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting fallback alarm on prepared", e)
                        stopAndReleasePlayer()
                        onError?.invoke("Could not start fallback alarm")
                    }
                }
                mediaPlayer = player
                preparing = true
                player.prepareAsync()
                Log.d(TAG, "Fallback alarm tone prepareAsync called for $uri")
                return
            } catch (e: Exception) {
                fallbackFailure = e.message
                Log.w(TAG, "Fallback tone source unusable, trying the next: $uri", e)
                stopAndReleasePlayer()
            }
        }
        Log.e(TAG, "No playable fallback tone; last failure: $fallbackFailure")
        onError?.invoke(fallbackFailure ?: "Could not play fallback alarm tone")
    }
}

/**
 * Production alias matching architectural specification.
 */
