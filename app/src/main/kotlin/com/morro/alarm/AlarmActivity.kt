package com.morro.alarm

import android.app.KeyguardManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.morro.alarm.mission.MissionPrefs
import com.morro.alarm.mission.MissionType
import com.morro.alarm.mission.PhotoMissionScreen
import com.morro.alarm.mission.PhotoTargetPool
import com.morro.alarm.mission.QrMissionScreen
import com.morro.alarm.ui.components.MorroSky
import com.morro.alarm.ui.theme.MorroTheme
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AlarmActivity: Full-screen alarm that runs the wake-up verification gates:
 *
 *   - VOICE_MEMO: Math/Affirmation Puzzle -> Playing Voice Commitment -> Tomorrow's Memo.
 *   - QR_BARCODE: 30s 20% volume grace window -> QR/Barcode Scanner -> Tomorrow's Memo.
 *   - PHOTO_OBJECT: 30s 20% volume grace window -> 30-Item Photo Recognition -> Tomorrow's Memo.
 */
class AlarmActivity : ComponentActivity() {

    enum class AlarmGateState {
        PUZZLE,
        MISSION_QR,
        MISSION_PHOTO,
        PLAYING_MEMO,
        RECORDING_TOMORROW
    }

    private var gateState by mutableStateOf(AlarmGateState.PUZZLE)

    private var missionGraceSeconds by mutableIntStateOf(30)
    private var graceJob: Job? = null

    // Math gate state. One tap per puzzle: a question arrives with its own four choices, so there
    // is no field to focus and no keyboard to fight at 6 a.m.
    private var problemsSolved by mutableStateOf(0)
    private val totalProblems = 3
    private var currentQuestion by mutableStateOf<MathEngine.Question?>(null)

    /** The choice that was just missed, tinted until the next puzzle. -1 means nothing missed. */
    private var wrongChoice by mutableIntStateOf(-1)

    // Recording gate state for tomorrow's commitment
    private var isRecording by mutableStateOf(false)
    private var recordingCountdown by mutableIntStateOf(15)

    private val TAG = "AlarmActivity"

    private companion object {
        const val KEY_GATE_STATE = "gate_state"
        const val KEY_PROBLEMS_SOLVED = "problems_solved"
        const val KEY_QUESTION_TEXT = "question_text"
        const val KEY_QUESTION_ANSWER = "question_answer"
    }
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * True when this ring is the wake-up check's one re-ring.
     *
     * Read from the intent *and* from prefs: the foreground service's full-screen intent can launch
     * this activity without the extra, and a re-ring that rescheduled tomorrow's alarm would be the
     * loop the whole feature exists to avoid.
     */
    private var isWakeUpCheckRing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        isWakeUpCheckRing =
            intent?.getBooleanExtra(WakeUpCheck.EXTRA_RERING, false) == true ||
                WakeUpCheck.isRingInProgress(this)

        Log.d(TAG, "AlarmActivity onCreate (wakeUpCheckRing=$isWakeUpCheckRing)")

        // Show over the lock screen, turn screen on, and keep screen on (Wake-Lock Maintenance).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        } else {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Request keyguard dismissal on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(KeyguardManager::class.java)
            @Suppress("NewApi")
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissError() {
                    Log.w(TAG, "Keyguard dismiss error")
                }

                override fun onDismissSucceeded() {
                    Log.d(TAG, "Keyguard dismissed successfully")
                }

                override fun onDismissCancelled() {
                    Log.d(TAG, "Keyguard dismiss cancelled")
                }
            })
        }

        // Acquire wake-lock to keep CPU alive during alarm
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "morro:alarm_active"
        ).apply {
            acquire(10 * 60 * 1000L) // 10-minute hard cap
        }
        Log.d(TAG, "WakeLock acquired")

        // Read user's chosen mission type
        val configuredMission = MissionPrefs.getMissionType(this)

        // A rotation, a dark-mode switch or a system-initiated recreate used to throw away the
        // gate, the solved count and the typed answer, sending a half-awake person back to problem
        // 1 of 3. Restore that state and only fall back to a fresh start when there is none.
        val restored = savedInstanceState != null && restoreGateState(savedInstanceState)
        if (!restored) {
            // Pre-generate math problem if in puzzle mode
            nextQuestion()

            // Start alarm and configure initial gate based on MissionType.
            when (configuredMission) {
                MissionType.QR_BARCODE, MissionType.QR_CODE -> {
                    gateState = AlarmGateState.MISSION_QR
                    AlarmSoundPlayer.start(this)
                    startGraceCountdown()
                }
                MissionType.PHOTO_OBJECT, MissionType.PHOTO_MISSION -> {
                    gateState = AlarmGateState.MISSION_PHOTO
                    AlarmSoundPlayer.start(this)
                    startGraceCountdown()
                }
                MissionType.VOICE_MEMO, MissionType.VOICE_COMMITMENT -> {
                    gateState = AlarmGateState.PUZZLE
                    AlarmSoundPlayer.start(this)
                    AlarmSoundPlayer.setVolume(1.0f)
                }
            }
        }

        setContent {
            MorroTheme {
                AlarmScreen(
                    gateState = gateState,
                    missionGraceSeconds = missionGraceSeconds,
                    problemsSolved = problemsSolved,
                    currentQuestion = currentQuestion,
                    onSubmitChoice = { submitAnswer(it) },
                                onMissionSuccess = { onMissionComplete() },
                    onRestartGracePeriod = { restoreFullVolume() },
                                onRecordTomorrowContent = {
                        RecordingGateUI(
                            isRecording = isRecording,
                            countdown = recordingCountdown,
                            onStartRecording = { startRecordingTomorrow() },
                            onStopRecording = { stopRecordingTomorrow() },
                            onSkip = { skipTomorrowMemo() }
                        )
                    }
                )
            }
        }
    }

    /**
     * Drops the ring back to full volume without touching the grace window.
     *
     * The banner's button used to call startGraceCountdown(), which muted the alarm to 20% at the
     * exact moment the banner was telling the person "MAX VOLUME ACTIVE". Nothing about the camera
     * is started by that button — the scanner is already live underneath.
     */
    private fun restoreFullVolume() {
        graceJob?.cancel()
        missionGraceSeconds = 0
        AlarmSoundPlayer.setVolume(1.0f)
    }

    /**
     * Starts or restarts the 30-second grace window (volume 20%).
     * Restores 100% volume when 30s expires.
     */
    private fun startGraceCountdown() {
        missionGraceSeconds = 30
        AlarmSoundPlayer.setVolume(0.2f)
        graceJob?.cancel()
        graceJob = lifecycleScope.launch {
            while (missionGraceSeconds > 0) {
                delay(1000L)
                missionGraceSeconds--
            }
            // Grace window expired -> restore 100% volume until user taps "Start Scan" or completes
            AlarmSoundPlayer.setVolume(1.0f)
        }
    }

    /** Cancels the foreground service notification (ID 1002). */
    private fun cancelForegroundNotification() {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(AlarmForegroundService.NOTIFICATION_ID)
    }

    /**
     * The next puzzle, at the difficulty chosen when the alarm was set.
     *
     * Reading the preference per question, instead of caching it, means a difficulty changed in the
     * builder applies to the next ring with nothing to restart.
     */
    private fun nextQuestion() {
        wrongChoice = -1
        currentQuestion = MathEngine.generateQuestion(MathPrefs.getDifficulty(this))
    }

    /** A tap on one of the four choices. A miss is marked; the alarm keeps ringing. */
    private fun submitAnswer(choice: Int) {
        val question = currentQuestion ?: return
        if (!MathEngine.isCorrect(choice, question)) {
            wrongChoice = choice
            return
        }
        wrongChoice = -1
        problemsSolved++
        if (problemsSolved >= totalProblems) {
            completePuzzle()
        } else {
            nextQuestion()
        }
    }

    /** Math/Affirmation solved for Voice Memo gate -> silence alarm, play commitment memo. */
    private fun completePuzzle() {
        gateState = AlarmGateState.PLAYING_MEMO
        AlarmSoundPlayer.stop()

        val memoPath = MemoStorage.getLatestMemoPath(this)
        Log.d(TAG, "Memo file check: path=${memoPath ?: "null"}")
        if (memoPath == null) {
            Log.w(TAG, "No saved memo; playing fallback alarm tone then prompting for tomorrow")
            AlarmSoundPlayer.playFallbackAlarmTone(this)
            lifecycleScope.launch {
                delay(3000L)
                AlarmSoundPlayer.stop()
                promptForTomorrowMemo()
            }
            return
        }

        lifecycleScope.launch {
            delay(300L)
            MemoPlayer.startPlayback(
                context = this@AlarmActivity,
                filePath = memoPath,
                onCompletion = { promptForTomorrowMemo() },
                onError = { err ->
                    Log.w(TAG, "Memo playback failed: $err; prompting for tomorrow")
                    promptForTomorrowMemo()
                }
            )
        }
    }

    /** QR or Photo mission solved -> silence ring, proceed to tomorrow's memo. */
    private fun onMissionComplete() {
        graceJob?.cancel()
        AlarmSoundPlayer.stop()
        promptForTomorrowMemo()
    }

    /** Transition the Compose state to CaptureTomorrow (RECORDING_TOMORROW). */
    private fun promptForTomorrowMemo() {
        // A wake-up check re-ring exists only to confirm the person is awake. Asking them to record
        // tomorrow's promise a second time in the same morning would duplicate the capture, so this
        // ring ends as soon as the gates are solved.
        if (isWakeUpCheckRing) {
            finishAlarm()
            return
        }
        gateState = AlarmGateState.RECORDING_TOMORROW
    }

    /** End the alarm: release screen keep-on flag, wake lock, cancel scheduled alarm and remove task. */
    private fun finishAlarm() {
        // The scheduler holds one alarm slot, so the occurrence that just rang must be replaced by
        // the next one or the alarm never comes back. Read the pending time before cancelling:
        // cancelAlarm() clears it, and a ring that never had a scheduled alarm (a debug test ring
        // after the person switched the alarm off) must not silently re-arm it.
        val wasScheduled = AlarmScheduler.getNextAlarmTime(this) != null

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wakeLock in finishAlarm", e)
        }

        if (isWakeUpCheckRing) {
            // A re-ring is confirmation, not a new morning. Tomorrow's alarm is already armed and
            // this morning's single re-ring is already spent, so neither is touched here: calling
            // cancelAlarm() would delete the pending alarm and leave the person with nothing.
            cancelForegroundNotification()
            WakeUpCheck.resolve(this)
            Log.i(TAG, "Wake-up re-ring completed; left the daily alarm and the check untouched")
        } else {
            cancelAlarm()

            // The gates are solved, so this occurrence is finished rather than missed. Recorded
            // before the re-arm below, which moves the expected trigger to tomorrow.
            MissedAlarmLog.recordCompleted(this)

            if (wasScheduled) {
                rearmNextWakeUp()
                // Armed only after a real dismissal. WakeUpCheck itself refuses a second one inside
                // the same morning, which is the cap that stops this becoming a loop.
                WakeUpCheck.arm(this)
            }
        }

        finishAndRemoveTask()
    }

    /**
     * Arms the next daily occurrence from the saved hour and minute.
     *
     * `cancelAlarm()` only drops the pending trigger and its timestamp; the chosen hour and minute
     * survive, so every dismissal can put the alarm back on the calendar. `scheduleDailyAlarm()`
     * rolls to tomorrow when that time has already passed today, which is always the case once the
     * alarm has rung.
     *
     * A snooze works the same way: it replaces the slot with a trigger ten minutes out, and when
     * that one ends this path arms tomorrow. That keeps exactly one pending alarm at any moment —
     * two competing triggers would mean a duplicate ring.
     */
    private fun rearmNextWakeUp() {
        val saved = AlarmScheduler.getSavedAlarmTime(this)
        if (saved == null) {
            Log.w(TAG, "No saved wake-up time; nothing to re-arm")
            return
        }
        val armed = AlarmScheduler.scheduleDailyAlarm(this, saved.first, saved.second)
        if (armed) {
            Log.i(TAG, "Re-armed next wake-up for ${saved.first}:${saved.second}")
        } else {
            Log.w(TAG, "Could not re-arm the next wake-up; exact alarm access may be revoked")
        }
    }

    /** Starts recording tomorrow's commitment memo with 15s monotonic countdown. */
    private fun startRecordingTomorrow() {
        isRecording = true
        recordingCountdown = 15
        MemoRecorder.startRecording(
            this,
            onError = { err ->
                Log.w(TAG, "Recording failed: $err")
                isRecording = false
                Toast.makeText(this, "Recording error: $err", Toast.LENGTH_SHORT).show()
            },
            onMaxDurationReached = {
                Log.d(TAG, "Recording auto-stopped at 15s cap")
                stopRecordingTomorrow()
            }
        )
        lifecycleScope.launch {
            val startRealtime = android.os.SystemClock.elapsedRealtime()
            while (isRecording) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - startRealtime
                val remainingMs = (15_000L - elapsed).coerceAtLeast(0L)
                recordingCountdown = ((remainingMs + 999L) / 1000L).toInt().coerceIn(0, 15)
                if (remainingMs <= 0L) {
                    stopRecordingTomorrow()
                    break
                }
                delay(100L)
            }
        }
    }

    /**
     * Stops recording, promotes the clip to the saved memo, shows the result, and finishes.
     *
     * The 15 s cap arrives from two places — MediaRecorder's own MAX_DURATION_REACHED callback and
     * the countdown coroutine in [startRecordingTomorrow] — and whichever lands second finds the
     * recorder already torn down. isRecording makes this idempotent; the clip on disk, checked by
     * saveMemo(), decides whether there is a memo to keep.
     */
    private fun stopRecordingTomorrow() {
        if (!isRecording) return
        isRecording = false
        MemoRecorder.stopRecording(this)
        if (MemoStorage.saveMemo(this)) {
            Toast.makeText(this, "Tomorrow's commitment saved!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Recording was too short; using fallback.", Toast.LENGTH_SHORT).show()
        }
        finishAlarm()
    }

    /** Skip action: immediately finishes the activity, using yesterday's memo as fallback. */
    private fun skipTomorrowMemo() {
        isRecording = false
        MemoRecorder.cancelRecording(this)
        Toast.makeText(this, "Skipped. Using yesterday's memo as fallback.", Toast.LENGTH_SHORT).show()
        finishAlarm()
    }

    /** Persists the puzzle gate so a recreate resumes mid-question instead of restarting it. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_GATE_STATE, gateState.name)
        outState.putInt(KEY_PROBLEMS_SOLVED, problemsSolved)
        currentQuestion?.let { question ->
            outState.putString(KEY_QUESTION_TEXT, question.text)
            outState.putInt(KEY_QUESTION_ANSWER, question.answer)
        }
    }

    /** @return true when a usable gate state was restored. */
    private fun restoreGateState(state: Bundle): Boolean {
        val name = state.getString(KEY_GATE_STATE) ?: return false
        val restoredGate = runCatching { AlarmGateState.valueOf(name) }.getOrNull() ?: return false
        gateState = restoredGate
        problemsSolved = state.getInt(KEY_PROBLEMS_SOLVED, 0)
        val text = state.getString(KEY_QUESTION_TEXT)
        val answer = state.getInt(KEY_QUESTION_ANSWER, Int.MIN_VALUE)
        currentQuestion = if (text != null && answer != Int.MIN_VALUE) {
            // Choices are rebuilt rather than saved: they only ever contain the answer, and
            // re-rolling them keeps the rotation path the same size as a fresh question.
            MathEngine.Question(text, answer, MathEngine.optionsFor(answer))
        } else {
            MathEngine.generateQuestion()
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        // A permission dialog or a pulled-down shade pauses and resumes this activity. Restarting
        // the player from the top made the ringtone jump back to its first note every time, so the
        // tone is only (re)started when nothing is playing.
        val alreadyRinging = AlarmSoundPlayer.isPlaying()
        when (gateState) {
            AlarmGateState.PUZZLE -> {
                if (!alreadyRinging) AlarmSoundPlayer.start(this)
                AlarmSoundPlayer.setVolume(1.0f)
            }
            AlarmGateState.MISSION_QR, AlarmGateState.MISSION_PHOTO -> {
                if (!alreadyRinging) AlarmSoundPlayer.start(this)
                if (missionGraceSeconds > 0) {
                    AlarmSoundPlayer.setVolume(0.2f)
                } else {
                    AlarmSoundPlayer.setVolume(1.0f)
                }
            }
            else -> {
                // PLAYING_MEMO or RECORDING_TOMORROW — audio is handled by MemoPlayer/MemoRecorder
            }
        }
    }

    private fun snoozeAlarm() {
        graceJob?.cancel()
        AlarmSoundPlayer.stop()
        MemoPlayer.stopPlayback()
        // Snooze takes the single alarm slot for ten minutes; the next daily occurrence is armed
        // when that trigger ends (same path as a normal dismissal), or by BootReceiver if the phone
        // restarts first. The saved hour and minute are never touched here.
        AlarmScheduler.snoozeAlarm(this, 10)
        cancelForegroundNotification()
        finish()
    }

    private fun cancelAlarm() {
        AlarmScheduler.cancelAlarm(this)
        cancelForegroundNotification()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        // Must complete gates; back does nothing.
    }

    override fun onDestroy() {
        super.onDestroy()
        graceJob?.cancel()
        AlarmSoundPlayer.stop()
        MemoPlayer.release()
        MemoRecorder.release()

        // Release wake-lock
        try {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released in onDestroy")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake-lock", e)
        }

        // Stop foreground service
        stopService(android.content.Intent(this, AlarmForegroundService::class.java))
        Log.d(TAG, "AlarmForegroundService stopped")
    }

    @Composable
    fun AlarmScreen(
        gateState: AlarmGateState,
        missionGraceSeconds: Int,
        problemsSolved: Int,
        currentQuestion: MathEngine.Question?,
        onSubmitChoice: (Int) -> Unit,
        onMissionSuccess: () -> Unit,
        onRestartGracePeriod: () -> Unit,
        onRecordTomorrowContent: @Composable () -> Unit
    ) {
        val colors = morroColors()
        val typo = morroTypography()
        // One formatter, reused every tick. Creating one twice a second was avoidable churn.
        val timeFormat = remember { SimpleDateFormat("h:mm:ss a", Locale.getDefault()) }
        val currentTime = remember {
            mutableStateOf(timeFormat.format(Date()))
        }
        LaunchedEffect(Unit) {
            while (true) {
                delay(500L)
                currentTime.value = timeFormat.format(Date())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas)
                // Android 15 draws every app edge to edge; without this the grace banner sat under
                // the status bar and the mission controls under the gesture bar.
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // The same sky as the rest of the app: clouds in Midnight, leaves in Autumn. It sits
            // behind every gate, low enough to stay out of the way of the copy.
            MorroSky(modifier = Modifier.fillMaxSize(), intensity = 0.5f)

            when (gateState) {
                AlarmGateState.MISSION_QR -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Grace window banner
                        GraceWindowBanner(
                            graceSeconds = missionGraceSeconds,
                            onRestartGrace = onRestartGracePeriod
                        )

                        val registeredCode = remember {
                            MissionPrefs.getRegisteredQrCode(this@AlarmActivity)
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            QrMissionScreen(
                                registeredCode = registeredCode,
                                isRegistrationMode = false,
                                onCodeScanned = { onMissionSuccess() }
                            )
                        }
                    }
                }

                AlarmGateState.MISSION_PHOTO -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Grace window banner
                        GraceWindowBanner(
                            graceSeconds = missionGraceSeconds,
                            onRestartGrace = onRestartGracePeriod
                        )

                        val poolIds = remember {
                            val set = MissionPrefs.getSelectedPhotoItemIds(this@AlarmActivity)
                            if (set.isNotEmpty()) set.toList() else PhotoTargetPool.COMMON_10_IDS.toList()
                        }

                        var targetItemId by rememberSaveable {
                            mutableStateOf(
                                poolIds.firstOrNull()
                                    ?: PhotoTargetPool.COMMON_10_IDS.firstOrNull()
                                    ?: PhotoTargetPool.ALL_ITEMS.first()
                            )
                        }

                        val targetItem = remember(targetItemId) {
                            PhotoTargetPool.getById(targetItemId)
                                ?: PhotoTargetPool.ITEMS.find { it.displayName.equals(targetItemId, ignoreCase = true) }
                                ?: PhotoTargetPool.ITEMS.first()
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            PhotoMissionScreen(
                                targetItem = targetItem,
                                onMissionSuccess = onMissionSuccess,
                                onSwapItem = {
                                    val other = poolIds.filter { it != targetItemId }
                                    if (other.isNotEmpty()) {
                                        targetItemId = other.random()
                                    }
                                }
                            )
                        }
                    }
                }

                AlarmGateState.PUZZLE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentTime.value,
                            style = typo.displayLarge,
                            color = colors.textPrimary,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "MORRO ALARM",
                            style = typo.titleMedium,
                            color = colors.textSecondary,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        currentQuestion?.let { question ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = question.text,
                                    style = typo.displayLarge,
                                    color = colors.textPrimary,
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Problem ${problemsSolved + 1} of $totalProblems",
                                    style = typo.labelLarge,
                                    color = colors.textTertiary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                // Two rows of two: four answers big enough to hit without aiming,
                                // and still one glance rather than a list to read.
                                question.options.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        pair.forEach { option ->
                                            val missed = option == wrongChoice
                                            Button(
                                                onClick = { onSubmitChoice(option) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(68.dp),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(
                                                    width = 1.dp,
                                                    color = if (missed) {
                                                        colors.error.copy(alpha = 0.65f)
                                                    } else {
                                                        colors.borderSubtle
                                                    }
                                                ),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (missed) {
                                                        colors.error.copy(alpha = 0.20f)
                                                    } else {
                                                        colors.surfaceElevated
                                                    },
                                                    contentColor = if (missed) colors.error else colors.textPrimary
                                                )
                                            ) {
                                                Text(
                                                    text = option.toString(),
                                                    fontSize = 26.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        if (pair.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AlarmGateState.PLAYING_MEMO -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "PLAYING YOUR COMMITMENT",
                                    style = typo.labelLarge,
                                    color = colors.textSecondary,
                                    letterSpacing = 1.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Your promise is playing in your voice. Listen to the end.",
                                    style = typo.bodyLarge,
                                    color = colors.textPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "No skip, no seek.",
                                    style = typo.bodyMedium,
                                    color = colors.honey,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                AlarmGateState.RECORDING_TOMORROW -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        onRecordTomorrowContent()
                    }
                }
            }
        }
    }

    @Composable
    private fun GraceWindowBanner(
        graceSeconds: Int,
        onRestartGrace: () -> Unit
    ) {
        val colors = morroColors()
        val isGraceActive = graceSeconds > 0
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isGraceActive) colors.surfaceHigh else colors.error.copy(alpha = 0.2f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (isGraceActive) {
                        "MUTED GRACE WINDOW: ${graceSeconds}s (20% VOL)"
                    } else {
                        "MAX VOLUME ACTIVE (100%)"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isGraceActive) colors.honey else colors.error,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (isGraceActive) "Complete mission before volume increases" else "Alarm is sounding at full volume",
                    fontSize = 11.sp,
                    color = colors.textDisabled
                )
            }

            if (!isGraceActive) {
                Box(
                    modifier = Modifier
                        .background(colors.honey, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onRestartGrace)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "MAX VOLUME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    /**
     * RecordingGateUI: Sleek screen asking the user to "Record tomorrow's commitment"
     * with a massive Honey Record button and low-contrast Skip button.
     */
    @Composable
    private fun RecordingGateUI(
        isRecording: Boolean,
        countdown: Int,
        onStartRecording: () -> Unit,
        onStopRecording: () -> Unit,
        onSkip: () -> Unit
    ) {
        val colors = morroColors()
        val typo = morroTypography()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "RECORD TOMORROW'S COMMITMENT",
                    style = typo.labelLarge,
                    color = colors.textSecondary,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "What is the ONE thing you commit to doing tomorrow?",
                    style = typo.bodyLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                if (isRecording) {
                    Text(
                        text = "Recording: $countdown s",
                        style = typo.headlineMedium,
                        color = colors.honey,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onStopRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.success,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "STOP & SAVE",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onStartRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.honey,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "RECORD COMMITMENT",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Skip (Use yesterday's memo)",
                            style = typo.bodyMedium,
                            color = colors.textDisabled
                        )
                    }
                }
            }
        }
    }
}
