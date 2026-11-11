package com.morro.alarm

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MemoRecorder: Records the user's voice memo into app-private storage.
 *
 * The clip is written to `temp_memo.m4a` (see [MemoStorage]) and is capped at
 * [MAX_DURATION_MS] so a forgotten recording cannot run forever.
 *
 * MediaRecorder is a blocking, single-instance resource, so this is a plain
 * `object` with guarded, synchronized teardown. All callbacks are
 * delivered on the main thread, which keeps them safe to use with Compose state.
 */
object MemoRecorder {

    private const val TAG = "MemoRecorder"

    /** Hard cap on a single recording in milliseconds (15s). */
    const val MAX_DURATION_MS = 15_000

    private val OUTPUT_FORMAT = MediaRecorder.OutputFormat.MPEG_4
    private val AUDIO_ENCODER = MediaRecorder.AudioEncoder.AAC
    private val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
    private const val SAMPLE_RATE = 16_000      // 16 kHz speech optimal
    private const val CHANNELS = 1              // Mono
    private const val BIT_RATE = 28_000         // ~28 kbps compact speech

    private var recorder: MediaRecorder? = null
    private var activeAppContext: Context? = null

    /**
     * Reactive state flow for recording state. Compose can collect this via
     * `collectAsStateWithLifecycle()` so UI survives rotation and stays in sync
     * with the active hardware state even after the Activity is recreated.
     */
    private val _isRecordingFlow = MutableStateFlow(false)
    val isRecordingFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRecordingFlow.asStateFlow()

    /**
     * Syncs the internal `recordingActive` flag with the reactive flow.
     * Called from every state transition so Compose UI stays in sync.
     */
    private fun syncRecordingFlow() {
        _isRecordingFlow.value = recordingActive
    }

    /**
     * Internal flag to track recording state for race condition protection.
     * Prevents double-stop crashes when stop() is called multiple times.
     */
    @Volatile
    private var recordingActive = false

    /**
     * True once the recorder has auto-stopped because it hit the duration cap.
     * Read/written only on the main thread (all callbacks run there).
     */
    @Volatile
    var autoStopped = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var monotonicTimerRunnable: Runnable? = null
    private var startRealtime: Long = 0L

    /**
     * Single guarded teardown helper (Fix Double-Stop Crash Race & Mic Lock).
     *
     * Synchronized so only ONE caller (Stop button, monotonic countdown zero,
     * native MEDIA_RECORDER_INFO_MAX_DURATION_REACHED, Cancel, or Error handler)
     * can execute teardown at a time.
     *
     * @param context Context used to clear temp file if needed.
     * @param deleteTemp true if the temp recording file should be deleted immediately.
     * @return true if a valid recording was saved and deleteTemp is false.
     */
    @Synchronized
    private fun teardownRecorder(context: Context?, deleteTemp: Boolean): Boolean {
        if (!recordingActive && recorder == null) {
            return false
        }
        cancelMonotonicTimer()
        val ctx = context?.applicationContext ?: activeAppContext
        var stoppedCleanly = false

        try {
            recorder?.let { mr ->
                try {
                    mr.stop()
                    stoppedCleanly = true
                } catch (e: RuntimeException) {
                    // Swallow stop-when-not-recording race condition
                    Log.w(TAG, "Swallowed RuntimeException during recorder.stop()", e)
                }
            }
        } finally {
            try {
                recorder?.reset()
            } catch (e: Exception) {
                Log.w(TAG, "Error resetting recorder", e)
            }
            try {
                recorder?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing recorder", e)
            }
            recorder = null
            recordingActive = false
            syncRecordingFlow()
        }

        if (deleteTemp && ctx != null) {
            MemoStorage.clearTemp(ctx)
        }

        return if (deleteTemp) {
            false
        } else {
            stoppedCleanly || (ctx != null && MemoStorage.hasTempMemo(ctx))
        }
    }

    /**
     * Starts recording into the temp file with strict speech encoding and monotonic timer.
     *
     * @param onError called with a human-readable reason if recording fails.
     * @param onMaxDurationReached called when the clip is auto-stopped at the 15s cap.
     */
    @Synchronized
    fun startRecording(
        context: Context,
        onError: (String) -> Unit,
        onMaxDurationReached: () -> Unit
    ) {
        if (recordingActive) {
            onError("A recording is already in progress")
            return
        }

        val appCtx = context.applicationContext
        activeAppContext = appCtx

        // Start from a clean slate so a failed run cannot leave a stale clip behind.
        MemoStorage.clearTemp(appCtx)
        val outputPath = MemoStorage.getTempPath(appCtx)

        try {
            val mr = createRecorder(appCtx)
            recorder = mr
            autoStopped = false
            recordingActive = true
            syncRecordingFlow()

            // 1.3 Speech Encoder Optimization & Strict setMaxDuration Ordering
            mr.setAudioSource(AUDIO_SOURCE)
            mr.setOutputFormat(OUTPUT_FORMAT)
            mr.setAudioEncoder(AUDIO_ENCODER)
            mr.setAudioSamplingRate(SAMPLE_RATE)
            mr.setAudioChannels(CHANNELS)
            mr.setAudioEncodingBitRate(BIT_RATE)
            mr.setMaxDuration(MAX_DURATION_MS) // MUST be set before prepare()
            mr.setOutputFile(outputPath)

            mr.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    Log.d(TAG, "MEDIA_RECORDER_INFO_MAX_DURATION_REACHED triggered")
                    autoStopped = true
                    teardownRecorder(appCtx, deleteTemp = false)
                    mainHandler.post { onMaxDurationReached() }
                }
            }

            mr.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaRecorder error what=$what extra=$extra")
                teardownRecorder(appCtx, deleteTemp = true)
                mainHandler.post {
                    onError("Microphone unavailable — check if another app is using audio")
                }
            }

            mr.prepare()
            mr.start()
            Log.d(TAG, "Recording started -> $outputPath")

            // 1.4 Drift-Free Monotonic Countdown Timer
            val nowRealtime = SystemClock.elapsedRealtime()
            startMonotonicTimer(nowRealtime, appCtx, onMaxDurationReached)

        } catch (e: Exception) {
            Log.e(TAG, "Could not start recording", e)
            teardownRecorder(appCtx, deleteTemp = true)
            onError("Microphone unavailable — check if another app is using audio")
        }
    }

    /**
     * Stops the current recording and keeps the clip.
     * @return true when a clip was successfully finalised.
     */
    @Synchronized
    fun stopRecording(context: Context): Boolean {
        return teardownRecorder(context, deleteTemp = false)
    }

    /** Aborts the recording and deletes the temp file. The saved memo is untouched. */
    @Synchronized
    fun cancelRecording(context: Context) {
        teardownRecorder(context, deleteTemp = true)
        Log.d(TAG, "Recording cancelled and temp cleared")
    }

    /** Releases the underlying MediaRecorder and nullifies the instance. */
    @Synchronized
    fun release(context: Context? = null) {
        teardownRecorder(context, deleteTemp = false)
    }

    /** True while a recording is in progress. */
    fun isRecording(): Boolean = recordingActive

    /**
     * Public alias callable from Compose's remember { } block.
     * Identical to [isRecording] — exists because Kotlin doesn't allow calling a
     * function with parentheses directly inside mutableStateOf.
     */
    fun isCurrentlyRecording(): Boolean = recordingActive

    /** Returns remaining milliseconds according to monotonic clock. */
    fun getRemainingMs(): Long {
        if (!recordingActive) return 0L
        val elapsed = SystemClock.elapsedRealtime() - startRealtime
        return (MAX_DURATION_MS.toLong() - elapsed).coerceAtLeast(0L)
    }

    private fun startMonotonicTimer(
        startTime: Long,
        context: Context,
        onMaxDurationReached: () -> Unit
    ) {
        cancelMonotonicTimer()
        startRealtime = startTime
        val runnable = object : Runnable {
            override fun run() {
                if (!recordingActive) return
                val elapsed = SystemClock.elapsedRealtime() - startRealtime
                val remainingMs = (MAX_DURATION_MS.toLong() - elapsed).coerceAtLeast(0L)
                if (remainingMs <= 0L) {
                    Log.d(TAG, "15s cap reached via monotonic timer")
                    autoStopped = true
                    teardownRecorder(context, deleteTemp = false)
                    mainHandler.post { onMaxDurationReached() }
                } else {
                    mainHandler.postDelayed(this, 100L)
                }
            }
        }
        monotonicTimerRunnable = runnable
        mainHandler.postDelayed(runnable, 100L)
    }

    private fun cancelMonotonicTimer() {
        monotonicTimerRunnable?.let {
            mainHandler.removeCallbacks(it)
            monotonicTimerRunnable = null
        }
    }

    /**
     * API 31+ requires a Context for MediaRecorder so it can scope the audio
     * device routing. Older devices use the deprecated no-arg constructor.
     */
    @Suppress("DEPRECATION")
    private fun createRecorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context.applicationContext)
        } else {
            MediaRecorder()
        }
}

/**
 * Production alias matching architectural specification.
 */
typealias MorroAudioRecorder = MemoRecorder
