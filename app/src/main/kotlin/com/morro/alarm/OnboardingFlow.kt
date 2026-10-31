package com.morro.alarm

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.theme.MorroColors
import kotlinx.coroutines.delay
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Cyber-Defense 5-Screen Onboarding Flow
// Root background: AmoledBlack (Color(0xFF000000))
// Surfaces: MidnightBlue (Color(0xFF0A0A0F))
// Primary: ElectricCyan (Color(0xFF00F0FF))
// Warning/Missing: AlertOrange (Color(0xFFFF4500))
// Strict rules: NO glassmorphism, NO glow shadows, NO gradients, NO emojis.
// ─────────────────────────────────────────────────────────────────────────────

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
    var currentStep by remember { mutableIntStateOf(1) }

    var selectedEnemy by remember { mutableStateOf("Doomscrolling") }
    var selectedProtection by remember { mutableStateOf("First task of the day") }
    var selectedHour by remember { mutableIntStateOf(6) }
    var selectedMinute by remember { mutableIntStateOf(30) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingCountdown by remember { mutableIntStateOf(15) }
    var hasTempRecorded by remember { mutableStateOf(false) }
    var isPlayingPreview by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MorroColors.AmoledBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "OnboardingScreens"
        ) { step ->
            when (step) {
                1 -> Screen1PainHook(
                    onNext = { currentStep = 2 },
                    onSkip = { currentStep = 5 }
                )
                2 -> Screen2Interrogation(
                    selectedEnemy = selectedEnemy,
                    onEnemySelected = { selectedEnemy = it },
                    selectedProtection = selectedProtection,
                    onProtectionSelected = { selectedProtection = it },
                    onNext = { currentStep = 3 }
                )
                3 -> Screen3TrustDrop(
                    onNext = { currentStep = 4 }
                )
                4 -> Screen4InteractiveTutorial(
                    selectedHour = selectedHour,
                    selectedMinute = selectedMinute,
                    onTimeChanged = { h, m ->
                        selectedHour = h
                        selectedMinute = m
                    },
                    isRecording = isRecording,
                    recordingCountdown = recordingCountdown,
                    hasTempRecorded = hasTempRecorded,
                    isPlayingPreview = isPlayingPreview,
                    onRequestPermissions = onRequestPermissions,
                    onStartRecording = {
                        isRecording = true
                        recordingCountdown = 15
                        hasTempRecorded = false
                        MemoRecorder.startRecording(
                            context,
                            onError = { isRecording = false },
                            onMaxDurationReached = {
                                isRecording = false
                                hasTempRecorded = true
                            }
                        )
                    },
                    onStopRecording = {
                        isRecording = false
                        hasTempRecorded = true
                    },
                    onCancelRecording = {
                        isRecording = false
                        hasTempRecorded = false
                    },
                    onPlayPreview = { isPlayingPreview = true },
                    onNext = { currentStep = 5 }
                )
                5 -> Screen5Commitment(
                    wakeHour = selectedHour,
                    wakeMinute = selectedMinute,
                    enemy = selectedEnemy,
                    protection = selectedProtection,
                    onStartTomorrow = onComplete
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 1: Pain Hook
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Screen1PainHook(onNext: () -> Unit, onSkip: () -> Unit) {
    var frameIndex by remember { mutableIntStateOf(0) }
    val frames = listOf(
        "3 hours of sleep.",
        "7 snoozes.",
        "Another morning gone."
    )

    LaunchedEffect(frameIndex) {
        if (frameIndex < frames.size) {
            delay(1800)
            frameIndex++
        } else {
            onNext()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MorroColors.AmoledBlack)
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSkip() }
                .padding(8.dp)
        ) {
            Text(
                text = "SKIP",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MorroColors.WhiteSecondary,
                letterSpacing = 2.sp
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (frameIndex < frames.size) {
                Text(
                    text = frames[frameIndex],
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = MorroColors.WhitePrimary,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-1).sp
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Not tomorrow.",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = MorroColors.WhitePrimary,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-1.5).sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Morro.",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = MorroColors.ElectricCyan,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-1.5).sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 2: Interrogation
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Screen2Interrogation(
    selectedEnemy: String,
    onEnemySelected: (String) -> Unit,
    selectedProtection: String,
    onProtectionSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    var questionStep by remember { mutableIntStateOf(1) }
    val enemies = listOf("Doomscrolling", "Snooze tapping", "Racing thoughts", "Zero intention")
    val protections = listOf("First task of the day", "Deep work session", "Morning workout", "Quiet coffee")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MorroColors.AmoledBlack)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (questionStep == 1) "QUESTION 1 OF 2" else "QUESTION 2 OF 2",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MorroColors.ElectricCyan,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (questionStep == 1) "Biggest morning enemy?" else "First thing you're protecting tomorrow?",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MorroColors.WhitePrimary,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(32.dp))

            val currentOptions = if (questionStep == 1) enemies else protections
            val currentSelected = if (questionStep == 1) selectedEnemy else selectedProtection

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                currentOptions.forEach { option ->
                    val isSelected = currentSelected == option
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MorroColors.MidnightBlue, RoundedCornerShape(16.dp))
                            .border(
                                BorderStroke(
                                    1.5.dp,
                                    if (isSelected) MorroColors.ElectricCyan else MorroColors.WhiteSecondary.copy(alpha = 0.15f)
                                ),
                                RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (questionStep == 1) onEnemySelected(option) else onProtectionSelected(option)
                            }
                            .padding(20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = option,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MorroColors.ElectricCyan else MorroColors.WhitePrimary
                        )
                    }
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
                    if (questionStep == 1) {
                        questionStep = 2
                    } else {
                        onNext()
                    }
                }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (questionStep == 1) "NEXT" else "CONTINUE",
                color = MorroColors.AmoledBlack,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.5.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 3: Trust Drop
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen3TrustDrop(onNext: () -> Unit) {
    var showBottomSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MorroColors.AmoledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "SECURITY MODEL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MorroColors.ElectricCyan,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Your data stays yours.",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MorroColors.WhitePrimary,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.height(36.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    TrustBullet("Local-first", "All recordings and alarms live strictly in device-private storage.")
                    TrustBullet("Zero telemetry", "No tracking SDKs, no analytics, no third-party servers.")
                    TrustBullet("Open source", "Auditable codebase designed for absolute reliability.")
                }

                Spacer(modifier = Modifier.height(40.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showBottomSheet = true }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Privacy policy, 10-second version ->",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MorroColors.ElectricCyan
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MorroColors.ElectricCyan, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onNext() }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CONTINUE",
                    color = MorroColors.AmoledBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.5.sp
                )
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                containerColor = MorroColors.MidnightBlue,
                contentColor = MorroColors.WhitePrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "PRIVACY POLICY (10s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MorroColors.ElectricCyan,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "- 100% offline. Zero internet permissions.",
                        fontSize = 14.sp,
                        color = MorroColors.WhiteSecondary
                    )
                    Text(
                        text = "- Voice memos never leave your phone.",
                        fontSize = 14.sp,
                        color = MorroColors.WhiteSecondary
                    )
                    Text(
                        text = "- No accounts, no login, no cloud sync.",
                        fontSize = 14.sp,
                        color = MorroColors.WhiteSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun TrustBullet(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MorroColors.MidnightBlue, RoundedCornerShape(16.dp))
            .border(1.5.dp, MorroColors.WhiteSecondary.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MorroColors.WhitePrimary
        )
        Text(
            text = description,
            fontSize = 13.sp,
            color = MorroColors.WhiteSecondary,
            lineHeight = 18.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 4: Interactive Tutorial
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Screen4InteractiveTutorial(
    selectedHour: Int,
    selectedMinute: Int,
    onTimeChanged: (Int, Int) -> Unit,
    isRecording: Boolean,
    recordingCountdown: Int,
    hasTempRecorded: Boolean,
    isPlayingPreview: Boolean,
    onRequestPermissions: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onPlayPreview: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MorroColors.AmoledBlack)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "INTERACTIVE TUTORIAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MorroColors.ElectricCyan,
                letterSpacing = 2.sp
            )
            Text(
                text = "Set alarm & record first promise.",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MorroColors.WhitePrimary,
                letterSpacing = (-1).sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MorroColors.MidnightBlue, RoundedCornerShape(16.dp))
                    .border(1.5.dp, MorroColors.WhiteSecondary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "STEP A: ALARM TIME & PERMISSIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MorroColors.WhiteSecondary,
                    letterSpacing = 1.5.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MorroColors.WhitePrimary
                    )
                    Button(
                        onClick = { onRequestPermissions() },
                        colors = ButtonDefaults.buttonColors(containerColor = MorroColors.ElectricCyan, contentColor = MorroColors.AmoledBlack)
                    ) {
                        Text("Grant Alarm Access", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MorroColors.MidnightBlue, RoundedCornerShape(16.dp))
                    .border(1.5.dp, MorroColors.WhiteSecondary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "STEP B: RECORD FIRST COMMITMENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MorroColors.WhiteSecondary,
                    letterSpacing = 1.5.sp
                )
                when {
                    isRecording -> {
                        Text(
                            text = "Recording: ${recordingCountdown}s",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MorroColors.AlertOrange
                        )
                        Button(
                            onClick = onStopRecording,
                            colors = ButtonDefaults.buttonColors(containerColor = MorroColors.AlertOrange, contentColor = MorroColors.WhitePrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop & Save", fontWeight = FontWeight.Bold)
                        }
                    }
                    hasTempRecorded -> {
                        Text(
                            text = "Commitment recorded successfully.",
                            fontSize = 14.sp,
                            color = MorroColors.ElectricCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onPlayPreview,
                                colors = ButtonDefaults.buttonColors(containerColor = MorroColors.MidnightBlue, contentColor = MorroColors.WhitePrimary),
                                border = BorderStroke(1.dp, MorroColors.WhiteSecondary.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Preview")
                            }
                            Button(
                                onClick = onCancelRecording,
                                colors = ButtonDefaults.buttonColors(containerColor = MorroColors.MidnightBlue, contentColor = MorroColors.AlertOrange),
                                border = BorderStroke(1.dp, MorroColors.AlertOrange.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Re-record")
                            }
                        }
                    }
                    else -> {
                        Button(
                            onClick = onStartRecording,
                            colors = ButtonDefaults.buttonColors(containerColor = MorroColors.ElectricCyan, contentColor = MorroColors.AmoledBlack),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Record 15s Commitment", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MorroColors.MidnightBlue, RoundedCornerShape(16.dp))
                    .border(1.5.dp, MorroColors.WhiteSecondary.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "STEP C: THE MORNING LOCK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MorroColors.WhiteSecondary,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "1. Math puzzle wakes the body.\n2. Your voice memo plays until finished. No skip, no seek.",
                    fontSize = 13.sp,
                    color = MorroColors.WhiteSecondary,
                    lineHeight = 18.sp
                )
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
                ) { onNext() }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CONTINUE",
                color = MorroColors.AmoledBlack,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.5.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 5: Commitment Summary
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Screen5Commitment(
    wakeHour: Int,
    wakeMinute: Int,
    enemy: String,
    protection: String,
    onStartTomorrow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MorroColors.AmoledBlack)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "READY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MorroColors.ElectricCyan,
                letterSpacing = 2.sp
            )
            Text(
                text = "Your defense is set.",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MorroColors.WhitePrimary,
                letterSpacing = (-1).sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MorroColors.MidnightBlue, RoundedCornerShape(20.dp))
                    .border(1.5.dp, MorroColors.ElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SummaryRow(label = "Wake time", value = String.format(Locale.getDefault(), "%02d:%02d", wakeHour, wakeMinute))
                SummaryRow(label = "Target enemy", value = enemy)
                SummaryRow(label = "Protecting", value = protection)
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
                ) { onStartTomorrow() }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "START TOMORROW",
                color = MorroColors.AmoledBlack,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MorroColors.WhiteSecondary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MorroColors.WhitePrimary
        )
    }
}
