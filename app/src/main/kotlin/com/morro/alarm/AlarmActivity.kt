package com.morro.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AlarmActivity: Full-screen alarm interface that manages the puzzle dismiss gate.
 * Two states: RINGING (initial alarm with puzzle prompt) and SOLVING_PUZZLE (3-problem sequence).
 */
class AlarmActivity : ComponentActivity() {

    /** Screen state: alarm is ringing until the user starts the puzzle gate */
    enum class AlarmState { RINGING, SOLVING_PUZZLE }

    private var state by mutableStateOf(AlarmState.RINGING)
    private var problemsSolved by mutableStateOf(0)
    private var totalProblems = 3

    private var currentQuestion by mutableStateOf<MathEngine.Question?>(null)
    private var userInput by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        AlarmSoundPlayer.start(this)

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
                    state = state,
                    problemsSolved = problemsSolved,
                    totalProblems = totalProblems,
                    currentQuestion = currentQuestion,
                    userInput = userInput,
                    onStartPuzzle = { startPuzzle() },
                    onSnooze = { snoozeAlarm() },
                    onAnswerChanged = { userInput = it },
                    onBackspace = { clearInput() },
                    onSubmit = { submitAnswer(it) },
                    onDismissAlarm = { dismissAlarm() }
                )
            }
        }
    }

    private fun startPuzzle() {
        state = AlarmState.SOLVING_PUZZLE
        problemsSolved = 0
        userInput = ""
        currentQuestion = MathEngine.generateQuestion()
    }

    private fun clearInput() {
        userInput = userInput.take(userInput.length - 1)
    }

    private fun submitAnswer(answer: String) {
        val question = currentQuestion ?: return
        if (!MathEngine.verify(answer, question)) {
            // Wrong answer: clear input, keep question (user retries same problem)
            userInput = ""
            return
        }
        problemsSolved++
        userInput = ""
        if (problemsSolved >= totalProblems) {
            completePuzzle()
        } else {
            currentQuestion = MathEngine.generateQuestion()
        }
    }

    /** Puzzle complete — stop sound/vibration, cancel alarms, remove task from recents */
    private fun completePuzzle() {
        AlarmSoundPlayer.stop()
        cancelAlarm()
        finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        AlarmSoundPlayer.start(this)
    }

    override fun onPause() {
        super.onPause()
        // Keep sound running if just briefly obscured; stop only on explicit dismiss/snooze
    }

    private fun dismissAlarm() {
        AlarmSoundPlayer.stop()
        cancelAlarm()
        finish()
    }

    private fun snoozeAlarm() {
        AlarmSoundPlayer.stop()
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

    override fun onBackPressed() {
        // Must use Dismiss or Snooze buttons; back does nothing
    }
}

@Composable
fun AlarmScreen(
    state: AlarmActivity.AlarmState,
    problemsSolved: Int,
    totalProblems: Int,
    currentQuestion: MathEngine.Question?,
    userInput: String,
    onStartPuzzle: () -> Unit,
    onSnooze: () -> Unit,
    onAnswerChanged: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: (String) -> Unit,
    onDismissAlarm: () -> Unit
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing amber ring
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .background(Color(0x15FF9E1B).copy(alpha = 0.08f), shape = androidx.compose.foundation.shape.CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(Color(0x15FF9E1B).copy(alpha = 0.19f), shape = androidx.compose.foundation.shape.CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color(0x15FF9E1B).copy(alpha = 0.31f), shape = androidx.compose.foundation.shape.CircleShape)
                )
                Text(
                    text = currentTime.value,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color(0xFFFFE0B2),
                    fontSize = 56.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "ALARM",
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFFFFCC80)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Promise what you're doing today.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFFB74D)
            )
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f).height(72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9E1B),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Snooze (10m)", fontSize = 20.sp)
                }
                Button(
                    onClick = onDismissAlarm,
                    modifier = Modifier.weight(1f).height(72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB71C1C),
                        contentColor = Color.White
                    )
                ) {
                    Text("Dismiss", fontSize = 20.sp)
                }
            }
        }
    }
}
