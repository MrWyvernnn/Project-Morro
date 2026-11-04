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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle

/**
 * OnboardingFlow — refined 6-step commitment setup.
 *
 * Flow: Pain Hook → Target → Trap → Placebo Loader → Time → Contract
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

    // Steps: 1=Pain Hook, 2=Target, 3=Trap, 4=Loader, 5=Time, 6=Contract
    var currentStep by remember { mutableIntStateOf(1) }
    var selectedTarget by remember { mutableStateOf(prefs.getString("onboarding_target", "Deep Work") ?: "Deep Work") }
    var selectedTrap by remember { mutableStateOf(prefs.getString("onboarding_trap", "Doomscrolling") ?: "Doomscrolling") }
    val savedAlarm = AlarmScheduler.getSavedAlarmTime(context) ?: Pair(6, 30)
    var wakeHour by remember { mutableIntStateOf(savedAlarm.first) }
    var wakeMinute by remember { mutableIntStateOf(savedAlarm.second) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        ProceduralOrbitBackground(modifier = Modifier.fillMaxSize())

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
                    1 -> Screen1PainHook(
                        onNext = { currentStep = 2 },
                        onSkip = { currentStep = 5 }
                    )
                    2 -> Screen2Target(
                        selectedTarget = selectedTarget,
                        onTargetSelected = {
                            selectedTarget = it
                            prefs.edit().putString("onboarding_target", it).apply()
                        },
                        onNext = { currentStep = 3 }
                    )
                    3 -> Screen3Trap(
                        selectedTrap = selectedTrap,
                        onTrapSelected = {
                            selectedTrap = it
                            prefs.edit().putString("onboarding_trap", it).apply()
                        },
                        onNext = { currentStep = 4 }
                    )
                    4 -> Screen4PlaceboLoader(onNext = { currentStep = 5 })
                    5 -> Screen5TimePicker(
                        wakeHour = wakeHour,
                        wakeMinute = wakeMinute,
                        onTimeChanged = { h, m ->
                            wakeHour = h
                            wakeMinute = m
                            AlarmScheduler.scheduleDailyAlarm(context, h, m)
                        },
                        onRequestPermissions = onRequestPermissions,
                        onNext = { currentStep = 6 }
                    )
                    6 -> Screen6Contract(
                        wakeHour = wakeHour,
                        wakeMinute = wakeMinute,
                        target = selectedTarget,
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

/** Screen 1: Pain Hook — Ghost floats on pure AMOLED black with soft radial glow. */
@Composable
fun Screen1PainHook(onNext: () -> Unit, onSkip: () -> Unit) {
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
            MorroGhostMascot(modifier = Modifier.size(80.dp, 100.dp), happy = false)

            Spacer(modifier = Modifier.height(32.dp))

            if (frameIndex < frames.size) {
                Text(
                    text = frames[frameIndex],
                    style = MaterialTheme.typography.headlineMedium,
                    color = MorroTokens.TextPrimary,
                    textAlign = TextAlign.Center
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

/** Screen 2: "What is your main target tomorrow?" */
@Composable
fun Screen2Target(
    selectedTarget: String,
    onTargetSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    val targets = listOf("Deep Work", "Morning Workout", "Early Flight", "Just Waking Up")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MorroTokens.CanvasRoot)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "What is your main target tomorrow?",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = MorroTokens.TextPrimary,
                    lineHeight = 36.sp
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                targets.forEach { option ->
                    val isSelected = selectedTarget == option
                    SelectionCard(
                        title = option,
                        isSelected = isSelected,
                        onSelect = { onTargetSelected(option) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
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
                    text = "NEXT",
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

/** Screen 3: "What usually traps you?" */
@Composable
fun Screen3Trap(
    selectedTrap: String,
    onTrapSelected: (String) -> Unit,
    onNext: () -> Unit
) {
    val traps = listOf("Doomscrolling", "Hitting Snooze blindly", "The Warm Bed")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MorroTokens.CanvasRoot)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "What usually traps you?",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = MorroTokens.TextPrimary,
                    lineHeight = 36.sp
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                traps.forEach { option ->
                    val isSelected = selectedTrap == option
                    SelectionCard(
                        title = option,
                        isSelected = isSelected,
                        onSelect = { onTrapSelected(option) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
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
                    text = "NEXT",
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

/** Screen 4: Placebo Loader — Happy ghost, spinning indicator, auto-advance. */
@Composable
fun Screen4PlaceboLoader(onNext: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2500)
        onNext()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MorroGhostMascot(modifier = Modifier.size(80.dp, 100.dp), happy = true)

        Spacer(modifier = Modifier.height(40.dp))

        // Spinning progress indicator
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = Color(0xFFFFD54F),
            strokeWidth = 2.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Personalizing your experience...",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFFFFFFF),
            textAlign = TextAlign.Center
        )
    }
}

/** Screen 5: Set Time — Massive digital clock, native TimePickerDialog on tap. Hardcoded AMOLED Black overrides. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen5TimePicker(
    wakeHour: Int,
    wakeMinute: Int,
    onTimeChanged: (Int, Int) -> Unit,
    onRequestPermissions: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        onRequestPermissions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Set your wake-up time.",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = Color(0xFFFFFFFF),
                    lineHeight = 36.sp
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Massive clickable time display
            Text(
                text = String.format(Locale.getDefault(), "%02d:%02d", wakeHour, wakeMinute),
                style = MaterialTheme.typography.displayLarge.copy(
                    color = Color(0xFFFFFFFF),
                    letterSpacing = (-0.04f).sp
                ),
                modifier = Modifier.clickable {
                    android.app.TimePickerDialog(
                        context,
                        { _, h, m -> onTimeChanged(h, m) },
                        wakeHour,
                        wakeMinute,
                        true
                    ).show()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tap to use system clock",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF999999),
                modifier = Modifier.clickable {
                    android.app.TimePickerDialog(
                        context,
                        { _, h, m -> onTimeChanged(h, m) },
                        wakeHour,
                        wakeMinute,
                        true
                    ).show()
                }
            )
        }

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
                    containerColor = Color(0xFFFFD54F),
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

/** Screen 6: Contract Sealed — Sad ghost, dynamic countdown, mission display, START TOMORROW. */
@Composable
fun Screen6Contract(
    wakeHour: Int,
    wakeMinute: Int,
    target: String,
    onStartTomorrow: () -> Unit
) {
    val hoursUntilExecution = remember(wakeHour, wakeMinute) {
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
            hours > 0 -> "$hours hours"
            minutes > 0 -> "$minutes minutes"
            else -> "Soon"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Sad ghost
            MorroGhostMascot(modifier = Modifier.size(80.dp, 100.dp), happy = false)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "You have $hoursUntilExecution until execution.",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = MorroTokens.TextPrimary,
                    lineHeight = 32.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Mission Display — bold, highlighted
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MorroTokens.AccentPrimary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .border(1.5.dp, MorroTokens.AccentPrimary, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MISSION",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MorroTokens.TextSecondary,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = target.uppercase(),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = MorroTokens.AccentPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier
                    .size(6.dp)
                    .background(MorroTokens.AccentPrimary, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MORRO ARMED",
                    style = MaterialTheme.typography.labelLarge,
                    color = MorroTokens.AccentPrimary
                )
            }
        }

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

/** SelectionCard — clean option selector. */
@Composable
fun SelectionCard(
    title: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) MorroTokens.AccentPrimary else Color.White.copy(alpha = 0.10f)
    val borderWidth = if (isSelected) 1.5.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MorroTokens.Surface)
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
                Box(modifier = Modifier
                    .size(8.dp)
                    .background(MorroTokens.AccentPrimary))
            }
        }
    }
}