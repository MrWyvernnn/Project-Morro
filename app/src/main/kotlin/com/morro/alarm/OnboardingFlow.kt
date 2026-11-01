package com.morro.alarm

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.components.MorroCard
import com.morro.alarm.ui.components.MorroGhostMascot
import com.morro.alarm.ui.components.MorroOptionChip
import com.morro.alarm.ui.components.MorroPrimaryButton
import com.morro.alarm.ui.components.ProceduralOrbitBackground
import com.morro.alarm.ui.theme.MorroTokens
import kotlinx.coroutines.delay
import java.util.Locale

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

    var currentStep by remember { mutableIntStateOf(1) }
    var selectedEnemy by remember { mutableStateOf(prefs.getString("onboarding_enemy", "Doomscrolling") ?: "Doomscrolling") }
    var selectedProtection by remember { mutableStateOf(prefs.getString("onboarding_protection", "First task of the day") ?: "First task of the day") }
    val savedAlarm = AlarmScheduler.getSavedAlarmTime(context) ?: Pair(6, 30)
    var wakeHour by remember { mutableIntStateOf(savedAlarm.first) }
    var wakeMinute by remember { mutableIntStateOf(savedAlarm.second) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MorroTokens.CanvasRoot)
    ) {
        // Procedural orbital background (behind all screens)
        ProceduralOrbitBackground(
            modifier = Modifier.fillMaxSize()
        )

        // Onboarding content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "OnboardingTransition"
            ) { step ->
                when (step) {
                    1 -> Screen1PainHook(onNext = { currentStep = 2 }, onSkip = { currentStep = 5 })
                    2 -> Screen2Interrogation(
                        selectedEnemy = selectedEnemy,
                        onEnemySelected = {
                            selectedEnemy = it
                            prefs.edit().putString("onboarding_enemy", it).apply()
                        },
                        selectedProtection = selectedProtection,
                        onProtectionSelected = {
                            selectedProtection = it
                            prefs.edit().putString("onboarding_protection", it).apply()
                        },
                        onNext = { currentStep = 3 }
                    )
                    3 -> Screen3TrustDrop(onNext = { currentStep = 4 })
                    4 -> Screen4Tutorial(
                        wakeHour = wakeHour,
                        wakeMinute = wakeMinute,
                        onTimeChanged = { h, m ->
                            wakeHour = h
                            wakeMinute = m
                            AlarmScheduler.scheduleDailyAlarm(context, h, m)
                        },
                        onRequestPermissions = onRequestPermissions,
                        onNext = { currentStep = 5 }
                    )
                    5 -> Screen5Summary(
                        wakeHour = wakeHour,
                        wakeMinute = wakeMinute,
                        enemy = selectedEnemy,
                        protection = selectedProtection,
                        onStartTomorrow = {
                            prefs.edit().putBoolean("has_onboarded", true).commit()
                            onComplete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SegmentedRule(label: String = "") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(MorroTokens.Border.copy(alpha = 0.3f)))
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MorroTokens.TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Box(modifier = Modifier.weight(1f).height(0.5.dp).background(MorroTokens.Border.copy(alpha = 0.3f)))
        }
    }
}

@Composable
fun Screen1PainHook(onNext: () -> Unit, onSkip: () -> Unit) {
    var frameIndex by remember { mutableIntStateOf(0) }
    val frames = listOf("3 hours of sleep.", "7 snoozes.", "Another morning gone.")

    LaunchedEffect(frameIndex) {
        if (frameIndex < frames.size) {
            delay(1800)
            frameIndex++
        } else {
            delay(1800)
            onNext()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSkip() }
                .padding(8.dp)
        ) {
            Text(
                text = "SKIP",
                style = MaterialTheme.typography.labelLarge,
                color = MorroTokens.TextSecondary,
                letterSpacing = 0.12.sp
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ghost mascot
            MorroGhostMascot(modifier = Modifier.size(80.dp, 100.dp))

            Spacer(modifier = Modifier.height(24.dp))

            if (frameIndex < frames.size) {
                Text(
                    text = frames[frameIndex],
                    style = MaterialTheme.typography.headlineMedium,
                    color = MorroTokens.TextPrimary,
                    textAlign = TextAlign.Center
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Not tomorrow.",
                        style = MaterialTheme.typography.headlineLarge.copy(color = MorroTokens.TextPrimary),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Morro.",
                        style = MaterialTheme.typography.headlineLarge.copy(color = MorroTokens.AccentPrimary),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    SegmentedRule(label = "HARD-LOCK INSTRUMENT")
                }
            }
        }
    }
}

@Composable
fun Screen2Interrogation(
    selectedEnemy: String,
    onEnemySelected: (String) -> Unit,
    selectedProtection: String,
    onProtectionSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    var subStep by remember { mutableIntStateOf(1) }
    val enemies = listOf("Doomscrolling", "Heavy Sleeper", "Hate Mornings", "Racing Thoughts", "Zero Intention")
    val protections = listOf("First task of the day", "Deep work session", "Morning workout", "Quiet coffee")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (subStep == 1) "Biggest morning enemy?" else "First thing you're protecting tomorrow?",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = MorroTokens.TextPrimary,
                    lineHeight = 36.sp
                )
            )

            // Fixed spacing: 28.dp between headline and first option
            Spacer(modifier = Modifier.height(28.dp))

            val currentOptions = if (subStep == 1) enemies else protections
            val currentSelected = if (subStep == 1) selectedEnemy else selectedProtection

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                currentOptions.forEach { option ->
                    val isSelected = currentSelected == option
                    TacticalOptionCard(
                        title = option,
                        subtitle = chipSubtitle(option),
                        tagLabel = "// THREAT_ID: ${currentOptions.indexOf(option) + 1}",
                        isSelected = isSelected,
                        onSelect = {
                            if (subStep == 1) onEnemySelected(option) else onProtectionSelected(option)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            MorroPrimaryButton(
                text = if (subStep == 1) "NEXT" else "CONTINUE",
                onClick = {
                    if (subStep == 1) subStep = 2 else onNext()
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            SegmentedRule(label = "PHASE ${if (subStep == 1) "02A" else "02B"} // 02 OF 05")
        }
    }
}

@Composable
fun TacticalOptionCard(
    title: String,
    subtitle: String,
    tagLabel: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        Color(0xFF00F0FF).copy(alpha = 0.10f)
    } else {
        Color(0xFF0E0E14)
    }

    val borderColor = if (isSelected) {
        Color(0xFF00F0FF)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

    val borderWidth = if (isSelected) 1.5.dp else 1.dp

    MorroCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tagLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MorroTokens.AccentPrimary else MorroTokens.TextSecondary.copy(alpha = 0.6f)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier.size(6.dp).background(MorroTokens.AccentPrimary)
                    )
                }
            }

            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = MorroTokens.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MorroTokens.TextSecondary
            )
        }
    }
}

private fun chipSubtitle(option: String): String = when (option) {
    "Doomscrolling" -> "Reaching for dopamine before your feet hit the floor."
    "Heavy Sleeper" -> "Turning off alarms in your sleep without waking intention."
    "Hate Mornings" -> "Starting the day with friction instead of purpose."
    "Racing Thoughts" -> "Mind spiraling before consciousness."
    "Zero Intention" -> "Waking without reason or commitment."
    "First task of the day" -> "Ironclad runtime parameter. The interface executes confirmed directives."
    "Deep work session" -> "Protected focus block. Zero notifications, zero friction."
    "Morning workout" -> "Physical movement protocol to override sleep inertia."
    "Quiet coffee" -> "Intentional stillness before the day's demands."
    else -> "Morning commitment parameter."
}

@Composable
fun Screen3TrustDrop(onNext: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(20.dp))

            // Ghost mascot for Screen 3
            MorroGhostMascot(modifier = Modifier.size(80.dp, 100.dp))

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your Data Stays Yours.",
                style = MaterialTheme.typography.headlineLarge.copy(color = MorroTokens.TextPrimary)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TrustCard(
                    tag = "// SYS.STORAGE_01",
                    badge = "[ LOCAL ]",
                    title = "Local-first",
                    body = "Voice memos never leave this device. Stored only in app-private storage."
                )
                TrustCard(
                    tag = "// NET.TRANSMIT_00",
                    badge = "[ ZERO-NET ]",
                    title = "Zero telemetry",
                    body = "No analytics, no accounts, no cloud sync, no tracking SDKs. Works in airplane mode."
                )
                TrustCard(
                    tag = "// SRC.INTEGRITY_VERIFIED",
                    badge = "[ AUDITABLE ]",
                    title = "Open source",
                    body = "Nothing to hide. Every line of code is inspectable and verifiable."
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Privacy Policy (10s)",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MorroTokens.AccentPrimary,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showDialog = true }
                    .padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            MorroPrimaryButton(text = "I TRUST THIS DEVICE", onClick = onNext)
            Spacer(modifier = Modifier.height(12.dp))
            SegmentedRule(label = "OFFLINE VERIFIED // 03 OF 05")
        }
    }

    if (showDialog) {
        PrivacyDialog(onDismiss = { showDialog = false })
    }
}

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MorroTokens.Surface,
        titleContentColor = MorroTokens.TextPrimary,
        textContentColor = MorroTokens.TextSecondary,
        title = {
            Text(text = "Privacy Policy (10s)", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MorroTokens.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("• All audio recordings are stored solely in app-private local storage.", color = MorroTokens.TextSecondary)
                Text("• Zero network requests. Morro is 100% offline and air-gapped.", color = MorroTokens.TextSecondary)
                Text("• No third-party tracking, analytics, or external telemetry.", color = MorroTokens.TextSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MorroTokens.AccentPrimary,
                    contentColor = Color.Black
                )
            ) {
                Text(text = "Close", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    )
}

@Composable
fun TrustCard(tag: String, badge: String, title: String, body: String) {
    MorroCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MorroTokens.TextSecondary.copy(alpha = 0.6f)
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .background(MorroTokens.SurfaceDim)
                        .border(1.dp, MorroTokens.Border, RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelLarge,
                        color = MorroTokens.AccentPrimary
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = MorroTokens.AccentPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MorroTokens.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen4Tutorial(
    wakeHour: Int,
    wakeMinute: Int,
    onTimeChanged: (Int, Int) -> Unit,
    onRequestPermissions: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    var showTimePickerModal by remember { mutableStateOf(false) }

    val is24Hour = DateFormat.is24HourFormat(context)
    val timePickerState = rememberTimePickerState(
        initialHour = wakeHour,
        initialMinute = wakeMinute,
        is24Hour = is24Hour
    )

    // Auto-trigger permission request on first render
    LaunchedEffect(Unit) {
        onRequestPermissions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Set your first alarm.",
                style = MaterialTheme.typography.headlineLarge.copy(color = MorroTokens.TextPrimary)
            )

            MorroCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXEC_TIME // REG_UTC_LOCAL",
                            style = MaterialTheme.typography.labelLarge,
                            color = MorroTokens.TextSecondary
                        )
                        Box(
                            modifier = Modifier.size(6.dp).background(MorroTokens.AccentPrimary)
                        )
                    }

                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", wakeHour, wakeMinute),
                        style = MaterialTheme.typography.displayLarge.copy(
                            color = MorroTokens.AccentPrimary
                        ),
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onTimeChanged((wakeHour + 1) % 24, wakeMinute) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MorroTokens.SurfaceDim,
                                contentColor = MorroTokens.TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Increment hour",
                                tint = MorroTokens.AccentPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Button(
                            onClick = { onTimeChanged((wakeHour + 23) % 24, wakeMinute) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MorroTokens.SurfaceDim,
                                contentColor = MorroTokens.TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Decrement hour",
                                tint = MorroTokens.AccentPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            MorroPrimaryButton(text = "GRANT & CONTINUE", onClick = onNext)
            Spacer(modifier = Modifier.height(12.dp))
            SegmentedRule(label = "SCHEDULE READY // 04 OF 05")
        }
    }

    if (showTimePickerModal) {
        AlertDialog(
            onDismissRequest = { showTimePickerModal = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MorroTokens.Surface,
            title = {
                Text(text = "Select Wake Time", color = MorroTokens.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTimeChanged(timePickerState.hour, timePickerState.minute)
                        showTimePickerModal = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MorroTokens.AccentPrimary,
                        contentColor = Color.Black
                    )
                ) {
                    Text(text = "Confirm", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showTimePickerModal = false },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MorroTokens.Surface,
                        contentColor = MorroTokens.TextPrimary
                    ),
                    border = BorderStroke(1.dp, MorroTokens.Border)
                ) {
                    Text(text = "Cancel", color = MorroTokens.TextPrimary)
                }
            }
        )
    }
}

@Composable
fun Screen5Summary(
    wakeHour: Int,
    wakeMinute: Int,
    enemy: String,
    protection: String,
    onStartTomorrow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Contract Sealed",
                style = MaterialTheme.typography.headlineLarge.copy(color = MorroTokens.TextPrimary)
            )

            MorroCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SYS.SPEC // CONTRACT #001",
                            style = MaterialTheme.typography.labelSmall,
                            color = MorroTokens.TextSecondary
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .background(MorroTokens.AccentPrimary)
                                .clip(RoundedCornerShape(2.dp))
                        ) {
                            Text(
                                text = "[ ARMED ]",
                                style = MaterialTheme.typography.labelLarge,
                                color = MorroTokens.Surface
                            )
                        }
                    }

                    SummaryRowTactical(label = "EXECUTION TIME", value = String.format(Locale.getDefault(), "%02d:%02d", wakeHour, wakeMinute))
                    SummaryRowTactical(label = "IDENTIFIED ENEMY", value = enemy)
                    SummaryRowTactical(label = "PROTECTING OBJECTIVE", value = protection)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(MorroTokens.AccentPrimary))
                    Text(
                        text = "MORRO ENGINE ACTIVE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MorroTokens.AccentPrimary
                    )
                }
                Text(
                    text = "OFFLINE READY // SECURE ENCLAVE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MorroTokens.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            MorroPrimaryButton(text = "START TOMORROW", onClick = onStartTomorrow)
            Spacer(modifier = Modifier.height(12.dp))
            SegmentedRule(label = "COMMITMENT SEALED // 05 OF 05")
        }
    }
}

@Composable
fun SummaryRowTactical(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MorroTokens.TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = MorroTokens.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
