package com.morro.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * AlarmSoundPlayer: Plays alarm sound + vibration on alarm stream.
 * Life-cycle is managed by AlarmActivity (start on show, stop on dismiss).
 * Fallback chain: TYPE_ALARM -> TYPE_RINGTONE -> TYPE_NOTIFICATION.
 * Always tries to vibrate so alarm is not silent.
 */
object AlarmSoundPlayer {

    private const val TAG = "AlarmSoundPlayer"
    private val VIBRATE_PATTERN = longArrayOf(0, 800, 400, 800)

    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null

    private val alarmAudioAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    /** Called by AlarmActivity onCreate / onResume */
    fun start(context: Context) {
        stop() // clean any leftover
        requestAudioFocus(context)
        startVibration(context)
        tryPlay(context)
    }

    /** Stops everything; safe to call repeatedly */
    fun stop() {
        releasePlayer()
        abandonAudioFocus()
        stopVibration()
    }

    private fun tryPlay(context: Context) {
        val candidates = listOfNotNull(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
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
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed $uri", e)
                releasePlayer()
            }
        }
        Log.e(TAG, "All alarm URIs failed; alarm will rely on vibration only.")
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            try { mediaPlayer?.release() } catch (_: Exception) {}
            Log.w(TAG, "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
    }

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

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        val req = audioFocusRequest ?: return
        try { am.abandonAudioFocusRequest(req) } catch (_: Exception) {}
        audioManager = null
        audioFocusRequest = null
    }

    private fun startVibration(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        try {
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(VIBRATE_PATTERN, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(VIBRATE_PATTERN, 0) // 0 = repeat indefinitely
                }
                Log.d(TAG, "Vibration started")
            } ?: Log.w(TAG, "No vibrator service")
        } catch (e: Exception) {
            Log.w(TAG, "Vibration start failed", e)
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Vibration cancel failed", e)
        }
        vibrator = null
    }
}
