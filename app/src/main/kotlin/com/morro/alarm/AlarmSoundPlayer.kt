package com.morro.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.util.Log

/**
 * AlarmSoundPlayer: Plays alarm sound on the alarm stream.
 * Uses MediaPlayer with USAGE_ALARM so it plays at alarm volume, not notification volume.
 * Singleton to ensure only one instance plays at a time.
 *
 * Fallback chain: system alarm tone → system notification tone → built-in fallback.
 * If all URIs fail to load, the alarm still fires visually — it never crashes.
 */
object AlarmSoundPlayer {

    private const val TAG = "AlarmSoundPlayer"

    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null

    /** Audio attributes shared by the player and the focus request. */
    private val alarmAudioAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    /**
     * Start playing alarm sound.
     * Requests audio focus, then tries the system alarm tone.
     * Falls back to notification tone, then to the platform default ringtone.
     */
    fun start(context: Context) {
        // Stop any existing playback and release resources first
        stop()

        requestAudioFocus(context)

        // Build ordered list of candidate URIs; skip nulls
        val candidates = listOfNotNull(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        )

        for (uri in candidates) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
                    setAudioAttributes(alarmAudioAttributes)
                    isLooping = true
                    prepare()
                    start()
                }
                Log.d(TAG, "Playing alarm sound: $uri")
                return // success — stop trying
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play $uri, trying next fallback", e)
                // Release the failed player before trying the next URI
                releasePlayer()
            }
        }

        // Every URI failed (extremely rare — device has no tones at all)
        Log.e(TAG, "All alarm sound URIs failed. Alarm will fire visually only.")
    }

    /**
     * Stop alarm sound, release MediaPlayer, and abandon audio focus.
     * Safe to call even if nothing is playing.
     */
    fun stop() {
        releasePlayer()
        abandonAudioFocus()
    }

    // ── Private helpers ──────────────────────────────────────────────

    /** Release the MediaPlayer safely, handling every state. */
    private fun releasePlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            // MediaPlayer can throw IllegalStateException if in an error state.
            // Always release even if stop() threw.
            try { mediaPlayer?.release() } catch (_: Exception) {}
            Log.w(TAG, "Error stopping MediaPlayer", e)
        }
        mediaPlayer = null
    }

    /**
     * Request transient audio focus on the alarm stream.
     * This ducks or pauses other apps (music, podcasts) while the alarm plays.
     */
    private fun requestAudioFocus(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(alarmAudioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { /* alarm keeps playing regardless */ }
            .build()

        am.requestAudioFocus(request)
        audioManager = am
        audioFocusRequest = request
    }

    /** Abandon audio focus so other apps can resume. */
    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        val request = audioFocusRequest ?: return
        try {
            am.abandonAudioFocusRequest(request)
        } catch (_: Exception) {}
        audioManager = null
        audioFocusRequest = null
    }
}
