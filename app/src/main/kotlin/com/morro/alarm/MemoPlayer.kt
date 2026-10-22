package com.morro.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * MemoPlayer: Plays a recorded memo clip.
 *
 * Audio attributes are `USAGE_ALARM` so the clip is routed to the alarm stream
 * and is audible even when the ringer is silenced. `CONTENT_TYPE_SPEECH`
 * matches the content (the user speaking).
 *
 * Playback is deliberately linear: there is no seek and no skip. The clip runs
 * to the end and then reports completion, which is what the alarm gate needs.
 */
object MemoPlayer {

    private const val TAG = "MemoPlayer"

    private val AUDIO_USAGE = AudioAttributes.USAGE_ALARM
    private val CONTENT_TYPE = AudioAttributes.CONTENT_TYPE_SPEECH

    private val audioAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AUDIO_USAGE)
            .setContentType(CONTENT_TYPE)
            .build()

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Plays the clip at [filePath] to the end.
     *
     * @param onCompletion called when the clip finishes on its own.
     * @param onError called with a human-readable reason if playback fails.
     */
    fun startPlayback(
        context: Context,
        filePath: String,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ) {
        stopPlayback()

        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            onError("Memo file is missing")
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    releasePlayer()
                    onCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                    releasePlayer()
                    onError("Playback failed (code $what)")
                    true
                }
                prepare()
                start()
            }
            Log.d(TAG, "Playing memo: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start playback", e)
            releasePlayer()
            onError(e.message ?: "Could not play memo")
        }
    }

    /** Stops playback and releases the player. Safe to call repeatedly. */
    fun stopPlayback() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping playback", e)
            try { mediaPlayer?.release() } catch (_: Exception) { }
        }
        mediaPlayer = null
    }

    /** True while a clip is playing. */
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    private fun releasePlayer() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
    }
}
