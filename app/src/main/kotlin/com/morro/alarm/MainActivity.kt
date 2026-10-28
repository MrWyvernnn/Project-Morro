package com.morro.alarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * MainActivity: Production Home Screen for Morro.
 * Allows users to configure their daily wake-up alarm time via a TimePicker,
 * schedules exact alarms via AlarmScheduler, and displays tomorrow's commitment status.
 */
class MainActivity : ComponentActivity() {

    // Recording state observed by HomeScreen via Compose state
    private var isRecording by mutableStateOf(false)
    private var recordingCountdown by mutableStateOf(15)
    private var hasTempRecorded by mutableStateOf(false)
    private var isPlayingPreview by mutableStateOf(false)

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Microphone permission required to record commitments.", Toast.LENGTH_LONG).show()
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

        setContent {
            MorroTheme {
                var showExactAlarmRationale by remember { mutableStateOf(false) }

                // Rotation & singleton state sync
                LaunchedEffect(Unit) {
                    isRecording = MemoRecorder.isRecording()
                    isPlayingPreview = MemoPlayer.isPlaying()
                    hasTempRecorded = MemoStorage.hasTempMemo(this@MainActivity)
                }

                if (showExactAlarmRationale) {
                    AlertDialog(
                        onDismissRequest = { showExactAlarmRationale = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showExactAlarmRationale = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                data = Uri.parse("package:$packageName")
                                            }
                                            startActivity(intent)
                                        } catch (e: Exception) {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:$packageName")
                                            }
                                            startActivity(intent)
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = "OPEN SETTINGS",
                                    color = MorroColors.AlertOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExactAlarmRationale = false }) {
                                Text(
                                    text = "CANCEL",
                                    color = MorroColors.WhiteSecondary
                                )
                            }
                        },
                        containerColor = MorroColors.MidnightBlue,
                        title = {
                            Text(
                                text = "Exact Alarm Permission Required",
                                color = MorroColors.WhitePrimary,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = "Morro requires permission to schedule exact alarms so your wake-up call fires at the exact minute scheduled, even in deep sleep.",
                                color = MorroColors.WhiteSecondary
                            )
                        }
                    )
                }

                HomeScreen(
                    context = this,
                    isRecording = isRecording,
                    recordingCountdown = recordingCountdown,
                    hasTempRecorded = hasTempRecorded,
                    isPlayingPreview = isPlayingPreview,
                    onTimeSaved = { hour, minute ->
                        if (!AlarmScheduler.canScheduleExactAlarms(this)) {
                            showExactAlarmRationale = true
                        } else {
                            val scheduled = AlarmScheduler.scheduleDailyAlarm(this, hour, minute)
                            if (scheduled) {
                                val formatted = AlarmScheduler.getFormattedTime(this)
                                    ?: String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                                Toast.makeText(this, "Wake-up alarm set for $formatted", Toast.LENGTH_SHORT).show()
                            } else {
                                showExactAlarmRationale = true
                            }
                        }
                    },
                    onCancelAlarm = {
                        AlarmScheduler.cancelAlarm(this)
                        Toast.makeText(this, "Alarm canceled", Toast.LENGTH_SHORT).show()
                    },
                    onStartRecording = { startRecording() },
                    onStopRecording = { stopRecording() },
                    onCancelRecording = { cancelRecording() },
                    onPlayPreview = { playPreview() },
                    onSaveMemo = { saveMemo() },
                    onTickCountdown = {
                        if (recordingCountdown > 0) {
                            recordingCountdown -= 1
                        }
                        if (recordingCountdown <= 0) {
                            stopRecording()
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isRecording = MemoRecorder.isRecording()
        isPlayingPreview = MemoPlayer.isPlaying()
        hasTempRecorded = MemoStorage.hasTempMemo(this)
    }

    /** Triggers RECORD_AUDIO permission request — only when the user taps Record. */
    fun requestRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Reports whether RECORD_AUDIO is currently granted. */
    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Recording lifecycle helpers — only called from Compose onClick handlers. */
    private fun startRecording() {
        if (!hasRecordAudioPermission()) {
            requestRecordAudioPermission()
            return
        }
        isRecording = true
        recordingCountdown = 15
        hasTempRecorded = false

        MemoRecorder.startRecording(
            this,
            onError = { err ->
                Log.e("Morro", "Recording error: $err")
                Toast.makeText(this, "Recording error: $err", Toast.LENGTH_SHORT).show()
                isRecording = false
            },
            onMaxDurationReached = {
                recordingCountdown = 0
                stopRecording()
            }
        )
    }

    private fun stopRecording() {
        val stopped = MemoRecorder.stopRecording(this)
        isRecording = false
        hasTempRecorded = stopped && MemoStorage.hasTempMemo(this)
    }

    private fun cancelRecording() {
        MemoRecorder.cancelRecording(this)
        isRecording = false
        recordingCountdown = 15
        hasTempRecorded = false
        isPlayingPreview = false
    }

    private fun playPreview() {
        isPlayingPreview = true
        MemoPlayer.startPlayback(
            this,
            MemoStorage.getTempPath(this),
            onCompletion = { isPlayingPreview = false },
            onError = { err ->
                isPlayingPreview = false
                Toast.makeText(this, "Playback error: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun saveMemo() {
        val success = MemoStorage.saveMemo(this)
        if (success) {
            isRecording = false
            recordingCountdown = 15
            hasTempRecorded = false
            isPlayingPreview = false
            Toast.makeText(this, "Commitment saved!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Recording was too short or empty.", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    context: Context,
    isRecording: Boolean,
    recordingCountdown: Int,
    hasTempRecorded: Boolean,
    isPlayingPreview: Boolean,
    onTimeSaved: (Int, Int) -> Unit,
    onCancelAlarm: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayPreview: () -> Unit,
    onSaveMemo: () -> Unit,
    onTickCountdown: () -> Unit
) {
    val prefs = remember { context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE) }
    var prefsUpdateTrigger by remember { mutableStateOf(0) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            prefsUpdateTrigger++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Proactive Compose Fail-safe for 15s cap
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording && recordingCountdown > 0) {
                delay(1000)
                onTickCountdown()
            }
            if (isRecording && recordingCountdown <= 0) {
                MemoRecorder.stopRecording(context)
                onStopRecording()
            }
        }
    }

    // Current ambient time tick
    var currentTimeStr by remember {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        mutableStateOf(fmt.format(Date()))
    }

    var refreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        while (true) {
            delay(1000)
            currentTimeStr = fmt.format(Date())
            refreshTick++
        }
    }

    // Retrieve saved alarm time or fallback to 6:30 AM
    val savedAlarm = remember(refreshTick, prefsUpdateTrigger) {
        AlarmScheduler.getSavedAlarmTime(context) ?: Pair(6, 30)
    }

    val alarmTime = remember(refreshTick, prefsUpdateTrigger) {
        AlarmScheduler.getNextAlarmTime(context)
    }
    val hasActiveAlarm = alarmTime != null && alarmTime > System.currentTimeMillis()

    // Countdown calculation
    val countdownStr = remember(alarmTime, refreshTick, prefsUpdateTrigger) {
        if (hasActiveAlarm) {
            val diffMillis = maxOf(0, alarmTime - System.currentTimeMillis())
            val hours = diffMillis / (1000 * 60 * 60)
            val minutes = (diffMillis / (1000 * 60)) % 60
            if (hours > 0) "Rings in $hours hr $minutes min" else "Rings in $minutes min"
        } else {
            "Alarm inactive"
        }
    }

    var showTimePicker by remember { mutableStateOf(false) }

    // Format display for massive typography
    val displayCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, savedAlarm.first)
        set(Calendar.MINUTE, savedAlarm.second)
    }
    val timeOnlyFormat = SimpleDateFormat("h:mm", Locale.getDefault())
    val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
    val timeDigits = timeOnlyFormat.format(displayCalendar.time)
    val amPm = amPmFormat.format(displayCalendar.time)

    val hasRecordedMemo = remember(refreshTick, prefsUpdateTrigger) { MemoStorage.hasSavedMemo(context) }

    if (showTimePicker) {
        MorroTimePickerDialog(
            initialHour = savedAlarm.first,
            initialMinute = savedAlarm.second,
            onConfirm = { hour, minute ->
                showTimePicker = false
                onTimeSaved(hour, minute)
            },
            onDismiss = { showTimePicker = false }
        )
    }

    Scaffold(
        containerColor = MorroColors.AmoledBlack,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // App header & ambient clock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MORRO",
                    style = MaterialTheme.typography.titleLarge,
                    color = MorroColors.WhitePrimary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
                Text(
                    text = currentTimeStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MorroColors.WhiteTertiary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hero Alarm Time Card with Massive Typography (displayLarge)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MorroColors.MidnightBlue),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "DAILY WAKE-UP",
                        style = MaterialTheme.typography.labelMedium,
                        color = MorroColors.WhiteSecondary,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Massive, bold typography for the time display
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = timeDigits,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black,
                            color = MorroColors.WhitePrimary,
                            letterSpacing = (-2).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = amPm,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MorroColors.AlertOrange,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Active countdown / status badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (hasActiveAlarm) MorroColors.ElectricCyan else MorroColors.WhiteTertiary,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = countdownStr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasActiveAlarm) MorroColors.ElectricCyan else MorroColors.WhiteTertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wake-Up Mission Toggle wrapped in padded Card with MidnightBlue background (16dp internal padding)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MorroColors.MidnightBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                var missionType by remember {
                    mutableStateOf(AlarmScheduler.MissionType.MATH)
                }

                LaunchedEffect(Unit) {
                    val prefs = context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE)
                    val savedType = prefs.getString(AlarmScheduler.KEY_MISSION_TYPE, null)
                    missionType = savedType?.let {
                        AlarmScheduler.MissionType.valueOf(it)
                    } ?: AlarmScheduler.DEFAULT_MISSION_TYPE
                }

                fun saveMissionType(type: AlarmScheduler.MissionType) {
                    missionType = type
                    context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString(AlarmScheduler.KEY_MISSION_TYPE, type.name)
                        .commit()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Math Puzzle Option
                    Button(
                        onClick = { saveMissionType(AlarmScheduler.MissionType.MATH) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (missionType == AlarmScheduler.MissionType.MATH) {
                                MorroColors.AlertOrange
                            } else {
                                MorroColors.AmoledBlack
                            },
                            contentColor = if (missionType == AlarmScheduler.MissionType.MATH) {
                                MorroColors.AmoledBlack
                            } else {
                                MorroColors.WhitePrimary
                            }
                        ),
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    ) {
                        Text("Math Puzzle")
                    }

                    // Type Affirmation Option
                    Button(
                        onClick = { saveMissionType(AlarmScheduler.MissionType.AFFIRMATION) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (missionType == AlarmScheduler.MissionType.AFFIRMATION) {
                                MorroColors.AlertOrange
                            } else {
                                MorroColors.AmoledBlack
                            },
                            contentColor = if (missionType == AlarmScheduler.MissionType.AFFIRMATION) {
                                MorroColors.AmoledBlack
                            } else {
                                MorroColors.WhitePrimary
                            }
                        ),
                        shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                    ) {
                        Text("Type Affirmation")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Prominent AlertOrange Edit Button (Only opens TimePickerDialog)
            Button(
                onClick = { showTimePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MorroColors.AlertOrange,
                    contentColor = MorroColors.AmoledBlack
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SET WAKE-UP TIME",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Push the "Tomorrow's Commitment" status and the "Record" button to the bottom third of the screen with 32dp whitespace above
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(32.dp))

            // Commitment Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MorroColors.MidnightBlue),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (hasRecordedMemo) MorroColors.SuccessGreen.copy(alpha = 0.4f) else MorroColors.AlertOrange.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOMORROW'S COMMITMENT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MorroColors.WhiteSecondary,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (hasRecordedMemo) MorroColors.SuccessGreen else MorroColors.AlertOrange,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (hasRecordedMemo) "Recorded" else "Missing",
                                color = if (hasRecordedMemo) MorroColors.ElectricCyan else MorroColors.AlertOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Text(
                        text = if (hasRecordedMemo) {
                            "Your promise is locked in and will play in your voice at wake-up."
                        } else {
                            "No new memo recorded. Your previous memo will be replayed as fallback."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MorroColors.WhiteTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Record Commitment Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MorroColors.MidnightBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "RECORD YOUR COMMITMENT",
                        style = MaterialTheme.typography.titleMedium,
                        color = MorroColors.WhitePrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Speak your promise for tomorrow (max 15 seconds)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MorroColors.WhiteSecondary,
                        textAlign = TextAlign.Center
                    )

                    if (isRecording) {
                        Text(
                            text = "Recording: $recordingCountdown s",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MorroColors.AlertOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onStopRecording() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MorroColors.SuccessGreen,
                                contentColor = MorroColors.AmoledBlack
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop Recording")
                        }
                        OutlinedButton(
                            onClick = { onCancelRecording() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MorroColors.ErrorRed
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    } else if (hasTempRecorded) {
                        Text(
                            text = if (isPlayingPreview) "Playing preview…" else "Commitment recorded",
                            style = MaterialTheme.typography.titleMedium,
                            color = MorroColors.ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onPlayPreview() },
                                enabled = !isPlayingPreview,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MorroColors.AlertOrange,
                                    contentColor = MorroColors.AmoledBlack
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Preview")
                            }
                            OutlinedButton(
                                onClick = { onCancelRecording() },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MorroColors.WhiteSecondary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Re-record")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onSaveMemo() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MorroColors.SuccessGreen,
                                contentColor = MorroColors.AmoledBlack
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Commitment")
                        }
                    } else {
                        // Record button: full-width (fillMaxWidth()), heavily rounded (CircleShape), bold AlertOrange coloring
                        Button(
                            onClick = { onStartRecording() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MorroColors.AlertOrange,
                                contentColor = MorroColors.AmoledBlack
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Start Recording",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // Cancel / Disarm Button (if alarm is scheduled)
            if (hasActiveAlarm) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onCancelAlarm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MorroColors.ErrorRed
                    ),
                    border = BorderStroke(1.dp, MorroColors.ErrorRed.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "CANCEL ALARM",
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorroTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }
            ) {
                Text(
                    text = "SAVE",
                    color = MorroColors.AlertOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CANCEL",
                    color = MorroColors.WhiteSecondary,
                    fontSize = 16.sp
                )
            }
        },
        containerColor = MorroColors.MidnightBlue,
        title = {
            Text(
                text = "Select Wake-Up Time",
                style = MaterialTheme.typography.titleMedium,
                color = MorroColors.WhitePrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MorroColors.AmoledBlack,
                        clockDialSelectedContentColor = MorroColors.AmoledBlack,
                        clockDialUnselectedContentColor = MorroColors.WhitePrimary,
                        selectorColor = MorroColors.AlertOrange,
                        containerColor = MorroColors.MidnightBlue,
                        periodSelectorBorderColor = MorroColors.AlertOrange,
                        periodSelectorSelectedContainerColor = MorroColors.AlertOrange,
                        periodSelectorUnselectedContainerColor = MorroColors.AmoledBlack,
                        periodSelectorSelectedContentColor = MorroColors.AmoledBlack,
                        periodSelectorUnselectedContentColor = MorroColors.WhiteSecondary,
                        timeSelectorSelectedContainerColor = MorroColors.AlertOrange,
                        timeSelectorUnselectedContainerColor = MorroColors.AmoledBlack,
                        timeSelectorSelectedContentColor = MorroColors.AmoledBlack,
                        timeSelectorUnselectedContentColor = MorroColors.WhitePrimary
                    )
                )
            }
        }
    )
}
