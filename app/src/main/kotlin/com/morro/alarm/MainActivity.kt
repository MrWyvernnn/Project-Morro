package com.morro.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.graphics.Color as AndroidColor
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.morro.alarm.ui.components.MorroCard
import com.morro.alarm.ui.components.MorroGhostMascot
import com.morro.alarm.ui.components.MorroPrimaryButton
import com.morro.alarm.ui.components.MorroSettingsSheet
import com.morro.alarm.ui.components.MorroSky
import com.morro.alarm.ui.components.MorroSkyScrim
import com.morro.alarm.ui.components.MorroStatusDot
import com.morro.alarm.ui.components.MorroSwitch
import com.morro.alarm.ui.components.MorroPrivacyPolicySheet
import com.morro.alarm.ui.onboarding.IntroStep
import com.morro.alarm.ui.onboarding.OnboardingGate
import com.morro.alarm.ui.onboarding.PaywallScreen
import com.morro.alarm.ui.onboarding.PersonalizingScreen
import com.morro.alarm.ui.onboarding.PersonalizationStorage
import com.morro.alarm.ui.onboarding.PersonalizedQuestions
import com.morro.alarm.ui.onboarding.nextIntroStep
import com.morro.alarm.ui.splash.MorroGhostSplash
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.MorroTheme
import com.morro.alarm.ui.theme.MorroThemeChoice
import com.morro.alarm.ui.theme.ThemePrefs
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroGlassBorder
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars and keep their icons light: the canvas is black in every
        // state, so the default auto light/dark icons would disappear in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

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
                // The Morro screen plays on every cold start, not only the first: the app opens as
                // itself for a beat before it becomes the alarm. rememberSaveable keeps a rotation
                // from replaying it, and a tap still skips it.
                var splashDone by rememberSaveable { mutableStateOf(false) }
                // The paywall is a visual beat for now: no billing is wired, and it can be
                // skipped. Their state lives in the saved instance, like the splash, so the intro
                // replays until it is truly finished. There is no sign-in state because there is
                // no account: entitlement comes from the Google account on the device.
                var questionsDone by rememberSaveable {
                    mutableStateOf(alreadyOnboarded || PersonalizationStorage.isComplete(this@MainActivity))
                }
                // Seeded from their own flags, not from the "has met the app" flag written at the
                // questions step: killing the app during Personalizing used to skip straight to
                // Permissions and the rest of the intro never ran.
                var personalizeDone by rememberSaveable {
                    mutableStateOf(prefs.getBoolean("onboarding_personalize_done", false))
                }
                var paywallDone by rememberSaveable {
                    mutableStateOf(prefs.getBoolean("onboarding_paywall_done", false))
                }

                // The order lives in `nextIntroStep`, where it is a pure function the unit tests
                // can hold still. This `when` only maps a step onto the screen that draws it.
                when (
                    nextIntroStep(
                        splashDone = splashDone,
                        questionsDone = questionsDone,
                        personalizeDone = personalizeDone,
                        paywallDone = paywallDone,
                        permissionsDone = hasOnboarded
                    )
                ) {
                    IntroStep.Splash -> MorroGhostSplash(
                        onDismiss = { splashDone = true },
                        modifier = Modifier.fillMaxSize()
                    )

                    IntroStep.Questions -> PersonalizedQuestions(
                        onComplete = {
                            // The answers are saved by the questions screen itself; the intro flag is
                            // only about "this person has met the app", not about the answers.
                            OnboardingGate.markComplete(this@MainActivity)
                            questionsDone = true
                        }
                    )

                    // Answers are on disk already; this beat hands off to choosing a plan.
                    IntroStep.Personalizing -> PersonalizingScreen(
                        onDone = {
                            prefs.edit().putBoolean("onboarding_personalize_done", true).apply()
                            personalizeDone = true
                        }
                    )

                    IntroStep.Paywall -> PaywallScreen(
                        onContinue = {
                            prefs.edit().putBoolean("onboarding_paywall_done", true).apply()
                            paywallDone = true
                        }
                    )

                    IntroStep.Permissions -> {
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

                    IntroStep.Dashboard -> MainAlarmScreen(context = this@MainActivity)
                }
            }
        }
    }
}

@Composable
fun MainAlarmScreen(context: Context) {
    val prefs = remember { context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE) }
    var prefsUpdateTrigger by remember { mutableIntStateOf(0) }

    val haptics = LocalHapticFeedback.current
    var alarmVolumeSnapshot by remember {
        mutableStateOf(AlarmVolumeGuard.readAlarmVolume(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                prefsUpdateTrigger++
                alarmVolumeSnapshot = AlarmVolumeGuard.readAlarmVolume(context)
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

    // A ring that died before AlarmActivity could re-arm (process death, crash, force-stop) leaves
    // the one-shot trigger consumed. If the person never cancelled, put the next occurrence back.
    var missedRing by remember { mutableStateOf<AlarmEvent?>(null) }
    LaunchedEffect(Unit) {
        // Inference first: the re-arm below moves the expected trigger to tomorrow, which would
        // erase the evidence this check depends on.
        val missed = MissedAlarmLog.evaluateMissedRing(context)
        if (missed != null && !MissedAlarmLog.isDismissed(context, missed)) {
            missedRing = missed
        }

        if (!AlarmScheduler.isArmed(context)) return@LaunchedEffect
        val pending = AlarmScheduler.getNextAlarmTime(context)
        if (pending != null && pending > System.currentTimeMillis()) return@LaunchedEffect
        AlarmScheduler.getSavedAlarmTime(context)?.let { (hour, minute) ->
            AlarmScheduler.scheduleDailyAlarm(context, hour, minute)
            prefsUpdateTrigger++
        }
    }

    val savedAlarm = remember(prefsUpdateTrigger) { AlarmScheduler.getSavedAlarmTime(context) }
    val alarmTime = remember(prefsUpdateTrigger) { AlarmScheduler.getNextAlarmTime(context) }
    val hasActiveAlarm = alarmTime != null && alarmTime > System.currentTimeMillis()

    var showAlarmBuilder by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showCompanionSheet by remember { mutableStateOf(false) }
    var wakeCheckEnabled by remember { mutableStateOf(WakeUpCheck.isEnabled(context)) }

    // Opening the app at all is an answer: a pending "Still up?" has served its purpose, and its
    // 90-second escalation must not ring while the person is looking at the dashboard.
    LaunchedEffect(Unit) {
        WakeUpCheck.resolve(context)
    }

    val missionType = remember(prefsUpdateTrigger) { MissionPrefs.getMissionType(context) }
    val photoItems = remember(prefsUpdateTrigger) { MissionPrefs.getSelectedPhotoItems(context) }
    // Build.MANUFACTURER never changes at runtime, so this is read once per screen.
    val oemGuidance = remember { OemReliability.current() }

    val missionLabel = when (missionType) {
        MissionType.VOICE_COMMITMENT, MissionType.VOICE_MEMO -> "Voice promise"
        MissionType.QR_CODE, MissionType.QR_BARCODE -> "QR scan"
        MissionType.PHOTO_MISSION, MissionType.PHOTO_OBJECT -> "Photo hunt, ${photoItems.size} objects"
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
    // Format once per day, not once per second: every tick recreating this string was pure churn.
    val dateLine = remember(clockCal.get(Calendar.YEAR), clockCal.get(Calendar.DAY_OF_YEAR)) {
        dayFormat.format(clockCal.time)
    }

    // One entrance for the clock, a beat later for the dock: the screen assembles on springs
    // instead of appearing all at once.
    val reducedMotion = rememberReducedMotion()
    val clockReveal = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val dockReveal = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            clockReveal.animateTo(1f, MorroSpring.FloatSpringDefault)
        }
    }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            delay(140L)
            dockReveal.animateTo(1f, MorroSpring.FloatSpringPlayful)
        }
    }

    val dockTime = remember(prefsUpdateTrigger, savedAlarm) {
        savedAlarm?.let { (hour, minute) ->
            String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        }
    }

    val anySheetOpen = showAlarmBuilder || showSettings || showPrivacyPolicy || showCompanionSheet
    val backdropScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (anySheetOpen && !reducedMotion) 0.94f else 1f,
        animationSpec = MorroSpring.FloatSpringDefault,
        label = "backdropScale"
    )
    val backdropAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (anySheetOpen) 0.65f else 1f,
        animationSpec = tween(220, easing = MorroMotion.EnterEasing),
        label = "backdropAlpha"
    )
    val backdropBlur by animateDpAsState(
        targetValue = if (anySheetOpen && !reducedMotion) 10.dp else 0.dp,
        animationSpec = tween(220, easing = MorroMotion.EnterEasing),
        label = "backdropBlur"
    )
    val scope = rememberCoroutineScope()
    val clockTapScale = remember { Animatable(1f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = backdropScale
                    scaleY = backdropScale
                    alpha = backdropAlpha
                }
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backdropBlur > 0.5.dp) {
                        Modifier.blur(backdropBlur)
                    } else {
                        Modifier
                    }
                )
        ) {
            MorroSky(intensity = 0.85f)
            MorroSkyScrim(alpha = 0.60f)

            Column(
                modifier = Modifier
                    // A phone column on a tablet: the clock and dock stay one reading width instead of
                    // stretching to the bezels.
                    .fillMaxHeight()
                    .widthIn(max = 520.dp)
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // ── Top bar: wordmark + interactive mascot chip, Privacy action ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Morro",
                        fontSize = 18.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(
                        modifier = Modifier
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colors.surfaceCard.copy(alpha = 0.72f))
                            .morroGlassBorder(radius = 50.dp, topAlpha = 0.22f, bottomAlpha = 0.04f)
                            .clickable { showCompanionSheet = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MorroGhostMascot(
                            modifier = Modifier.size(width = 24.dp, height = 26.dp),
                            happy = true,
                            interactive = false
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Poke",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.honey
                        )
                    }
                }
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
            if (alarmVolumeSnapshot.tooQuiet) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = colors.honey,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (alarmVolumeSnapshot.volume == 0) {
                            "Alarm volume is off. Your promise will play silently."
                        } else {
                            "Alarm volume is low (${alarmVolumeSnapshot.volume}/${alarmVolumeSnapshot.max}). Turn it up so you don't sleep through it."
                        },
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = colors.textTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.12f))

            // ── Hero clock ────────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = clockReveal.value
                        translationY = (1f - clockReveal.value) * 16f
                        scaleX = clockTapScale.value
                        scaleY = clockTapScale.value
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!reducedMotion) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                clockTapScale.snapTo(0.955f)
                                clockTapScale.animateTo(1f, MorroSpring.FloatSpringPlayful)
                            }
                        }
                    }
            ) {
                MorroAnalogClock(size = 244.dp, calendar = clockCal)
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = dateLine,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.2.sp,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── A ring that did not happen ────────────────────────────────────
            missedRing?.let { event ->
                MissedRingCard(
                    event = event,
                    onFix = {
                        missedRing = null
                        MissedAlarmLog.dismiss(context, event)
                        context.startActivity(Intent(context, OemGuidanceActivity::class.java))
                    },
                    onDismiss = {
                        missedRing = null
                        MissedAlarmLog.dismiss(context, event)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Alarm dock ────────────────────────────────────────────────────
            AlarmDock(
                dockTime = dockTime,
                missionLabel = missionLabel,
                hasActiveAlarm = hasActiveAlarm,
                modifier = Modifier.graphicsLayer {
                    alpha = dockReveal.value
                    translationY = (1f - dockReveal.value) * 26f
                },
                onSetAlarm = { showAlarmBuilder = true },
                onArmToggle = { armed ->
                    // Confirm the state change on the same frame the switch moves.
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val saved = savedAlarm
                    if (armed && saved != null) {
                        val scheduled = AlarmScheduler.scheduleDailyAlarm(context, saved.first, saved.second)
                        if (!scheduled) {
                            // The switch springs back to Idle; say why instead of doing it silently.
                            Toast.makeText(
                                context,
                                "Exact alarm access is off. Allow it in Settings to arm the alarm.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        AlarmScheduler.cancelAlarm(context)
                    }
                    prefsUpdateTrigger++
                },
                onTestAlarm = { scheduleTestAlarmIn5Seconds(context) },
                onOpenSettings = { showSettings = true }
            )
            }
        }

        // Always composed: the sheet needs to stay on screen for its 192ms exit animation.
        val initialHour = savedAlarm?.first ?: 7
        val initialMinute = savedAlarm?.second ?: 0
        AlarmBuilderSheet(
            isVisible = showAlarmBuilder,
            onDismiss = { showAlarmBuilder = false },
            content = {
                    AlarmBuilderSheetContent(
                        initialHour = initialHour,
                        initialMinute = initialMinute,
                        onDismiss = { showAlarmBuilder = false },
                        onSave = { hour, minute, mission, qrCode, photoItems ->
                            showAlarmBuilder = false

                            // Persist exactly what the sheet was showing, then arm the alarm.
                            MissionPrefs.saveMission(
                                context = context,
                                missionType = mission,
                                qrCode = qrCode,
                                photoItems = photoItems
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

        if (showPrivacyPolicy) {
            MorroPrivacyPolicySheet(onDismiss = { showPrivacyPolicy = false })
        }

        if (showCompanionSheet) {
            MorroCompanionSheet(onDismiss = { showCompanionSheet = false })
        }

        // The wrapper already reads the stored theme; this state only makes the switch respond on
        // the same tap instead of waiting for the settings round trip.
        var autumnTheme by remember { mutableStateOf(ThemePrefs.isAutumn(context)) }

        // Every row in Settings is a door to a sheet that already exists, so nothing here can
        // disagree with the dashboard about what the alarm currently is.
        if (showSettings) {
            MorroSettingsSheet(
                wakeUpLabel = dockTime ?: "Not set",
                missionLabel = missionLabel,
                oemLabel = oemGuidance.brandLabel,
                wakeCheckEnabled = wakeCheckEnabled,
                themeChoice = if (autumnTheme) MorroThemeChoice.AUTUMN else MorroThemeChoice.MIDNIGHT,
                onAutumnThemeChange = { on ->
                    autumnTheme = on
                    ThemePrefs.set(
                        context,
                        if (on) MorroThemeChoice.AUTUMN else MorroThemeChoice.MIDNIGHT
                    )
                },
                onWakeCheckChange = { on ->
                    wakeCheckEnabled = on
                    WakeUpCheck.setEnabled(context, on)
                },
                onOpenWakeUp = {
                    showSettings = false
                    showAlarmBuilder = true
                },
                onOpenOemGuidance = {
                    showSettings = false
                    context.startActivity(Intent(context, OemGuidanceActivity::class.java))
                },
                onOpenPrivacy = {
                    showSettings = false
                    showPrivacyPolicy = true
                },
                onDismiss = { showSettings = false }
            )
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
    onTestAlarm: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = morroColors()
    val armed = dockTime != null && hasActiveAlarm
    val shape = morroShapes().Card
    val dockInteraction = remember { MutableInteractionSource() }

    val reducedMotion = rememberReducedMotion()
    val breathTransition = rememberInfiniteTransition(label = "dockTimeBreath")
    val timeBreath by breathTransition.animateFloat(
        initialValue = 0.998f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timeBreathScale"
    )

    val dockTimeColor by animateColorAsState(
        targetValue = if (armed) colors.textPrimary else colors.textTertiary,
        animationSpec = tween(220, easing = MorroMotion.EnterEasing),
        label = "dockTimeColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            // The whole dock opens setup (Fitts); the arm switch consumes its own taps.
            .pressScale(dockInteraction, pressedScale = 0.985f, releaseSpec = MorroSpring.FloatSpringPlayful)
            .clickable(
                interactionSource = dockInteraction,
                indication = null,
                onClick = onSetAlarm
            )
            .clip(shape)
            .background(colors.surfaceCard.copy(alpha = 0.78f))
            .morroGlassBorder(
                radius = 16.dp,
                topAlpha = if (armed) 0.28f else 0.18f,
                bottomAlpha = 0.05f,
                tint = if (armed) colors.honey else colors.textPrimary
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Next alarm",
                fontSize = 12.sp,
                letterSpacing = 0.2.sp,
                color = colors.textTertiary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // One pulse when the alarm arms, then still: the app's only celebratory motion.
                MorroStatusDot(armed = armed)
                Spacer(modifier = Modifier.width(8.dp))
                AnimatedContent(
                    targetState = armed,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically { if (targetState) -it / 2 else it / 2 })
                            .togetherWith(fadeOut(tween(140)))
                    },
                    label = "armedStatus"
                ) { isArmed ->
                    Text(
                        text = if (isArmed) "Armed" else "Idle",
                        fontSize = 12.sp,
                        color = if (isArmed) colors.textPrimary else colors.textTertiary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Settings lives on the card, not in a corner of the screen: this card is the one
                // place a person looks when nothing is set, so the way out belongs here.
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (dockTime != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val timeScale = if (hasActiveAlarm && !reducedMotion) timeBreath else 1f
                Text(
                    text = dockTime,
                    fontSize = 46.sp,
                    lineHeight = 50.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1.6).sp,
                    color = dockTimeColor,
                    maxLines = 1,
                    // Takes the remaining width so a large system font size cannot push the switch
                    // off the card.
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .graphicsLayer {
                            scaleX = timeScale
                            scaleY = timeScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                )
                MorroSwitch(
                    checked = hasActiveAlarm,
                    onCheckedChange = onArmToggle
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = missionLabel,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.textSecondary
            )
        } else {
            Text(
                text = "No wake-up set",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Choose a time and the mission that gets you up.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.textTertiary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

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
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTestAlarm)
                    // 48dp minimum target, even for this debug-only shortcut.
                    .heightIn(min = 48.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

/**
 * One quiet card for a morning that went wrong, with the one action that can stop it repeating.
 *
 * Two shapes reach here and they are not the same news. A ring that never happened is the serious
 * one. A ring that arrived late still woke the person, so the card says that instead of claiming
 * nothing happened — the alarm did its job, the phone was slow, and the setting to fix is the same.
 *
 * It only ever describes one event and only until it is acted on or dismissed, because a permanent
 * banner would become furniture and stop meaning anything. The copy names the time and the cause;
 * it never implies the person did something wrong.
 */
@Composable
private fun MissedRingCard(
    event: AlarmEvent,
    onFix: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = morroColors()
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val expectedLabel = remember(event.expectedAtMillis) {
        timeFormat.format(Date(event.expectedAtMillis))
    }
    val observedLabel = remember(event.observedAtMillis) {
        timeFormat.format(Date(event.observedAtMillis))
    }

    val title = when (event.kind) {
        AlarmEventKind.NEVER_FIRED -> "Your $expectedLabel alarm didn't ring on time."
        AlarmEventKind.LATE_FIRE -> "Your $expectedLabel alarm rang at $observedLabel."
    }
    val detail = when (event.kind) {
        AlarmEventKind.NEVER_FIRED -> "This phone stopped the app from starting. One setting fixes it."
        AlarmEventKind.LATE_FIRE -> "This phone held the alarm back. One setting fixes it."
    }

    MorroCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = detail,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colors.textTertiary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Show me how to fix this",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.honey,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .morroHairline(radius = 24.dp, color = colors.borderStrong)
                    .clickable(onClick = onFix)
                    .heightIn(min = 48.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorroCompanionSheet(
    onDismiss: () -> Unit,
) {
    val colors = morroColors()
    val reducedMotion = rememberReducedMotion()
    var pokeCount by remember { mutableIntStateOf(0) }

    val moodText = when (pokeCount) {
        0 -> "Ready to keep tomorrow's promise."
        1 -> "Boop! Wide awake and smiling."
        2 -> "Hehe! Feeling bouncy today."
        else -> "Wheee! Celebratory spin ($pokeCount pokes)."
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(colors.borderSubtle, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MorroGhostMascot(
                modifier = Modifier.size(width = 152.dp, height = 168.dp),
                happy = true,
                interactive = true,
                pokeSignal = pokeCount,
                onPoked = { pokeCount++ }
            )

            Spacer(modifier = Modifier.height(14.dp))

            AnimatedContent(
                targetState = moodText,
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (fadeIn(tween(180)) + slideInVertically { it / 3 })
                            .togetherWith(fadeOut(tween(120)))
                    }
                },
                label = "companionMood"
            ) { line ->
                Text(
                    text = line,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tap the button or tap Morro directly.",
                fontSize = 12.sp,
                color = colors.textTertiary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            MorroPrimaryButton(
                text = if (pokeCount == 0) "Poke Morro" else "Poke again ($pokeCount)",
                onClick = { pokeCount++ },
                modifier = Modifier.fillMaxWidth()
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
