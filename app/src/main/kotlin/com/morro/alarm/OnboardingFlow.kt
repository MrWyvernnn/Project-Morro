package com.morro.alarm

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import com.morro.alarm.ui.components.MorroGhostMascot
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
        // Subtle orbital background (behind all screens)
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
                    2 -> Screen2Questions(
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
                    3 -> Screen3Trust(onNext = { currentStep = 4 })
                    4 -> Screen4Schedule(
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

/**
 * Screen 1: Pain Hook + Bouncing Mascot
 *
 * Displays animated text frames with a bouncing charcoal ghost mascot.
 */
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
            // Bouncing ghost mascot
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
                }
            }
        }
    }
}

/**
 * Screen 2: Simplified Selection Cards
 *
 * Two question substeps with clean flat card design.
 */
@Composable
fun Screen2Questions(
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

            Spacer(modifier = Modifier.height(28.dp))

            val currentOptions = if (subStep == 1) enemies else protections
            val currentSelected = if (subStep == 1) selectedEnemy else selectedProtection

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                currentOptions.forEach { option ->
                    val isSelected = currentSelected == option
                    SelectionCard(
                        title = option,
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
            Button(
                onClick = {
                    if (subStep == 1) subStep = 2 else onNext()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(MorroTokens.buttonShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MorroTokens.AccentPrimary,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = if (subStep == 1) "NEXT" else "CONTINUE",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * SelectionCard — clean, flat option card.
 */
@Composable
fun SelectionCard(
    title: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val backgroundColor = Color(0xFF0A0A0F)
    val borderColor = if (isSelected) MorroTokens.AccentPrimary else Color.White.copy(alpha = 0.10f)
    val borderWidth = if (isSelected) 1.5.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect() }
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = MorroTokens.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MorroTokens.AccentPrimary)
                )
            }
        }
    }
}

/**
 * Screen 3: Trust Drop
 *
 * Clean trust messaging with ghost mascot and three trust cards.
 */
@Composable
fun Screen3Trust(onNext: () -> Unit) {
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
                TrustCard(title = "Local-first", body = "Voice memos never leave this device. Stored only in app-private storage.")
                TrustCard(title = "Zero telemetry", body = "No analytics, no accounts, no cloud sync, no tracking SDKs.")
                TrustCard(title = "Open source", body = "Every line of code is inspectable and verifiable.")
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
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(MorroTokens.buttonShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MorroTokens.AccentPrimary,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "I TRUST THIS DEVICE",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.Black
                )
            }
        }
    }

    if (showDialog) {
        PrivacyDialog(onDismiss = { showDialog = false })
    }
}

@Composable
fun TrustCard(title: String, body: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0A0A0F))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MorroTokens.Surface,
        titleContentColor = MorroTokens.TextPrimary,
        textContentColor = MorroTokens.TextSecondary,
        title = {
            Text(text = "Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MorroTokens.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("All audio recordings are stored solely in app-private local storage.", color = MorroTokens.TextSecondary)
                Text("Zero network requests. This app is 100% offline.", color = MorroTokens.TextSecondary)
                Text("No third-party tracking, analytics, or external telemetry.", color = MorroTokens.TextSecondary)
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

/**
 * Screen 4: Schedule & Permissions
 *
 * Clean time picker with auto-triggered permission request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen4Schedule(
    wakeHour: Int,
    wakeMinute: Int,
    onTimeChanged: (Int, Int) -> Unit,
    onRequestPermissions: () -> Unit,
    onNext: () -> Unit
) {
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0A0F))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", wakeHour, wakeMinute),
                        style = MaterialTheme.typography.displayLarge.copy(
                            color = MorroTokens.AccentPrimary
                        ),
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onTimeChanged((wakeHour + 1) % 24, wakeMinute) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF14141A),
                                contentColor = MorroTokens.TextPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Increment hour",
                                tint = MorroTokens.AccentPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = { onTimeChanged((wakeHour + 23) % 24, wakeMinute) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF14141A),
                                contentColor = MorroTokens.TextPrimary
                            )
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
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(MorroTokens.buttonShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MorroTokens.AccentPrimary,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "GRANT & CONTINUE",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.Black
                )
            }
        }
    }
}

/**
 * Screen 5: Commitment Summary
 *
 * Final confirmation screen with time, enemy, and protection summary.
 */
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0A0F))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SummaryRow(label = "Execution Time", value = String.format(Locale.getDefault(), "%02d:%02d", wakeHour, wakeMinute))
                    SummaryRow(label = "Morning Enemy", value = enemy)
                    SummaryRow(label = "Protecting", value = protection)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).background(MorroTokens.AccentPrimary))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MORRO ARMED",
                    style = MaterialTheme.typography.labelLarge,
                    color = MorroTokens.AccentPrimary
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
            Button(
                onClick = onStartTomorrow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(MorroTokens.buttonShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MorroTokens.AccentPrimary,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "START TOMORROW",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
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
