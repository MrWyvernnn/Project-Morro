package com.morro.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.morro.alarm.mission.MissionPrefs
import com.morro.alarm.mission.MissionType
import com.morro.alarm.ui.components.AlarmBuilderSheet
import com.morro.alarm.ui.components.MorroAnalogClock
import com.morro.alarm.ui.components.MorroPrivacyPolicySheet
import com.morro.alarm.ui.theme.MorroTheme
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroTypography
import com.morro.alarm.ui.theme.morroShapes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MorroTheme {
                // Orphan temp cleanup MUST run after Compose context is live.
                // Capture context at composable level, then use in LaunchedEffect.
                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    try {
                        if (!MorroAudioRecorder.isRecording() && MemoStorage.hasTempMemo(context)) {
                            MemoStorage.clearTemp(context)
                            Log.d("MainActivity", "Cleaned up orphan temp recording file on cold start")
                        }
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Error cleaning orphan temp file", e)
                    }
                }

                val prefs = remember { getSharedPreferences("morro_prefs", Context.MODE_PRIVATE) }
                var hasOnboarded by remember { mutableStateOf(prefs.getBoolean("onboarding_completed", false)) }

                if (!hasOnboarded) {
                    // Launch the full 4-step OnboardingActivity instead of inline screen
                    val intent = Intent(this@MainActivity, OnboardingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                } else {
                    MainAlarmScreen(context = this@MainActivity)
                }
            }
        }
    }
}

@Composable
fun MainAlarmScreen(context: Context) {
    val prefs = remember { context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE) }
    var prefsUpdateTrigger by remember { mutableIntStateOf(0) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var alarmVolume by remember { mutableIntStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 1) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                prefsUpdateTrigger++
                alarmVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            prefsUpdateTrigger++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val savedAlarm = remember(prefsUpdateTrigger) {
        AlarmScheduler.getSavedAlarmTime(context)
    }
    val alarmTime = remember(prefsUpdateTrigger) {
        AlarmScheduler.getNextAlarmTime(context)
    }
    val hasActiveAlarm = alarmTime != null && alarmTime > System.currentTimeMillis()

    var showAlarmBuilder by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    val missionType = remember(prefsUpdateTrigger) { MissionPrefs.getMissionType(context) }
    val photoItems = remember(prefsUpdateTrigger) { MissionPrefs.getSelectedPhotoItems(context) }

    val missionBadgeText = when (missionType) {
        MissionType.VOICE_COMMITMENT, MissionType.VOICE_MEMO -> "VOICE MEMO"
        MissionType.QR_CODE, MissionType.QR_BARCODE -> "QR CODE"
        MissionType.PHOTO_MISSION, MissionType.PHOTO_OBJECT -> "PHOTO MISSION (${photoItems.size} items)"
    }

    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    // Digital time readout
    val digitalTime = remember(prefsUpdateTrigger) {
        val cal = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        "${dayFormat.format(cal.time).uppercase()} • ${timeFormat.format(cal.time)}"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Top Bar: "MORRO" top-left + "Privacy" top-right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MORRO",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    letterSpacing = 4.sp
                )
                TextButton(onClick = { showPrivacyPolicy = true }) {
                    Text(
                        text = "Privacy",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.accentTactical
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3.1 Zero Alarm Volume Warning Card (Warn, Don't Auto-Change)
            if (alarmVolume == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF261805), RoundedCornerShape(16.dp))
                        .border(1.5.dp, Color(0xFFFF6D00), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Alarm Volume Off",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Alarm volume is off — your commitment will play silently.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF9800),
                            lineHeight = 18.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Center Focus: The Analog Clock component
            MorroAnalogClock(size = 280.dp)

            Spacer(modifier = Modifier.height(24.dp))

            // Digital Readout: "WED, NOV 11 • 15:14:03"
            Text(
                text = digitalTime,
                style = typo.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = colors.textSecondary
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Instruction Box: Full-width rounded box with 1dp AccentTactical border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.accentTactical, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap + to configure your wake-up alarm & mission",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary Card (when alarm is set)
            if (savedAlarm != null && hasActiveAlarm) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, savedAlarm.first)
                    set(Calendar.MINUTE, savedAlarm.second)
                }
                val timeStr = timeFormat.format(cal.time)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, shapes.Card)
                        .border(1.dp, colors.borderActive, shapes.Card)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SCHEDULED TARGET",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = timeStr,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(colors.accentTactical, CircleShape)
                            )
                            Text(
                                text = missionBadgeText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentTactical
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Switch(
                                checked = hasActiveAlarm,
                                onCheckedChange = { armed ->
                                    if (armed) {
                                        AlarmScheduler.scheduleDailyAlarm(context, savedAlarm.first, savedAlarm.second)
                                    } else {
                                        AlarmScheduler.cancelAlarm(context)
                                    }
                                    prefsUpdateTrigger++
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = colors.accentTactical,
                                    uncheckedThumbColor = colors.textSecondary,
                                    uncheckedTrackColor = colors.surfaceLow
                                )
                            )

                            if (BuildConfig.DEBUG) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, colors.accentTactical.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .background(colors.surfaceLow)
                                        .clickable { scheduleTestAlarmIn5Seconds(context) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "TEST 5S",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accentTactical
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom-Right Floating Action Button (+ Button) - anchored with 16dp padding
        FloatingActionButton(
            onClick = { showAlarmBuilder = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(64.dp),
            containerColor = colors.accentTactical,
            contentColor = Color.Black,
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Configure Alarm",
                modifier = Modifier.size(32.dp)
            )
        }

        if (showAlarmBuilder) {
            val initialHour = savedAlarm?.first ?: 7
            val initialMinute = savedAlarm?.second ?: 0
            AlarmBuilderSheet(
                initialHour = savedAlarm?.first ?: 7,
                initialMinute = savedAlarm?.second ?: 0,
                onDismiss = { showAlarmBuilder = false },
                onSaveAndArm = { hour, minute, _ ->
                    showAlarmBuilder = false
                    Toast.makeText(context, "Alarm armed for %02d:%02d".format(hour, minute), Toast.LENGTH_SHORT).show()
                    prefsUpdateTrigger++
                },
                onOpenPrivacyPolicy = {
                    showAlarmBuilder = false
                    showPrivacyPolicy = true
                }
            )
        }

        if (showPrivacyPolicy) {
            MorroPrivacyPolicySheet(
                onDismiss = { showPrivacyPolicy = false }
            )
        }
    }
}

fun scheduleTestAlarmIn5Seconds(context: Context) {
    if (!BuildConfig.DEBUG) return
    val triggerAt = System.currentTimeMillis() + 5_000L
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        action = "com.morro.alarm.ACTION_TEST_ALARM"
        putExtra("is_test_alarm", true)
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        9999,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return

    val showIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val showPendingIntent = PendingIntent.getActivity(
        context,
        0,
        showIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    try {
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent), pendingIntent)
        Toast.makeText(context, "Test alarm armed — firing in 5 seconds", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}