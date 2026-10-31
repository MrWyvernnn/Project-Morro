package com.morro.alarm

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Strict Cyber-Defense Design Tokens
// Background = Color(0xFF000000)
// Surface    = Color(0xFF0A0A0F)
// Primary Text = Color.White
// Primary Accent = Color(0xFF00F0FF)
// Rules: 0.dp corners on major containers/buttons, NO gradients, NO glassmorphism, NO emojis.
// ─────────────────────────────────────────────────────────────────────────────

private val BackgroundColor = Color(0xFF000000)
private val SurfaceColor = Color(0xFF0A0A0F)
private val PrimaryTextColor = Color.White
private val PrimaryAccentColor = Color(0xFF00F0FF)

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

@Composable
fun OnboardingFlow(
    onComplete: () -> Unit,
    onRequestPermissions: () -> Unit = {}
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var selectedEnemy by remember { mutableStateOf("Doomscrolling") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when (currentStep) {
            1 -> Screen1PainHook(
                onContinue = { currentStep = 2 }
            )
            2 -> Screen2Interrogation(
                selectedEnemy = selectedEnemy,
                onEnemySelected = { selectedEnemy = it },
                onContinue = { currentStep = 3 }
            )
            3 -> Screen3TrustDrop(
                onContinue = { currentStep = 4 }
            )
            4 -> Screen4Tutorial(
                onContinue = { onComplete() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Cyber-Defense Button (Full width, height 64.dp, 0.dp corners, bold)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CyberContinueButton(
    text: String = "Continue",
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryAccentColor,
            contentColor = Color.Black
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.Black
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 1: Pain Hook
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Screen1PainHook(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Not tomorrow. Morro.",
            fontWeight = FontWeight.Bold,
            fontSize = 48.sp,
            color = PrimaryTextColor,
            lineHeight = 56.sp
        )

        CyberContinueButton(onClick = onContinue)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 2: Interrogation
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Screen2Interrogation(
    selectedEnemy: String,
    onEnemySelected: (String) -> Unit,
    onContinue: () -> Unit
) {
    val enemies = listOf("Doomscrolling", "Heavy Sleeper", "Hate Mornings")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Biggest morning enemy?",
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = PrimaryTextColor
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                enemies.forEach { enemy ->
                    val isSelected = selectedEnemy == enemy

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(if (isSelected) PrimaryAccentColor else SurfaceColor)
                            .then(
                                if (!isSelected) {
                                    Modifier.border(BorderStroke(1.dp, Color.White))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onEnemySelected(enemy)
                            }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = enemy,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp,
                            color = if (isSelected) Color.Black else PrimaryTextColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        CyberContinueButton(onClick = onContinue)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 3: Trust Drop
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Screen3TrustDrop(onContinue: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your Data Stays Yours.",
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = PrimaryTextColor
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "• Local-first",
                    fontSize = 18.sp,
                    color = PrimaryTextColor
                )
                Text(
                    text = "• Zero telemetry",
                    fontSize = 18.sp,
                    color = PrimaryTextColor
                )
                Text(
                    text = "• Open source",
                    fontSize = 18.sp,
                    color = PrimaryTextColor
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Privacy Policy (10s)",
                fontSize = 16.sp,
                color = PrimaryAccentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showDialog = true
                    }
                    .padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        CyberContinueButton(onClick = onContinue)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            shape = RectangleShape,
            containerColor = SurfaceColor,
            titleContentColor = PrimaryTextColor,
            textContentColor = PrimaryTextColor,
            title = {
                Text(
                    text = "Privacy Policy (10s)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("• All audio recordings are stored solely in app-private local storage.")
                    Text("• Zero network requests. Morro is 100% offline and air-gapped.")
                    Text("• No third-party tracking, analytics, or external telemetry.")
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDialog = false },
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAccentColor,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Close",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 4: The Tutorial - Simplified
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Screen4Tutorial(onContinue: () -> Unit) {
    val context = LocalContext.current
    var selectedHour by remember { mutableIntStateOf(6) }
    var selectedMinute by remember { mutableIntStateOf(30) }
    var hasSetAlarm by remember { mutableStateOf(false) }

    val calendar = Calendar.getInstance()
    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
                hasSetAlarm = true
                AlarmScheduler.scheduleDailyAlarm(context, hourOfDay, minute)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Set your first alarm.",
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = PrimaryTextColor
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { timePickerDialog.show() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceColor,
                    contentColor = PrimaryTextColor
                ),
                border = BorderStroke(1.dp, Color.White)
            ) {
                Text(
                    text = if (hasSetAlarm) {
                        String.format(Locale.getDefault(), "Alarm: %02d:%02d", selectedHour, selectedMinute)
                    } else {
                        "Set Alarm"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTextColor
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        CyberContinueButton(onClick = onContinue)
    }
}
