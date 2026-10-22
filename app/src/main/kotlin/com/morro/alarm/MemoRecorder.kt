package com.morro.alarm

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * MemoRecorder: Records the user's voice memo into app-private storage.
 *
 * The clip is written to `temp_memo.m4a` (see [MemoStorage]) and is capped at
 * [MAX_DURATION_MS] so a forgotten recording cannot run forever.
 *
 * MediaRecorder is a blocking, single-instance resource, so this is a plain
 * `object` with explicit start / stop / cancel. All callbacks are delivered on
 * the main thread, which keeps them safe to use with Compose state.
 */
object MemoRecorder {

    private const val TAG = "MemoRecorder"

    /** Hard cap on a single recording. */
    const val MAX_DURATION_MS = 15_000

    private val OUTPUT_FORMAT = MediaRecorder.OutputFormat.MPEG_4
    private val AUDIO_ENCODER = MediaRecorder.AudioEncoder.AAC
    private val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
    private val SAMPLE_RATE = 44_100
    private val BIT_RATE = 96_000

    private var recorder: MediaRecorder? = null

    /** True once the recorder has auto-stopped because it hit the duration cap. */
    private var autoStopped = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Starts recording into the temp file.
     *
     * @param onError called with a human-readable reason if recording fails.
     * @param onMaxDurationReached called when the clip is auto-stopped at the cap.
     */
    fun startRecording(
        context: Context,
        onError: (String) -> Unit,
        onMaxDurationReached: () -> Unit
    ) {
        if (isRecording()) {
            onError("A recording is already in progress")
            return
        }

        // Start from a clean slate so a failed run cannot leave a stale clip behind.
        MemoStorage.clearTemp(context)
        val outputPath = MemoStorage.getTempPath(context)

        try {
            val mr = createRecorder(context)
            recorder = mr
            autoStopped = false

            mr.setAudioSource(AUDIO_SOURCE)
            mr.setOutputFormat(OUTPUT_FORMAT)
            mr.setAudioEncoder(AUDIO_ENCODER)
            mr.setAudioSamplingRate(SAMPLE_RATE)
            mr.setAudioEncodingBitRate(BIT_RATE)
            mr.setOutputFile(outputPath)

            // Enforce the cap: MediaRecorder stops itself and fires the info listener.
            mr.setMaxDuration(MAX_DURATION_MS)

            mr.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    autoStopped = true
                    // Release the recorder outside of MediaRecorder's own callback,
                    // then let the UI know the clip is ready.
                    mainHandler.post {
                        releaseRecorder()
                        onMaxDurationReached()
                    }
                }
            }

            mr.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaRecorder error what=$what extra=$extra")
                releaseRecorder()
                onError("Recording failed (code $what)")
            }

            mr.prepare()
            mr.start()
            Log.d(TAG, "Recording started -> $outputPath")
        } catch (e: Exception) {
            Log.e(TAG, "Could not start recording", e)
            releaseRecorder()
            MemoStorage.clearTemp(context)
            onError(e.message ?: "Could not start recording")
        }
    }

    /**
     * Stops the current recording and keeps the clip.
     * @return true when a clip was successfully finalised.
     */
    fun stopRecording(context: Context): Boolean {
        val mr = recorder
        if (mr == null) return false

        return try {
            if (!autoStopped) {
                // stop() finalises the file; release() alone would leave it truncated.
                mr.stop()
            }
            mr.release()
            recorder = null
            autoStopped = false
            Log.d(TAG, "Recording stopped")
            true
        } catch (e: Exception) {
            // stop() throws if too little audio was captured - the file is unusable.
            Log.e(TAG, "Could not stop recording cleanly", e)
            releaseRecorder()
            MemoStorage.clearTemp(context)
            false
        }
    }

    /** Aborts the recording and deletes the temp file. The saved memo is untouched. */
    fun cancelRecording(context: Context) {
        val mr = recorder
        if (mr != null) {
            try {
                if (!autoStopped) mr.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Ignoring error while cancelling", e)
            }
            releaseRecorder()
        }
        autoStopped = false
        MemoStorage.clearTemp(context)
        Log.d(TAG, "Recording cancelled")
    }

    /** True while a recording is in progress. */
    fun isRecording(): Boolean = recorder != null

    /** Releases the underlying MediaRecorder without touching the output file. */
    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaRecorder", e)
        }
        // `autoStopped` is intentionally left alone here: stopRecording() owns it,
        // so a clip finalized by the max-duration cap still counts as a good recording.
        recorder = null
    }

    /**
     * API 31+ requires a Context for MediaRecorder so it can scope the audio
     * device routing. Older devices use the deprecated no-arg constructor.
     */
    @Suppress("DEPRECATION")
    private fun createRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
}
