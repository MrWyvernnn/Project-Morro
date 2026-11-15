package com.morro.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.morro.alarm.mission.MissionPrefs
import com.morro.alarm.mission.MissionType
import com.morro.alarm.ui.components.AlarmBuilderSheet
import com.morro.alarm.ui.components.AlarmBuilderSheetContent
import com.morro.alarm.ui.components.MorroAnalogClock
import com.morro.alarm.ui.components.MorroPrimaryButton
import com.morro.alarm.ui.components.MorroPrivacyPolicySheet
import com.morro.alarm.ui.onboarding.OnboardingGate
import com.morro.alarm.ui.onboarding.PersonalizationStorage
import com.morro.alarm.ui.onboarding.PersonalizedQuestions
import com.morro.alarm.ui.splash.MorroGhostSplash
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.MorroTheme
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroHairline
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MorroTheme {
                // Orphan temp cleanup must run after the Compose context is live.
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
                val hasOnboarded = remember {
                    prefs.getBoolean("onboarding_completed", false)
                }

                // One flag for the intro; a returning person lands straight on the dashboard.
                val alreadyOnboarded = OnboardingGate.isComplete(this@MainActivity)
                var splashDone by remember { mutableStateOf(alreadyOnboarded) }
                var questionsDone by remember {
                    mutableStateOf(alreadyOnboarded || PersonalizationStorage.isComplete(this@MainActivity))
                }

                when {
                    !splashDone -> MorroGhostSplash(
                        onDismiss = { splashDone = true },
                        modifier = Modifier.fillMaxSize()
                    )

                    !questionsDone -> PersonalizedQuestions(
                        onComplete = {
                            // The intro is over the moment the questions are answered; there is no
                            // explainer step between the promise and the app.
                            OnboardingGate.markComplete(this@MainActivity)
                            questionsDone = true
                        }
                    )

                    !hasOnboarded -> {
                        // Launch from an effect, not from the composition body: a recomposition
                        // before finish() lands would otherwise start the screen twice.
                        LaunchedEffect(Unit) {
                            startActivity(
                                Intent(this@MainActivity, OnboardingActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                            finish()
                        }
                    }

                    else -> MainAlarmScreen(context = this@MainActivity)
                }
            }
        }
    }
}

@Composable
fun MainAlarmScreen(context: Context) {
    val prefs = remember { context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE) }
    var prefsUpdateTrigger by remember { mutableIntStateOf(0) }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    var alarmVolume by remember {
        mutableIntStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 1)
    }

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

    val savedAlarm = remember(prefsUpdateTrigger) { AlarmScheduler.getSavedAlarmTime(context) }
    val alarmTime = remember(prefsUpdateTrigger) { AlarmScheduler.getNextAlarmTime(context) }
    val hasActiveAlarm = alarmTime != null && alarmTime > System.currentTimeMillis()

    var showAlarmBuilder by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    val missionType = remember(prefsUpdateTrigger) { MissionPrefs.getMissionType(context) }
    val photoItems = remember(prefsUpdateTrigger) { MissionPrefs.getSelectedPhotoItems(context) }

    val missionLabel = when (missionType) {
        MissionType.VOICE_COMMITMENT, MissionType.VOICE_MEMO -> "Voice promise"
        MissionType.QR_CODE, MissionType.QR_BARCODE -> "QR scan"
        MissionType.PHOTO_MISSION, MissionType.PHOTO_OBJECT -> "Photo hunt · ${photoItems.size} items"
    }

    val colors = morroColors()

    // One clock, one source: both the dial and the readout read this value.
    var clockCal by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockCal = Calendar.getInstance()
            delay(1_000L)
        }
    }

    val dayFormat = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateLine = remember(clockCal) { dayFormat.format(clockCal.time) }
    val timeLine = remember(clockCal) { timeFormat.format(clockCal.time) }

    val dockTime = remember(prefsUpdateTrigger, savedAlarm) {
        savedAlarm?.let { (hour, minute) ->
            String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top bar: wordmark, one quiet action ───────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Morro",
                    fontSize = 18.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    letterSpacing = (-0.2).sp
                )
                TextButton(onClick = { showPrivacyPolicy = true }) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Privacy",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = colors.textSecondary
                    )
                }
            }

            // ── Volume warning (warn, never auto-change the stream) ───────────
            if (alarmVolume == 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MorroShapes.Card)
                        .background(colors.surfaceCard)
                        .morroHairline(radius = 16.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = colors.honey,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Alarm volume is off, so your promise will play silently.",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.12f))

            // ── Hero clock ────────────────────────────────────────────────────
            MorroAnalogClock(size = 248.dp, calendar = clockCal)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = timeLine,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateLine,
                fontSize = 12.5.sp,
                color = colors.textTertiary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Alarm dock ────────────────────────────────────────────────────
            if (!showAlarmBuilder) {
                AlarmDock(
                    dockTime = dockTime,
                    missionLabel = missionLabel,
                    hasActiveAlarm = hasActiveAlarm,
                    onSetAlarm = { showAlarmBuilder = true },
                    onArmToggle = { armed ->
                        val saved = savedAlarm
                        if (armed && saved != null) {
                            AlarmScheduler.scheduleDailyAlarm(context, saved.first, saved.second)
                        } else {
                            AlarmScheduler.cancelAlarm(context)
                        }
                        prefsUpdateTrigger++
                    },
                    onTestAlarm = { scheduleTestAlarmIn5Seconds(context) }
                )
            }
        }

        if (showAlarmBuilder) {
            val initialHour = savedAlarm?.first ?: 7
            val initialMinute = savedAlarm?.second ?: 0
            AlarmBuilderSheet(
                isVisible = showAlarmBuilder,
                onDismiss = { showAlarmBuilder = false },
                fabCenter = Offset(0f, 0f),
                content = {
                    AlarmBuilderSheetContent(
                        initialHour = initialHour,
                        initialMinute = initialMinute,
                        onDismiss = { showAlarmBuilder = false },
                        onSave = { hour, minute, mission ->
                            showAlarmBuilder = false

                            // Persist the mission the person picked, then arm the alarm.
                            MissionPrefs.saveMission(
                                context = context,
                                missionType = mission,
                                qrCode = MissionPrefs.getQrCode(context),
                                photoItems = MissionPrefs.getSelectedPhotoItems(context)
                            )

                            val armed = AlarmScheduler.scheduleDailyAlarm(context, hour, minute)
                            if (armed) {
                                Toast.makeText(
                                    context,
                                    "Alarm armed for %02d:%02d".format(hour, minute),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                // Never fail silently: the likely cause is revoked exact-alarm access.
                                Toast.makeText(
                                    context,
                                    "Exact alarm access is off. Allow it in Settings to arm the alarm.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            prefsUpdateTrigger++
                        }
                    )
                }
            )
        }

        if (showPrivacyPolicy) {
            MorroPrivacyPolicySheet(onDismiss = { showPrivacyPolicy = false })
        }
    }
}

/**
 * The alarm dock: one card that states the alarm, its mission and the one action that opens setup.
 *
 * Order is deliberate — the time is the biggest thing, the mission is a line of text, the arm
 * switch sits next to what it arms, and the button is the only filled surface on the screen.
 */
@Composable
private fun AlarmDock(
    dockTime: String?,
    missionLabel: String,
    hasActiveAlarm: Boolean,
    onSetAlarm: () -> Unit,
    onArmToggle: (Boolean) -> Unit,
    onTestAlarm: () -> Unit
) {
    val colors = morroColors()
    val armed = dockTime != null && hasActiveAlarm

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorroShapes.Card)
            .background(colors.surfaceCard)
            .morroHairline(radius = 16.dp)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Next alarm",
                fontSize = 12.sp,
                color = colors.textTertiary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (armed) colors.honey else colors.textDisabled,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = if (armed) "Armed" else "Idle",
                    fontSize = 12.sp,
                    color = if (armed) colors.textPrimary else colors.textTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (dockTime != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dockTime,
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1.4).sp,
                    color = colors.textPrimary
                )
                Switch(
                    checked = hasActiveAlarm,
                    onCheckedChange = onArmToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.canvas,
                        checkedTrackColor = colors.honey,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surfaceLow
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = missionLabel,
                fontSize = 13.sp,
                color = colors.textSecondary
            )
        } else {
            Text(
                text = "No alarm yet",
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "One time, one mission, one promise.",
                fontSize = 13.sp,
                color = colors.textTertiary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        MorroPrimaryButton(
            text = if (dockTime == null) "Set wake-up" else "Change wake-up",
            onClick = onSetAlarm,
            modifier = Modifier.fillMaxWidth()
        )

        if (BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Test alarm in 5s",
                fontSize = 11.sp,
                color = colors.textTertiary,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = onTestAlarm)
                    .padding(vertical = 4.dp)
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
        Toast.makeText(context, "Test alarm armed, firing in 5 seconds", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
