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
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.morro.alarm.ui.onboarding.HowItWorksSlideshow
import com.morro.alarm.ui.onboarding.OnboardingGate
import com.morro.alarm.ui.onboarding.PersonalizationStorage
import com.morro.alarm.ui.onboarding.PersonalizedQuestions
import com.morro.alarm.ui.splash.MorroGhostSplash
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.MorroTheme
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroGradientBorder
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
                var slideshowDone by remember { mutableStateOf(alreadyOnboarded) }
                var questionsDone by remember {
                    mutableStateOf(alreadyOnboarded || PersonalizationStorage.isComplete(this@MainActivity))
                }

                when {
                    !splashDone -> MorroGhostSplash(
                        onDismiss = { splashDone = true },
                        modifier = Modifier.fillMaxSize()
                    )

                    !questionsDone -> PersonalizedQuestions(onComplete = { questionsDone = true })

                    !slideshowDone -> HowItWorksSlideshow(
                        onFinished = {
                            OnboardingGate.markComplete(this@MainActivity)
                            slideshowDone = true
                        }
                    )

                    !hasOnboarded -> {
                        // The single permission screen; Continue writes onboarding_completed.
                        val intent = Intent(this@MainActivity, OnboardingActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
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

    val missionBadgeText = when (missionType) {
        MissionType.VOICE_COMMITMENT, MissionType.VOICE_MEMO -> "VOICE COMMITMENT"
        MissionType.QR_CODE, MissionType.QR_BARCODE -> "QR SCAN"
        MissionType.PHOTO_MISSION, MissionType.PHOTO_OBJECT -> "PHOTO HUNT · ${photoItems.size} ITEMS"
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

    val dayFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val digitalTime = remember(clockCal) {
        "${dayFormat.format(clockCal.time).uppercase()} • ${timeFormat.format(clockCal.time)}"
    }

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
        // Dawn sky sits behind the dial: an arc at the horizon, barely there.
        DawnSkyGlow(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MORRO",
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    letterSpacing = 6.sp
                )
                Row(
                    modifier = Modifier
                        .clip(MorroShapes.Pill)
                        .background(colors.surfaceCard)
                        .morroGradientBorder(
                            shape = MorroShapes.Pill,
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(colors.borderSubtle, colors.borderSubtle)
                            )
                        )
                        .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = colors.accentTactical,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = { showPrivacyPolicy = true }) {
                        Text(
                            text = "Privacy",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.accentTactical
                        )
                    }
                }
            }

            // ── Volume warning (warn, never auto-change the stream) ──────────
            if (alarmVolume == 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MorroShapes.Card)
                        .background(colors.surfaceCard)
                        .morroGradientBorder(
                            shape = MorroShapes.Card,
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    colors.accentTactical,
                                    colors.accentTactical.copy(alpha = 0.5f)
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = colors.accentTactical,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Alarm volume is off — your promise will play silently.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = colors.accentTactical
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.10f))

            // ── Hero clock ───────────────────────────────────────────────────
            MorroAnalogClock(size = 264.dp, calendar = clockCal)

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = digitalTime,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                letterSpacing = 1.2.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Bento mission dock ───────────────────────────────────────────
            if (!showAlarmBuilder) {
                MissionDock(
                    dockTime = dockTime,
                    missionBadgeText = missionBadgeText,
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
                                    "Exact alarm access is off — allow it in Settings to arm the alarm.",
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
 * Bento mission dock: one card that states the alarm, its mission, and the one action that opens
 * the setup sheet. It replaces both the hollow instruction box and the floating + button, so
 * there is exactly one obvious next step on the dashboard.
 */
@Composable
private fun MissionDock(
    dockTime: String?,
    missionBadgeText: String,
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
            .background(colors.surfaceElevated)
            .morroGradientBorder(
                shape = MorroShapes.Card,
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        colors.accentTactical.copy(alpha = if (armed) 0.85f else 0.35f),
                        colors.accentBubbly.copy(alpha = if (armed) 0.85f else 0.35f)
                    )
                )
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WAKE-UP MISSION",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textTertiary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            if (armed) colors.warmYellow else colors.textTertiary,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (armed) "ARMED" else "IDLE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (armed) colors.warmYellow else colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (dockTime != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dockTime,
                    fontSize = 40.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    color = colors.textPrimary
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = missionBadgeText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Switch(
                        checked = hasActiveAlarm,
                        onCheckedChange = onArmToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = colors.warmYellow,
                            uncheckedThumbColor = colors.textSecondary,
                            uncheckedTrackColor = colors.surfaceLow
                        )
                    )
                }
            }
        } else {
            Text(
                text = "No alarm set",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "One time, one mission, one promise.",
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        MorroPrimaryButton(
            text = if (dockTime == null) "Set Wake-Up Mission +" else "Change Wake-Up Mission",
            onClick = onSetAlarm,
            modifier = Modifier.fillMaxWidth()
        )

        if (BuildConfig.DEBUG) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "TEST 5S",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = colors.textTertiary,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(MorroShapes.Pill)
                    .background(colors.surfaceCard)
                    .clickable(onClick = onTestAlarm)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Dawn sky: a dusk-blue to orange to pink arc low behind the clock, at 0.10-0.30 alpha. It gives
 * the black canvas a horizon without ever turning the app light.
 */
@Composable
private fun DawnSkyGlow(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.52f

        // Sky wash above the horizon.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF16243F).copy(alpha = 0.30f),
                    MorroColors.AccentTactical.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset(w * 0.50f, horizon),
                radius = w * 0.95f
            )
        )

        // The arc itself: a wide ellipse, only its top edge drawn.
        drawArc(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2A3F63).copy(alpha = 0.22f),
                    MorroColors.AccentTactical.copy(alpha = 0.20f),
                    MorroColors.AccentBubbly.copy(alpha = 0.10f)
                ),
                startY = horizon - h * 0.22f,
                endY = h * 0.70f
            ),
            startAngle = 188f,
            sweepAngle = 164f,
            useCenter = false,
            topLeft = Offset(-w * 0.32f, horizon - h * 0.20f),
            size = Size(w * 1.64f, h * 0.46f),
            style = Stroke(width = 2.dp.toPx())
        )
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
