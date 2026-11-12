package com.morro.alarm

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.mission.MissionPrefs
import com.morro.alarm.mission.MissionType
import com.morro.alarm.ui.components.AlarmBuilderSheet
import com.morro.alarm.ui.components.AlarmBuilderSheetContent
import com.morro.alarm.ui.components.MorroAnalogClock
import com.morro.alarm.ui.components.MorroCard
import com.morro.alarm.ui.components.MorroGhostMascot
import com.morro.alarm.ui.components.MorroPrimaryButton
import com.morro.alarm.ui.components.MorroPrivacyPolicySheet
import com.morro.alarm.ui.components.ProceduralOrbitBackground
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.MorroTokens
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroTypography
import com.morro.alarm.ui.theme.morroShapes
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/**
 * OnboardingFlow — 4-step onboarding flow matching the exact screenshot structure.
 * Step 1: Pain Hook (Ghost mascot)
 * Step 2: Exact Timing (Permission explanation)
 * Step 3: Battery Hardening
 * Step 4: Mission Selection → Analog Clock Hub → Contract Sealed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onRequestPermissions: () -> Unit = {}
) {
    OnboardingFlow(
        onComplete = onComplete,
        onRequestPermissions = onRequestPermissions
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingFlow(
    onComplete: () -> Unit,
    onRequestPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE) }

    // 4-step flow state
    var currentStep by remember { mutableIntStateOf(1) }

    // Onboarding data
    var selectedTarget by remember { mutableStateOf(prefs.getString("onboarding_target", "Deep Work") ?: "Deep Work") }
    var selectedMissionType by remember { mutableStateOf(MissionPrefs.getMissionType(context)) }

    val savedAlarm = AlarmScheduler.getSavedAlarmTime(context) ?: Pair(6, 30)
    var wakeHour by remember { mutableIntStateOf(savedAlarm.first) }
    var wakeMinute by remember { mutableIntStateOf(savedAlarm.second) }

    var showPrivacyPolicy by remember { mutableStateOf(false) }

    if (showPrivacyPolicy) {
        MorroPrivacyPolicySheet(onDismiss = { showPrivacyPolicy = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(morroColors().canvas)
    ) {
        ProceduralOrbitBackground(modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Content area with scrolling
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "OnboardingTransition"
                    ) { step ->
                        when (step) {
                            1 -> ScreenPainHook(onNext = { currentStep = 2 })
                            2 -> ScreenExactTiming(onNext = { currentStep = 3 })
                            3 -> ScreenBatteryHardening(onNext = { currentStep = 4 })
                            4 -> ScreenMissionAndClockHub(
                                wakeHour = wakeHour,
                                wakeMinute = wakeMinute,
                                missionType = selectedMissionType,
                                onAlarmConfigured = { h, m, mission ->
                                    wakeHour = h
                                    wakeMinute = m
                                    selectedMissionType = mission
                                    AlarmScheduler.scheduleDailyAlarm(context, h, m)
                                    MissionPrefs.setMissionType(context, mission)
                                },
                                onRequestPermissions = onRequestPermissions,
                                onOpenPrivacyPolicy = { showPrivacyPolicy = true },
                                onNext = { currentStep = 5 }
                            )
                        }
                    }
                }

                // Footer - always visible at bottom
                OnboardingFooter()
            }
        }
    }
}

/** Screen 1: Pain Hook — Bouncing ghost mascot on warm canvas. */
@Composable
private fun ScreenPainHook(onNext: () -> Unit) {
    var frameIndex by remember { mutableIntStateOf(0) }
    val frames = listOf("3 hours of sleep.", "7 snoozes.", "Another morning gone.")

    LaunchedEffect(frameIndex) {
        if (frameIndex < frames.size) {
            delay(1800)
            frameIndex++
        } else {
            delay(800)
            onNext()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            MorroGhostMascot(
                modifier = Modifier.size(80.dp, 100.dp),
                happy = false
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (frameIndex < frames.size) {
            Text(
                text = frames[frameIndex],
                style = morroTypography().headlineMedium,
                color = morroColors().textPrimary,
                textAlign = TextAlign.Center
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Not tomorrow.",
                    style = morroTypography().headlineLarge.copy(color = morroColors().textPrimary),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Morro.",
                    style = morroTypography().headlineLarge.copy(color = morroColors().accentTactical),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Screen 2: Exact Timing — Permission explanation card with 4-step progress. */
@Composable
private fun ScreenExactTiming(onNext: () -> Unit) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header: MORRO + Subtitle
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MORRO",
                style = typo.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = colors.accentTactical
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Commitment-Driven Wake-Up",
                style = typo.bodyMedium,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Progress Bar: 4 distinct horizontal line segments
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..4).forEach { i ->
                val isActive = i <= 2 // Step 2 is active
                val isCompleted = i < 2
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (isCompleted) colors.accentTactical
                            else if (isActive) colors.accentTactical
                            else colors.borderSubtle,
                            RoundedCornerShape(2.dp)
                        )
                        .animateContentSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Question Card: Centered MorroCard
        MorroCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            content = { contentModifier ->
                Column(
                    modifier = contentModifier
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title: "2. Exact Timing"
                    Text(
                        text = "2. Exact Timing",
                        style = typo.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.accentTactical
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Body text
                    Text(
                        text = "To preserve battery, Android delays ordinary timers by up to 15 minutes. Morro needs exact alarm permission to wake you at the precise minute you set.",
                        style = typo.bodyLarge,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Status Line: "✓ Exact timing enabled"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Exact timing enabled",
                            style = typo.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = colors.success
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action: MorroPrimaryButton "Next: Battery Hardening"
                    MorroPrimaryButton(
                        text = "Next: Battery Hardening",
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}

/** Screen 3: Battery Hardening — OEM battery optimization guidance. */
@Composable
private fun ScreenBatteryHardening(onNext: () -> Unit) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header: MORRO + Subtitle
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MORRO",
                style = typo.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = colors.accentTactical
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Commitment-Driven Wake-Up",
                style = typo.bodyMedium,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Progress Bar: 4 segments, step 3 active
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..4).forEach { i ->
                val isCompleted = i < 3
                val isActive = i == 3
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (isCompleted) colors.accentTactical
                            else if (isActive) colors.accentTactical
                            else colors.borderSubtle,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Battery Hardening Card
        MorroCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            content = { contentModifier ->
                Column(
                    modifier = contentModifier
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "3. Battery Hardening",
                        style = typo.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.accentTactical
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Manufacturers aggressively kill background alarms. Morro guides you to disable battery optimization for reliable wake-ups on your specific device.",
                        style = typo.bodyLarge,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Status line - not yet enabled
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(colors.borderSubtle, RoundedCornerShape(50))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Battery optimization: Not configured",
                            style = typo.titleMedium.copy(
                                color = colors.textSecondary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    MorroPrimaryButton(
                        text = "Next: Choose Mission",
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }
}

/** Screen 4: Mission Selection + Analog Clock Hub combined. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenMissionAndClockHub(
    wakeHour: Int,
    wakeMinute: Int,
    missionType: MissionType,
    onAlarmConfigured: (Int, Int, MissionType) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onNext: () -> Unit
) {
    var showBuilderModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRequestPermissions()
    }

    val colors = morroColors()
    val typo = morroTypography()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MORRO",
                style = typo.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = colors.accentTactical
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Commitment-Driven Wake-Up",
                style = typo.bodyMedium,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Progress Bar: 4 segments, step 4 active
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..4).forEach { i ->
                val isCompleted = i < 4
                val isActive = i == 4
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (isCompleted) colors.accentTactical
                            else if (isActive) colors.accentTactical
                            else colors.borderSubtle,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mission Selection Card
        MorroCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            content = { contentModifier ->
                Column(
                    modifier = contentModifier
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "4. Choose Your Wake-Up Mission",
                        style = typo.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.accentTactical
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Three Mission Option Chips
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MissionOptionChip(
                            title = "Voice Commitment",
                            subtitle = "15s personal commitment recorded in-app",
                            icon = Icons.Filled.Mic,
                            isSelected = missionType == MissionType.VOICE_COMMITMENT || missionType == MissionType.VOICE_MEMO,
                            onSelect = { onAlarmConfigured(wakeHour, wakeMinute, MissionType.VOICE_COMMITMENT) }
                        )
                        MissionOptionChip(
                            title = "QR / Barcode Scan",
                            subtitle = "CameraX + offline ML Kit barcode scanner",
                            icon = Icons.Filled.QrCodeScanner,
                            isSelected = missionType == MissionType.QR_CODE || missionType == MissionType.QR_BARCODE,
                            onSelect = { onAlarmConfigured(wakeHour, wakeMinute, MissionType.QR_CODE) }
                        )
                        MissionOptionChip(
                            title = "Object Photo Hunt",
                            subtitle = "30-item household pool, 1 random item on ring",
                            icon = Icons.Filled.PhotoCamera,
                            isSelected = missionType == MissionType.PHOTO_MISSION || missionType == MissionType.PHOTO_OBJECT,
                            onSelect = { onAlarmConfigured(wakeHour, wakeMinute, MissionType.PHOTO_MISSION) }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Analog Clock Preview
                    Text(
                        text = "Wake-Up Time",
                        style = typo.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12f.sp,
                            color = colors.textSecondary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    MorroAnalogClock(
                        modifier = Modifier.padding(vertical = 8.dp),
                        size = 200.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", wakeHour, wakeMinute),
                        style = typo.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = colors.accentTactical
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Configure button
                    MorroPrimaryButton(
                        text = "Configure Alarm & Mission",
                        onClick = { showBuilderModal = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )

        // Alarm Builder Modal
        if (showBuilderModal) {
            AlarmBuilderSheet(
                isVisible = showBuilderModal,
                onDismiss = { showBuilderModal = false },
                fabCenter = Offset(0f, 0f),
                content = {
                    AlarmBuilderSheetContent(
                        initialHour = wakeHour,
                        initialMinute = wakeMinute,
                        onDismiss = { showBuilderModal = false },
                        onSave = { hour, minute, mission ->
                            onAlarmConfigured(hour, minute, mission)
                            showBuilderModal = false
                        }
                    )
                }
            )
        }
    }
}

/** Mission Option Chip for selection. */
@Composable
private fun MissionOptionChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) colors.accentTactical.copy(alpha = 0.10f) else colors.surfaceVariant,
                shapes.Card
            )
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) colors.accentTactical else colors.borderSubtle,
                shapes.Card
            )
            .clip(shapes.Card)
            .clickable(onClick = onSelect)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (isSelected) colors.accentTactical else colors.surfaceLow,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = typo.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) colors.accentTactical else colors.textPrimary
                        )
                    )
                    Text(
                        text = subtitle,
                        style = typo.bodyMedium.copy(color = colors.textSecondary)
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(colors.accentTactical, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** Onboarding Footer — "100% offline • No accounts • Local storage only" */
@Composable
private fun OnboardingFooter() {
    val colors = morroColors()
    val typo = morroTypography()

    Text(
        text = "100% offline  •  No accounts  •  Local storage only",
        style = typo.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            color = colors.textDisabled
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    )
}

/** Happy Ghost Placebo Loader (Between Mission Selection and Contract) — 2.5s auto-advance. */
@Composable
private fun ScreenPlaceboLoader(onNext: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2500)
        onNext()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(morroColors().canvas)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = morroColors().accentTactical,
                    strokeWidth = 2.dp
                )
                Box(modifier = Modifier.background(Color.Transparent)) {
                    MorroGhostMascot(
                        modifier = Modifier.size(80.dp, 100.dp),
                        happy = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "Personalizing your wake-up protocol...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = morroColors().textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Calibrating mission gates for your habits",
                fontSize = 13.sp,
                color = morroColors().textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Contract Screen: Sad ghost mascot, countdown to execution, mission badge, privacy link. */
@Composable
private fun ScreenContract(
    wakeHour: Int,
    wakeMinute: Int,
    target: String,
    missionType: MissionType,
    onOpenPrivacyPolicy: () -> Unit,
    onStartTomorrow: () -> Unit
) {
    val sleepCountdown = remember(wakeHour, wakeMinute) {
        val now = Calendar.getInstance()
        val alarm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, wakeHour)
            set(Calendar.MINUTE, wakeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val diff = alarm.timeInMillis - now.timeInMillis
        val hours = diff / (1000 * 60 * 60)
        val minutes = (diff / (1000 * 60)) % 60
        when {
            hours > 0 -> "$hours hours and $minutes minutes"
            minutes > 0 -> "$minutes minutes"
            else -> "Less than a minute"
        }
    }

    val shapes = morroShapes()
    val colors = morroColors()
    val typo = morroTypography()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Sad Ghost Mascot on transparent wrapper
            Box(modifier = Modifier.background(Color.Transparent)) {
                MorroGhostMascot(modifier = Modifier.size(80.dp, 100.dp), happy = false)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "You have $sleepCountdown of sleep left.",
                style = typo.headlineMedium.copy(
                    color = colors.textPrimary,
                    lineHeight = 32.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "The contract is sealed. Tomorrow begins on schedule.",
                style = typo.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Bold highlighted Mission badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant, shapes.Card)
                    .border(1.5.dp, colors.accentTactical, shapes.Card)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ACTIVE MISSION",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSecondary,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "MISSION: ${target.uppercase()} • ${missionType.name.replace('_', ' ')}",
                        style = typo.headlineSmall.copy(
                            color = colors.accentTactical,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Privacy Policy link
            TextButton(onClick = onOpenPrivacyPolicy) {
                Text(
                    text = "Privacy Policy",
                    style = typo.bodyMedium.copy(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    ),
                    color = colors.accentTactical
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            MorroPrimaryButton(
                text = "START TOMORROW",
                onClick = onStartTomorrow,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}