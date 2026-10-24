package com.morro.alarm

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * MainActivity: The main screen of Morro.
 * Houses both the alarm scheduling/testing controls and the isolated voice memo
 * test suite (Record, Stop, Preview, Re-record, Save) for Slice 2 verification.
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) Toast.makeText(this, "Notifications disabled — alarm may not show", Toast.LENGTH_LONG).show()
    }

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Microphone permission denied. Voice memo recording requires microphone access.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Gate: if onboarding hasn't been completed, redirect before anything else.
        if (!OnboardingActivity.isOnboardingComplete(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        requestNotificationPermission()
        requestExactAlarmPermission()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF9E1B),   // Warm Amber
                    secondary = Color(0xFFE65100), // Muted Orange
                    background = Color(0xFF000000),
                    surface = Color(0xFF1A1200),
                    onPrimary = Color.Black,
                    onSecondary = Color.Black,
                    onBackground = Color(0xFFFFE0B2),
                    onSurface = Color(0xFFFFCC80)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF000000)) {
                    MainScreen(
                        context = this,
                        onRequestRecordPermission = {
                            recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        },
                        onScheduleAlarm = { timeMillis -> scheduleAlarm(timeMillis) },
                        onTestAlarm = { scheduleTestAlarm() },
                        onCancel = { cancelAlarm() }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }

    private fun scheduleAlarm(timeMillis: Long) {
        AlarmScheduler.scheduleAlarm(this, timeMillis)
        Toast.makeText(this, "Alarm set for ${AlarmScheduler.getFormattedTime(this)}", Toast.LENGTH_SHORT).show()
    }

    private fun scheduleTestAlarm() {
        val now = System.currentTimeMillis()
        AlarmScheduler.scheduleAlarm(this, now + 60_000L)
        Toast.makeText(this, "Test alarm in 1 min", Toast.LENGTH_SHORT).show()
    }

    private fun cancelAlarm() {
        AlarmScheduler.cancelAlarm(this)
        Toast.makeText(this, "Alarm canceled", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainScreen(
    context: Context,
    onRequestRecordPermission: () -> Unit,
    onScheduleAlarm: (Long) -> Unit,
    onTestAlarm: () -> Unit,
    onCancel: () -> Unit
) {
    val currentTimeStr = remember {
        val fmt = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        mutableStateOf(fmt.format(java.util.Date()))
    }

    // Force UI tick to refresh recorder / player status
    var refreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            currentTimeStr.value = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                .format(java.util.Date())
            refreshTick++
        }
    }

    val alarmTime = AlarmScheduler.getNextAlarmTime(context)
    val alarmFormatted = AlarmScheduler.getFormattedTime(context)
    val hasActiveAlarm = alarmTime != null && alarmTime > System.currentTimeMillis()

    // Countdown to next alarm
    val countdownStr = remember(alarmTime, refreshTick) {
        if (hasActiveAlarm) {
            val diffMillis = maxOf(0, alarmTime - System.currentTimeMillis())
            val hours = diffMillis / (1000 * 60 * 60)
            val minutes = (diffMillis / (1000 * 60)) % 60
            if (hours > 0) "Rings in $hours hours $minutes mins" else "Rings in $minutes mins"
        } else {
            "No alarm set"
        }
    }

    var vibrateEnabled by remember { mutableStateOf(true) }
    var snoozeMinutes by remember { mutableStateOf(10) }
    val snoozeOptions = listOf(5 to "5m", 10 to "10m", 15 to "15m")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Hero Clock + Next Alarm Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1200)),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTimeStr.value,
                    style = MaterialTheme.typography.displayLarge,
                    color = Color(0xFFFFE0B2), // Warm Cream
                    fontSize = 56.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (hasActiveAlarm) "Next alarm at $alarmFormatted" else "No alarm scheduled",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFE65100) // Muted Orange
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = countdownStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFF9E1B) // Warm Amber
                )
            }
        }

        // Voice Memo Test Card (Slice 2 audio flow test)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1200)),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Commitment Voice Memo (Max 15s)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFFF9E1B)
                )

                val hasTemp = remember(refreshTick) { MemoStorage.hasTempMemo(context) }
                val hasSaved = remember(refreshTick) { MemoStorage.hasSavedMemo(context) }
                val isRecording = remember(refreshTick) { MemoRecorder.isRecording() }
                val isPlaying = remember(refreshTick) { MemoPlayer.isPlaying() }

                val statusText = when {
                    isRecording -> "Recording in progress... (15s cap)"
                    isPlaying -> "Playing commitment memo..."
                    hasTemp -> "Temp recording ready (uncommitted)"
                    hasSaved -> "Saved commitment memo ready"
                    else -> "No memo recorded yet"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFCC80)
                )

                // Controls: Record, Stop, Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                MemoRecorder.startRecording(
                                    context,
                                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() },
                                    onMaxDurationReached = { Toast.makeText(context, "Max duration 15s reached", Toast.LENGTH_SHORT).show() }
                                )
                            } else {
                                onRequestRecordPermission()
                            }
                        },
                        enabled = !isRecording && !isPlaying,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E1B), contentColor = Color.Black)
                    ) {
                        Text("Record")
                    }

                    Button(
                        onClick = {
                            MemoRecorder.stopRecording(context)
                        },
                        enabled = isRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100), contentColor = Color.Black)
                    ) {
                        Text("Stop")
                    }

                    Button(
                        onClick = {
                            val path = if (MemoStorage.hasTempMemo(context)) MemoStorage.getTempPath(context) else MemoStorage.getLatestMemoPath(context)
                            if (path != null) {
                                MemoPlayer.startPlayback(
                                    context,
                                    path,
                                    onCompletion = { Toast.makeText(context, "Playback completed", Toast.LENGTH_SHORT).show() },
                                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                                )
                            } else {
                                Toast.makeText(context, "No memo to preview", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isRecording && !isPlaying && (hasTemp || hasSaved),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCC80), contentColor = Color.Black)
                    ) {
                        Text("Preview")
                    }
                }

                // Controls: Re-record / Discard, Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            MemoPlayer.stopPlayback()
                            MemoRecorder.cancelRecording(context)
                            Toast.makeText(context, "Recording discarded", Toast.LENGTH_SHORT).show()
                        },
                        enabled = isRecording || hasTemp,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C), contentColor = Color.White)
                    ) {
                        Text("Re-record")
                    }

                    Button(
                        onClick = {
                            val success = MemoStorage.saveMemo(context)
                            if (success) {
                                Toast.makeText(context, "Memo saved successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No recording found to save", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = hasTemp && !isRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.Black)
                    ) {
                        Text("Save")
                    }
                }
            }
        }

        // Time selector & Alarm controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            var selectedHour by remember { mutableStateOf(7) }
            var selectedMinute by remember { mutableStateOf(0) }

            Button(
                onClick = {
                    val cal = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            selectedHour = h
                            selectedMinute = m
                            val targetCal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, h)
                                set(Calendar.MINUTE, m)
                                set(Calendar.SECOND, 0)
                                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                            }
                            onScheduleAlarm(targetCal.timeInMillis)
                        },
                        selectedHour,
                        selectedMinute,
                        false
                    ).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9E1B), contentColor = Color.Black)
            ) { Text("Set Alarm") }

            Button(
                onClick = { onTestAlarm() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C), contentColor = Color.White)
            ) { Text("Test +1 Min") }
        }

        if (hasActiveAlarm) {
            Button(
                onClick = { onCancel() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C), contentColor = Color.White)
            ) { Text("Cancel Alarm") }
        }
    }
}
