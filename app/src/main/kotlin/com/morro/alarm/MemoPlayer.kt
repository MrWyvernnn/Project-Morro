package com.morro.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MemoPlayer: Plays a recorded memo clip.
 *
 * Audio attributes are `USAGE_ALARM` so the clip is routed to the alarm stream
 * and is audible even when the ringer is silenced. `CONTENT_TYPE_SPEECH`
 * matches the content (the user speaking).
 *
 * Playback is deliberately linear: there is no seek and no skip. The clip runs
 * to the end and then reports completion, which is what the alarm gate needs.
 *
 * Uses non-blocking prepareAsync() with OnPreparedListener to prevent UI thread lockups.
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
    private var pendingCompletion: (() -> Unit)? = null
    private var pendingError: ((String) -> Unit)? = null

    /**
     * Reactive state flow for playback state. Compose can collect this via
     * `collectAsStateWithLifecycle()` so UI survives rotation and stays in sync
     * with the active hardware state even after the Activity is recreated.
     */
    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isPlayingFlow.asStateFlow()

    /**
     * Syncs the internal playing state with the reactive flow.
     * Called whenever MediaPlayer is created or released.
     */
    private fun syncPlayingFlow() {
        _isPlayingFlow.value = try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Plays the clip at [filePath] to the end.
     *
     * @param onCompletion called when the clip finishes on its own.
     * @param onError called with a human-readable reason if playback fails.
     */
    @Synchronized
    fun startPlayback(
        context: Context,
        filePath: String,
        onCompletion: () -> Unit,
        onError: (String) -> Unit
    ) {
        stopAndReleasePlayer()

        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            onError("Memo file is missing")
            return
        }

        pendingCompletion = onCompletion
        pendingError = onError

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    val completion = pendingCompletion
                    stopAndReleasePlayer()
                    completion?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                    val error = pendingError
                    stopAndReleasePlayer()
                    error?.invoke("Playback failed (code $what)")
                    true
                }
                setOnPreparedListener { mp ->
                    Log.d(TAG, "MediaPlayer prepared asynchronously, starting playback")
                    try {
                        mp.start()
                        syncPlayingFlow()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting MediaPlayer on prepared", e)
                        stopAndReleasePlayer()
                        onError("Could not start playback: ${e.message}")
                    }
                }
                prepareAsync()
            }
            Log.d(TAG, "prepareAsync called for memo: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start playback", e)
            stopAndReleasePlayer()
            onError(e.message ?: "Could not play memo")
        }
    }

    /** Stops playback and releases the player. Safe to call repeatedly. */
    @Synchronized
    fun stopAndReleasePlayer() {
        pendingCompletion = null
        pendingError = null
        try {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) player.stop()
                } catch (ignored: Exception) {}
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping and releasing player", e)
        }
        mediaPlayer = null
        syncPlayingFlow()
    }

    fun stopPlayback() = stopAndReleasePlayer()

    /** Releases the underlying MediaPlayer and nullifies the instance. */
    fun release() = stopAndReleasePlayer()

    /** True while a clip is playing. */
    fun isPlaying(): Boolean = try {
        mediaPlayer?.isPlaying == true
    } catch (e: Exception) {
        false
    }
}
