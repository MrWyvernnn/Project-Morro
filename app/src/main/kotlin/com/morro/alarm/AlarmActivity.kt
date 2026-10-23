package com.morro.alarm

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AlarmActivity: Full-screen alarm that runs the two Slice 2 gates in order:
 *
 *   1. PUZZLE  — alarm rings loudly; user solves a math puzzle to silence it.
 *   2. MEMO    — latest_memo.m4a plays on the USAGE_ALARM stream. No skip/seek.
 *
 * The alarm does not fully end until memo playback completes. The puzzle and the
 * memo are separate layers and never depend on each other's UX.
 */
class AlarmActivity : ComponentActivity() {

    /** Which gate the alarm is currently in. */
    enum class AlarmGateState { PUZZLE, PLAYING_MEMO }

    private var gateState by mutableStateOf(AlarmGateState.PUZZLE)
    private var problemsSolved by mutableStateOf(0)
    private val totalProblems = 3

    private var currentQuestion by mutableStateOf<MathEngine.Question?>(null)
    private var userInput by mutableStateOf("")

    private val TAG = "AlarmActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lock screen and turn the screen on.
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

        // Ring loudly on the alarm stream while the puzzle gate is active.
        AlarmSoundPlayer.start(this)

        // Generate the first puzzle problem.
        nextQuestion()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF9E1B),   // Warm Amber
                    background = Color(0xFF000000),
                    surface = Color(0xFF1A1200),
                    onPrimary = Color.Black,
                    onBackground = Color(0xFFFFE0B2), // Warm Cream
                    onSurface = Color(0xFFFFCC80)
                )
            ) {
                AlarmScreen(
                    gateState = gateState,
                    problemsSolved = problemsSolved,
                    totalProblems = totalProblems,
                    currentQuestion = currentQuestion,
                    userInput = userInput,
                    onAnswerChanged = { userInput = it },
                    onBackspace = { clearInput() },
                    onSubmit = { submitAnswer(it) },
                    onSnooze = { snoozeAlarm() }
                )
            }
        }
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
            // Wrong answer: clear input, keep the same question for retry.
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

    /** Puzzle solved — silence the ring and immediately play the saved commitment memo. */
    private fun completePuzzle() {
        AlarmSoundPlayer.stop()
        val memoPath = MemoStorage.getLatestMemoPath(this)
        if (memoPath == null) {
            Log.w(TAG, "No saved memo; ending alarm after puzzle")
            finishAlarm()
            return
        }
        gateState = AlarmGateState.PLAYING_MEMO
        MemoPlayer.startPlayback(
            context = this,
            filePath = memoPath,
            onCompletion = { finishAlarm() },
            onError = { err ->
                Log.w(TAG, "Memo playback failed: $err; ending alarm", null)
                finishAlarm()
            }
        )
    }

    /** End the alarm: cancel any scheduled alarm and remove the task. */
    private fun finishAlarm() {
        cancelAlarm()
        finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        // Keep the alarm ringing if the activity is shown again mid-puzzle.
        if (gateState == AlarmGateState.PUZZLE) {
            AlarmSoundPlayer.start(this)
        }
    }

    override fun onPause() {
        super.onPause()
        // Keep sound running if just briefly obscured; stop only on explicit dismiss/snooze.
    }

    private fun snoozeAlarm() {
        AlarmSoundPlayer.stop()
        MemoPlayer.stopPlayback()
        AlarmScheduler.snoozeAlarm(this, 10)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(1001)
        finish()
    }

    private fun cancelAlarm() {
        AlarmScheduler.cancelAlarm(this)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(1001)
    }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // Must solve the puzzle; back does nothing (no bypass of the gate).
        // Calling super would dismiss the alarm, so we intentionally don't.
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release all audio resources if the activity is torn down mid-gate.
        // Prevents MediaPlayer/MediaRecorder leaks that would crash the app overnight.
        AlarmSoundPlayer.stop()
        MemoPlayer.stopPlayback()
    }

    @Composable
    fun AlarmScreen(
        gateState: AlarmGateState,
        problemsSolved: Int,
        totalProblems: Int,
        currentQuestion: MathEngine.Question?,
        userInput: String,
        onAnswerChanged: (String) -> Unit,
        onBackspace: () -> Unit,
        onSubmit: (String) -> Unit,
        onSnooze: () -> Unit
    ) {
        val currentTime = remember {
            mutableStateOf(SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date()))
        }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(500)
                currentTime.value = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
            }
        }

        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentTime.value,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color(0xFFFFE0B2),
                    fontSize = 40.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "ALARM",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color(0xFFFFCC80)
                )

                when (gateState) {
                    AlarmGateState.PUZZLE -> {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "Solve the puzzle to silence the alarm.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFFFB74D)
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        currentQuestion?.let { question ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = question.text,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = userInput.ifEmpty { "_" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFFFF9E1B)
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                numberPad(onDigit = { onAnswerChanged(userInput + it.toString()) })
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.width(260.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(
                                        onClick = onBackspace,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF9E1B),
                                            contentColor = Color.Black
                                        )
                                    ) { Text("⌫", fontSize = 20.sp) }
                                    Button(
                                        onClick = { onSubmit(userInput) },
                                        enabled = userInput.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF9E1B),
                                            contentColor = Color.Black
                                        )
                                    ) { Text("Submit", fontSize = 20.sp) }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = onSnooze,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB71C1C),
                                contentColor = Color.White
                            )
                        ) { Text("Snooze (10m)", fontSize = 18.sp) }
                    }
                    AlarmGateState.PLAYING_MEMO -> {
                        Spacer(modifier = Modifier.height(48.dp))
                        Text(
                            text = "Your commitment is playing…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFFFCC80)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Please listen to the end. No skip, no seek.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFFB74D)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun numberPad(onDigit: (Int) -> Unit) {
        val rows = listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { digit ->
                        key(digit) {
                            digitButton(digit) { onDigit(digit) }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                digitButton(0) { onDigit(0) }
            }
        }
    }

    @Composable
    private fun key(digit: Int, content: @Composable () -> Unit) {
        // Used to stabilize digit-button recompositions. Provides a stable key for Row elements,
        // avoiding repeated recomposition of the entire number pad when a digit is pressed.
        key(digit) { content() }
    }

    @Composable
    private fun digitButton(digit: Int, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(56.dp, 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1A1200),
                contentColor = Color(0xFFFF9E1B)
            )
        ) {
            Text(text = digit.toString(), fontSize = 24.sp)
        }
    }
}
