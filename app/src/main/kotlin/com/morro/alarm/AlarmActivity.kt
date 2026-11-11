package com.morro.alarm

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.morro.alarm.ui.theme.MorroTheme
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroTypography
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var missionType by mutableStateOf(AlarmScheduler.MissionType.MATH)
    private var activeMissionType by mutableStateOf(MissionType.VOICE_MEMO)

    // 30-second mission grace window state
    private var missionGraceSeconds by mutableIntStateOf(30)
    private var graceJob: Job? = null

    // Math gate state
    private var problemsSolved by mutableStateOf(0)
    private val totalProblems = 3
    private var currentQuestion by mutableStateOf<MathEngine.Question?>(null)
    private var userInput by mutableStateOf("")

    // Recording gate state for tomorrow's commitment
    private var isRecording by mutableStateOf(false)
    private var recordingCountdown by mutableStateOf(15)

    private val TAG = "AlarmActivity"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "AlarmActivity onCreate")

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
            val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
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
        activeMissionType = configuredMission

        // Pre-generate math problem if in puzzle mode
        nextQuestion()

        // Derive puzzle type (MATH vs AFFIRMATION) from the actual mission type.
        // Voice missions (VOICE_COMMITMENT, VOICE_MEMO) use MATH puzzle gate.
        // Other missions (QR_BARCODE, QR_CODE, PHOTO_OBJECT, PHOTO_MISSION) skip the puzzle gate entirely.
        missionType = when (configuredMission) {
            MissionType.VOICE_COMMITMENT, MissionType.VOICE_MEMO -> AlarmScheduler.MissionType.MATH
            else -> AlarmScheduler.MissionType.AFFIRMATION // Won't be used for non-voice missions
        }

        // Start alarm and configure initial gate based on MissionType
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

        setContent {
            MorroTheme {
                AlarmScreen(
                    gateState = gateState,
                    missionType = missionType,
                    activeMissionType = activeMissionType,
                    missionGraceSeconds = missionGraceSeconds,
                    currentQuestion = currentQuestion,
                    userInput = userInput,
                    onAnswerChanged = { userInput = it },
                    onBackspace = { clearInput() },
                    onSubmit = { submitAnswer(it) },
                    onPuzzleSolved = { completePuzzle() },
                    onMissionSuccess = { onMissionComplete() },
                    onRestartGracePeriod = { startGraceCountdown() },
                    onSnooze = { snoozeAlarm() },
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
     * Starts or restarts the 30-second grace window (volume 20%).
     * Restores 100% volume when 30s expires.
     */
    private fun startGraceCountdown() {
        missionGraceSeconds = 30
        AlarmSoundPlayer.setVolume(0.2f)
        graceJob?.cancel()
        graceJob = lifecycleScope.launch {
            while (missionGraceSeconds > 0) {
                delay(1000)
                missionGraceSeconds--
            }
            // Grace window expired -> restore 100% volume until user taps "Start Scan" or completes
            AlarmSoundPlayer.setVolume(1.0f)
        }
    }

    /** Cancels the foreground service notification (ID 1002). */
    private fun cancelForegroundNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(AlarmForegroundService.NOTIFICATION_ID)
    }

    private fun nextQuestion() {
        userInput = ""
        currentQuestion = MathEngine.generateQuestion()
    }

    private fun clearInput() {
        userInput = userInput.take(userInput.length - 1)
    }

    private fun submitAnswer(answer: String) {
        val question = currentQuestion ?: return
        if (!MathEngine.verify(answer, question)) {
            userInput = ""
            return
        }
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
                delay(3000)
                AlarmSoundPlayer.stop()
                promptForTomorrowMemo()
            }
            return
        }

        lifecycleScope.launch {
            delay(300)
            MemoPlayer.startPlayback(
                context = this@AlarmActivity,
                filePath = memoPath,
                onCompletion = { promptForTomorrowMemo() },
                onError = { err ->
                    Log.w(TAG, "Memo playback failed: $err; prompting for tomorrow", null)
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
        gateState = AlarmGateState.RECORDING_TOMORROW
    }

    /** End the alarm: release screen keep-on flag, wake lock, cancel scheduled alarm and remove task. */
    private fun finishAlarm() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wakeLock in finishAlarm", e)
        }
        cancelAlarm()
        finishAndRemoveTask()
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

    /** Stops recording, calls MemoStorage.saveMemo() synchronously, shows success toast, and finishes. */
    private fun stopRecordingTomorrow() {
        if (!isRecording) return
        isRecording = false
        val recorded = MemoRecorder.stopRecording(this)
        if (recorded) {
            val saved = MemoStorage.saveMemo(this)
            if (saved) {
                Toast.makeText(this, "Tomorrow's commitment saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save commitment; using fallback.", Toast.LENGTH_SHORT).show()
            }
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

    override fun onResume() {
        super.onResume()
        when (gateState) {
            AlarmGateState.PUZZLE -> {
                AlarmSoundPlayer.start(this)
                AlarmSoundPlayer.setVolume(1.0f)
            }
            AlarmGateState.MISSION_QR, AlarmGateState.MISSION_PHOTO -> {
                AlarmSoundPlayer.start(this)
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
        missionType: AlarmScheduler.MissionType,
        activeMissionType: MissionType,
        missionGraceSeconds: Int,
        currentQuestion: MathEngine.Question?,
        userInput: String,
        onAnswerChanged: (String) -> Unit,
        onBackspace: () -> Unit,
        onSubmit: (String) -> Unit,
        onPuzzleSolved: () -> Unit,
        onMissionSuccess: () -> Unit,
        onRestartGracePeriod: () -> Unit,
        onSnooze: () -> Unit,
        onRecordTomorrowContent: @Composable () -> Unit
    ) {
        val colors = morroColors()
        val typo = morroTypography()
        val currentTime = remember {
            mutableStateOf(SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date()))
        }
        LaunchedEffect(Unit) {
            while (true) {
                delay(500)
                currentTime.value = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas)
        ) {
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

                        when (missionType) {
                            AlarmScheduler.MissionType.MATH -> {
                                currentQuestion?.let { question ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(
                                            text = question.text,
                                            style = typo.headlineMedium,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        OutlinedTextField(
                                            value = userInput,
                                            onValueChange = { onAnswerChanged(it) },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = { Text("Enter answer", color = colors.textDisabled) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(4.dp),
                                            colors = TextFieldColors(
                                                focusedTextColor = colors.textPrimary,
                                                unfocusedTextColor = colors.textPrimary,
                                                disabledTextColor = colors.textDisabled,
                                                errorTextColor = colors.error,
                                                focusedContainerColor = Color.Unspecified,
                                                unfocusedContainerColor = Color.Unspecified,
                                                disabledContainerColor = Color.Unspecified,
                                                errorContainerColor = Color.Unspecified,
                                                cursorColor = colors.accentTactical,
                                                errorCursorColor = colors.error,
                                                textSelectionColors = TextSelectionColors(
                    handleColor = colors.accentTactical,
                    backgroundColor = colors.accentTactical.copy(alpha = 0.3f)
                ),
                                                focusedIndicatorColor = colors.accentTactical,
                                                unfocusedIndicatorColor = colors.borderSubtle,
                                                disabledIndicatorColor = Color.Unspecified,
                                                errorIndicatorColor = colors.error,
                                                focusedLabelColor = Color.Unspecified,
                                                unfocusedLabelColor = Color.Unspecified,
                                                disabledLabelColor = Color.Unspecified,
                                                errorLabelColor = Color.Unspecified,
                                                focusedLeadingIconColor = Color.Unspecified,
                                                unfocusedLeadingIconColor = Color.Unspecified,
                                                disabledLeadingIconColor = Color.Unspecified,
                                                errorLeadingIconColor = Color.Unspecified,
                                                focusedTrailingIconColor = Color.Unspecified,
                                                unfocusedTrailingIconColor = Color.Unspecified,
                                                disabledTrailingIconColor = Color.Unspecified,
                                                errorTrailingIconColor = Color.Unspecified,
                                                focusedPlaceholderColor = colors.textDisabled,
                                                unfocusedPlaceholderColor = colors.textDisabled,
                                                disabledPlaceholderColor = colors.textDisabled,
                                                errorPlaceholderColor = colors.error,
                                                focusedSupportingTextColor = Color.Unspecified,
                                                unfocusedSupportingTextColor = Color.Unspecified,
                                                disabledSupportingTextColor = Color.Unspecified,
                                                errorSupportingTextColor = Color.Unspecified,
                                                focusedPrefixColor = Color.Unspecified,
                                                unfocusedPrefixColor = Color.Unspecified,
                                                disabledPrefixColor = Color.Unspecified,
                                                errorPrefixColor = Color.Unspecified,
                                                focusedSuffixColor = Color.Unspecified,
                                                unfocusedSuffixColor = Color.Unspecified,
                                                disabledSuffixColor = Color.Unspecified,
                                                errorSuffixColor = Color.Unspecified
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = onBackspace,
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = colors.textSecondary
                                                )
                                            ) {
                                                Text("Clear")
                                            }
                                            Button(
                                                onClick = { onSubmit(userInput) },
                                                modifier = Modifier.weight(1f),
                                                enabled = userInput.isNotBlank(),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colors.accentTactical,
                                                    contentColor = Color.White
                                                )
                                            ) {
                                                Text("Submit")
                                            }
                                        }
                                    }
                                } ?: run {
                                    AffirmationGate(
                                        onSuccess = onPuzzleSolved,
                                        onSnooze = onSnooze
                                    )
                                }
                            }
                            AlarmScheduler.MissionType.AFFIRMATION -> {
                                AffirmationGate(
                                    onSuccess = onPuzzleSolved,
                                    onSnooze = onSnooze
                                )
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
                                    color = colors.accentTactical,
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
                .background(if (isGraceActive) colors.surfaceLow else colors.error.copy(alpha = 0.2f))
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
                    color = if (isGraceActive) colors.accentTactical else colors.error,
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
                        .background(colors.accentTactical, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onRestartGrace)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "START SCAN",
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
     * with a massive AccentTactical Record button and low-contrast Skip button.
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
                        color = colors.accentTactical,
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
                            containerColor = colors.accentTactical,
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