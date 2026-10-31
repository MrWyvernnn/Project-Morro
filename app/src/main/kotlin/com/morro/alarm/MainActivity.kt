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
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * MainActivity: Production Cyber-Defense Home Screen for Morro.
 *
 * Strict Rules Enforced:
 * - Root background: AmoledBlack (Color(0xFF000000))
 * - Card surfaces: MidnightBlue (Color(0xFF0A0A0F))
 * - Primary actions: ElectricCyan (Color(0xFF00F0FF))
 * - Alerting/Missing: AlertOrange (Color(0xFFFF4500))
 * - NO glassmorphism, NO glow shadows, NO gradients, NO emojis.
 */
class MainActivity : ComponentActivity() {

    private var isRecording by mutableStateOf(MemoRecorder.isCurrentlyRecording())
    private var recordingCountdown by mutableStateOf(15)
    private var hasTempRecorded by mutableStateOf(false)
    private var isPlayingPreview by mutableStateOf(MemoPlayer.isPlaying())
    private var permissionsGranted by mutableStateOf(false)
    private var shouldNavigateAfterPermissions by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioGranted = results[Manifest.permission.RECORD_AUDIO] == true || hasRecordAudioPermission()
        permissionsGranted = audioGranted
        shouldNavigateAfterPermissions = audioGranted
        if (audioGranted) {
            startRecording()
        } else {
            Toast.makeText(this, "Microphone permission required to record commitments.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasTempRecorded = MemoStorage.hasTempMemo(this)

        setContent {
            MorroTheme {
                var showExactAlarmRationale by remember { mutableStateOf(false) }

                val prefs = remember { getSharedPreferences("morro_prefs", Context.MODE_PRIVATE) }
                var hasOnboarded by remember { mutableStateOf(prefs.getBoolean("has_onboarded", false)) }

                LaunchedEffect(shouldNavigateAfterPermissions) {
                    if (shouldNavigateAfterPermissions && !hasOnboarded) {
                        delay(300)
                        prefs.edit().putBoolean("has_onboarded", true).commit()
                        hasOnboarded = true
                    }
                }

                if (!hasOnboarded) {
                    OnboardingScreen(
                        onComplete = {
                            prefs.edit().putBoolean("has_onboarded", true).commit()
                            hasOnboarded = true
                        },
                        onRequestPermissions = {
                            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(permissions.toTypedArray())

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !AlarmScheduler.canScheduleExactAlarms(this@MainActivity)) {
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
                    )
                } else {
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
                                        color = MorroColors.ElectricCyan,
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

                    MainAlarmScreen(
                        context = this@MainActivity,
                        isRecording = isRecording,
                        recordingCountdown = recordingCountdown,
                        hasTempRecorded = hasTempRecorded,
                        isPlayingPreview = isPlayingPreview,
                        onTimeSaved = { hour, minute ->
                            if (!AlarmScheduler.canScheduleExactAlarms(this@MainActivity)) {
                                showExactAlarmRationale = true
                            } else {
                                val scheduled = AlarmScheduler.scheduleDailyAlarm(this@MainActivity, hour, minute)
                                if (scheduled) {
                                    val formatted = AlarmScheduler.getFormattedTime(this@MainActivity)
                                        ?: String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                                    Toast.makeText(this@MainActivity, "Wake-up alarm set for $formatted", Toast.LENGTH_SHORT).show()
                                } else {
                                    showExactAlarmRationale = true
                                }
                            }
                        },
                        onCancelAlarm = {
                            AlarmScheduler.cancelAlarm(this@MainActivity)
                            Toast.makeText(this@MainActivity, "Alarm canceled", Toast.LENGTH_SHORT).show()
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
    }

    override fun onResume() {
        super.onResume()
        isRecording = MemoRecorder.isRecording()
        isPlayingPreview = MemoPlayer.isPlaying()
        hasTempRecorded = MemoStorage.hasTempMemo(this)
    }

    fun requestRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

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
            Toast.makeText(this, "Commitment saved.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Recording was too short or empty.", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAlarmScreen(
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
    var prefsUpdateTrigger by remember { mutableIntStateOf(0) }

    // Re-read state on lifecycle resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                prefsUpdateTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            prefsUpdateTrigger++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

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

    var currentTimeStr by remember {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        mutableStateOf(fmt.format(Date()))
    }
    var refreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            delay(1000)
            currentTimeStr = fmt.format(Date())
            refreshTick++
        }
    }

    val savedAlarm = remember(refreshTick, prefsUpdateTrigger) {
        AlarmScheduler.getSavedAlarmTime(context) ?: Pair(6, 30)
    }
    val alarmTime = remember(refreshTick, prefsUpdateTrigger) {
        AlarmScheduler.getNextAlarmTime(context)
    }
    val hasActiveAlarm = alarmTime != null && alarmTime > System.currentTimeMillis()

    val countdownStr = remember(alarmTime, refreshTick, prefsUpdateTrigger) {
        if (hasActiveAlarm) {
            val diffMillis = maxOf(0, alarmTime - System.currentTimeMillis())
            val hours = diffMillis / (1000 * 60 * 60)
            val minutes = (diffMillis / (1000 * 60)) % 60
            if (hours > 0) "ARMED: $hours hr $minutes min" else "ARMED: $minutes min"
        } else {
            "DISARMED"
        }
    }

    val hasRecordedMemo = remember(refreshTick, prefsUpdateTrigger) {
        MemoStorage.hasSavedMemo(context)
    }

    val displayCalendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, savedAlarm.first)
        set(Calendar.MINUTE, savedAlarm.second)
    }
    val timeOnlyFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeDigits = timeOnlyFormat.format(displayCalendar.time)

    var showBottomSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTime by remember { mutableStateOf(savedAlarm) }

    LaunchedEffect(savedAlarm, prefsUpdateTrigger) {
        selectedTime = savedAlarm
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = bottomSheetState,
            containerColor = MorroColors.MidnightBlue,
            contentColor = MorroColors.WhitePrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(MorroColors.WhiteSecondary.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SET WAKE-UP TIME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MorroColors.ElectricCyan,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                selectedTime = selectedTime.copy(first = (selectedTime.first + 1) % 24)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Increment hour",
                                tint = MorroColors.ElectricCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = String.format(Locale.getDefault(), "%02d", selectedTime.first),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = MorroColors.WhitePrimary
                        )
                        IconButton(
                            onClick = {
                                selectedTime = selectedTime.copy(first = (selectedTime.first + 23) % 24)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Decrement hour",
                                tint = MorroColors.ElectricCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Text(
                        text = ":",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = MorroColors.WhitePrimary,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                selectedTime = selectedTime.copy(second = (selectedTime.second + 1) % 60)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Increment minute",
                                tint = MorroColors.ElectricCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = String.format(Locale.getDefault(), "%02d", selectedTime.second),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = MorroColors.WhitePrimary
                        )
                        IconButton(
                            onClick = {
                                selectedTime = selectedTime.copy(second = (selectedTime.second + 59) % 60)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Decrement minute",
                                tint = MorroColors.ElectricCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MorroColors.ElectricCyan, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showBottomSheet = false
                            onTimeSaved(selectedTime.first, selectedTime.second)
                        }
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CONFIRM WAKE TIME",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MorroColors.AmoledBlack,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MorroColors.AmoledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MORRO DEFENSE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MorroColors.WhiteSecondary,
                    letterSpacing = 3.sp
                )
                Text(
                    text = currentTimeStr,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MorroColors.WhiteSecondary
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Time & Armed Status Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MorroColors.MidnightBlue, RoundedCornerShape(20.dp))
                    .border(
                        1.5.dp,
                        if (hasActiveAlarm) MorroColors.ElectricCyan.copy(alpha = 0.4f) else MorroColors.WhiteSecondary.copy(alpha = 0.1f),
                        RoundedCornerShape(20.dp)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { showBottomSheet = true }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SCHEDULED TARGET",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MorroColors.WhiteSecondary,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = timeDigits,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = MorroColors.WhitePrimary,
                    letterSpacing = (-2).sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (hasActiveAlarm) MorroColors.ElectricCyan else MorroColors.AlertOrange,
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = countdownStr,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasActiveAlarm) MorroColors.ElectricCyan else MorroColors.AlertOrange,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Status Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MorroColors.MidnightBlue, RoundedCornerShape(20.dp))
                    .border(
                        1.5.dp,
                        if (hasRecordedMemo) MorroColors.ElectricCyan.copy(alpha = 0.3f) else MorroColors.AlertOrange.copy(alpha = 0.3f),
                        RoundedCornerShape(20.dp)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COMMITMENT STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MorroColors.WhiteSecondary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = if (hasRecordedMemo) "RECORDED" else "NOT RECORDED YET",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasRecordedMemo) MorroColors.ElectricCyan else MorroColors.AlertOrange,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = if (hasRecordedMemo) {
                        "Voice clip locked. Plays in full before alarm dismissal."
                    } else {
                        "Not recorded yet. Fallback to prior clip active."
                    },
                    fontSize = 13.sp,
                    color = MorroColors.WhiteSecondary,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Slice 2a Audio Harness Controls
            MemoAudioHarness(
                isRecording = isRecording,
                recordingCountdown = recordingCountdown,
                hasTempRecorded = hasTempRecorded,
                isPlayingPreview = isPlayingPreview,
                hasRecordedMemo = hasRecordedMemo,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onCancelRecording = onCancelRecording,
                onPlayPreview = onPlayPreview,
                onSaveMemo = onSaveMemo
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Cyber-Defense restyled Slice 2a Voice Memo Audio Harness
 */
@Composable
fun MemoAudioHarness(
    isRecording: Boolean,
    recordingCountdown: Int,
    hasTempRecorded: Boolean,
    isPlayingPreview: Boolean,
    hasRecordedMemo: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayPreview: () -> Unit,
    onSaveMemo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MorroColors.MidnightBlue, RoundedCornerShape(20.dp))
            .border(1.5.dp, MorroColors.WhiteSecondary.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "AUDIO GATE HARNESS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MorroColors.WhiteSecondary,
            letterSpacing = 1.5.sp
        )

        when {
            isRecording -> {
                Text(
                    text = "RECORDING: ${recordingCountdown}s",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MorroColors.AlertOrange
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStopRecording,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MorroColors.AlertOrange,
                            contentColor = MorroColors.WhitePrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Stop", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onCancelRecording,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, MorroColors.WhiteSecondary.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MorroColors.WhiteSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }

            hasTempRecorded -> {
                Text(
                    text = if (isPlayingPreview) "Playing preview..." else "Temporary recording ready",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MorroColors.ElectricCyan
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPlayPreview,
                        enabled = !isPlayingPreview,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MorroColors.MidnightBlue,
                            contentColor = MorroColors.WhitePrimary
                        ),
                        border = BorderStroke(1.dp, MorroColors.WhiteSecondary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Preview")
                    }
                    Button(
                        onClick = onSaveMemo,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MorroColors.ElectricCyan,
                            contentColor = MorroColors.AmoledBlack
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(
                    onClick = onCancelRecording,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "Discard & re-record",
                        color = MorroColors.WhiteSecondary.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }

            else -> {
                Button(
                    onClick = onStartRecording,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MorroColors.ElectricCyan,
                        contentColor = MorroColors.AmoledBlack
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Record Commitment (15s)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
