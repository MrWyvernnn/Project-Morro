package com.morro.alarm.ui.components

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.morro.alarm.AlarmScheduler
import com.morro.alarm.MemoPlayer
import com.morro.alarm.MemoRecorder
import com.morro.alarm.MemoStorage
import com.morro.alarm.mission.MissionPrefs
import com.morro.alarm.mission.MissionType
import com.morro.alarm.mission.PhotoTargetPool
import com.morro.alarm.ui.components.VoiceState
import kotlinx.coroutines.delay
import java.util.Locale

// ─── Animated Waveform ───────────────────────────────────────────────────────
@Composable
private fun AnimatedWaveform(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    // 12 bars, each with its own phase offset
    val phases = remember {
        List(12) { it * 0.15f }
    }

    val heights = phases.map { phase ->
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(offsetMillis = (phase * 1000).toInt())
            ),
            label = "bar$phase"
        )
    }

    Row(
        modifier = modifier
            .height(60.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEachIndexed { _, animState ->
            val heightFraction by animState

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height((50 * heightFraction).dp)
                    .background(
                        color = if (isRecording) Color(0xFFFFD54F) else Color(0xFF333344),
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

// ─── Time Display Formatter ──────────────────────────────────────────────────
private fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmBuilderModal(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onSaveAndArm: (hour: Int, minute: Int, missionType: MissionType) -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val context = LocalContext.current

    // ── Time state ────────────────────────────────────────────────────────────
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    // ── Mission selection ─────────────────────────────────────────────────────
    var selectedMission by remember { mutableStateOf(MissionPrefs.getMissionType(context)) }

    // ── QR code ───────────────────────────────────────────────────────────────
    var qrCodeValue by remember { mutableStateOf(MissionPrefs.getQrCode(context)) }
    var showQrDialog by remember { mutableStateOf(false) }

    // ── Photo items ───────────────────────────────────────────────────────────
    var selectedPhotoItems by remember { mutableStateOf(MissionPrefs.getSelectedPhotoItems(context)) }
    var showPhotoSheet by remember { mutableStateOf(false) }

    // ── Voice commitment state machine ───────────────────────────────────────
    var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
    var recordingCountdown by remember { mutableIntStateOf(15) }
    var isPlayingPreview by remember { mutableStateOf(false) }
    var hasSaved by remember { mutableStateOf(MemoStorage.hasSavedMemo(context)) }

    // ── Permission launcher ───────────────────────────────────────────────────
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            recordingCountdown = 15
            hasSaved = false
            MemoRecorder.startRecording(
                context,
                onError = { reason ->
                    Toast.makeText(context, "Recording error: $reason", Toast.LENGTH_SHORT).show()
                    voiceState = VoiceState.IDLE
                },
                onMaxDurationReached = {
                    voiceState = VoiceState.RECORDED_READY
                    hasSaved = MemoStorage.saveMemo(context)
                }
            )
            voiceState = VoiceState.RECORDING
        } else {
            Toast.makeText(context, "Microphone permission is required to record your voice promise.", Toast.LENGTH_LONG).show()
        }
    }

    // ── Recording countdown tick (Drift-Free Monotonic Countdown) ───────────
    LaunchedEffect(voiceState) {
        if (voiceState == VoiceState.RECORDING) {
            recordingCountdown = 15
            val startRealtime = android.os.SystemClock.elapsedRealtime()
            while (voiceState == VoiceState.RECORDING) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - startRealtime
                val remainingMs = (15_000L - elapsed).coerceAtLeast(0L)
                recordingCountdown = ((remainingMs + 999L) / 1000L).toInt().coerceIn(0, 15)
                if (remainingMs <= 0L) {
                    MemoRecorder.stopRecording(context)
                    voiceState = VoiceState.RECORDED_READY
                    hasSaved = MemoStorage.saveMemo(context)
                    break
                }
                delay(100L)
            }
        }
    }

    // ── Build time string for voice prompt ───────────────────────────────────
    val wakeTimeStr = remember(selectedHour, selectedMinute) {
        val ampm = if (selectedHour >= 12) "PM" else "AM"
        val displayHour = when {
            selectedHour == 0 -> 12
            selectedHour > 12 -> selectedHour - 12
            else -> selectedHour
        }
        "$displayHour:${String.format(Locale.getDefault(), "%02d", selectedMinute)} $ampm"
    }

    val missionTargetName = remember {
        context.getSharedPreferences("morro_prefs", Context.MODE_PRIVATE)
            .getString("onboarding_target", "mission") ?: "mission"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0F),
        contentColor = Color(0xFFFFFFFF),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFF333344), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Bar ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configure Alarm",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFFFFF)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFFFFFFF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Section 1: Wake-Up Time Card ────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D14), RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                selectedHour = hourOfDay
                                selectedMinute = minute
                            },
                            selectedHour,
                            selectedMinute,
                            true
                        ).show()
                    }
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Top pill badge: 'WAKE-UP TARGET' in 11.sp tracking-wide #999999
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF161622), RoundedCornerShape(50))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "WAKE-UP TARGET",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF999999),
                            letterSpacing = 1.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Center: Massive 72.sp bold time in #FFD54F with radial glow behind digits
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(width = 240.dp, height = 90.dp)
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFFFD54F).copy(alpha = 0.20f),
                                                Color(0xFFFFD54F).copy(alpha = 0.05f),
                                                Color.Transparent
                                            ),
                                            center = center,
                                            radius = size.maxDimension / 1.5f
                                        )
                                    )
                                }
                        )
                        Text(
                            text = formatTime(selectedHour, selectedMinute),
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F),
                            letterSpacing = (-2).sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bottom hint pill: 'Tap digits to adjust time' in #999999
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF161622), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Tap digits to adjust time",
                            fontSize = 12.sp,
                            color = Color(0xFF999999)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Section 2: Mission Selector Cards ──────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("WAKE-UP MISSION")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card 1: Voice Commitment
            MissionCard(
                title = "Voice Commitment",
                subtitle = "Record a 15s promise played back unskippably.",
                icon = Icons.Filled.Mic,
                isSelected = selectedMission == MissionType.VOICE_COMMITMENT,
                onClick = {
                    selectedMission = MissionType.VOICE_COMMITMENT
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!hasPerm) {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            ) {
                if (selectedMission == MissionType.VOICE_COMMITMENT) {
                    Spacer(modifier = Modifier.height(16.dp))

                    when (voiceState) {
                        VoiceState.IDLE -> {
                            Text(
                                text = "Speak clearly: \"I am waking up at $wakeTimeStr tomorrow to protect my $missionTargetName. No excuses.\"",
                                fontSize = 13.sp,
                                color = Color(0xFFAAAAAA),
                                lineHeight = 20.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        recordingCountdown = 15
                                        hasSaved = false
                                        MemoRecorder.startRecording(
                                            context,
                                            onError = { reason ->
                                                Toast.makeText(context, "Recording error: $reason", Toast.LENGTH_SHORT).show()
                                                voiceState = VoiceState.IDLE
                                            },
                                            onMaxDurationReached = {
                                                voiceState = VoiceState.RECORDED_READY
                                                hasSaved = MemoStorage.saveMemo(context)
                                            }
                                        )
                                        voiceState = VoiceState.RECORDING
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFD54F),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tap to Record (15s)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        VoiceState.RECORDING -> {
                            AnimatedWaveform(
                                isRecording = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "00:${String.format(Locale.getDefault(), "%02d", recordingCountdown)}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    MemoRecorder.stopRecording(context)
                                    voiceState = VoiceState.RECORDED_READY
                                    hasSaved = MemoStorage.saveMemo(context)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF5252),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Stop & Lock Clip",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        VoiceState.RECORDED_READY -> {
                            LaunchedEffect(Unit) {
                                if (!hasSaved && MemoStorage.hasTempMemo(context)) {
                                    MemoStorage.saveMemo(context)
                                    hasSaved = true
                                    Toast.makeText(context, "Voice promise saved.", Toast.LENGTH_SHORT).show()
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = Color(0xFFFFD54F).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(50)
                                        )
                                        .border(1.5.dp, Color(0xFFFFD54F), RoundedCornerShape(50))
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color(0xFFFFD54F),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Voice Promise Locked",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFFFFD54F)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isPlayingPreview = true
                                        MemoPlayer.startPlayback(
                                            context,
                                            MemoStorage.getTempPath(context),
                                            onCompletion = { isPlayingPreview = false },
                                            onError = {
                                                isPlayingPreview = false
                                                Toast.makeText(context, "Playback failed", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    enabled = !isPlayingPreview,
                                    border = BorderStroke(1.dp, Color(0xFF555566)),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (isPlayingPreview) "Playing..." else "Preview Voice",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        MemoPlayer.stopPlayback()
                                        isPlayingPreview = false
                                        MemoRecorder.cancelRecording(context)
                                        hasSaved = false
                                        voiceState = VoiceState.IDLE
                                    },
                                    border = BorderStroke(1.dp, Color(0xFF555566)),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Re-record",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card 2: QR / Barcode Scan
            MissionCard(
                title = "QR / Barcode Scan",
                subtitle = "Force yourself out of bed to scan a code.",
                icon = Icons.Filled.QrCodeScanner,
                isSelected = selectedMission == MissionType.QR_CODE,
                onClick = { selectedMission = MissionType.QR_CODE }
            ) {
                if (selectedMission == MissionType.QR_CODE) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF161622), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (qrCodeValue.isNotBlank()) "Target: $qrCodeValue" else "Status: Any QR/Barcode accepted",
                            fontSize = 12.sp,
                            color = Color(0xFF999999)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showQrDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF161622),
                            contentColor = Color(0xFFFFD54F)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan & Lock Target Barcode (Optional)", fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card 3: Object Photo Hunt
            MissionCard(
                title = "Object Photo Hunt",
                subtitle = "Take a photo of a random household object.",
                icon = Icons.Filled.PhotoCamera,
                isSelected = selectedMission == MissionType.PHOTO_MISSION,
                onClick = { selectedMission = MissionType.PHOTO_MISSION }
            ) {
                if (selectedMission == MissionType.PHOTO_MISSION) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showPhotoSheet = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF161622),
                            contentColor = Color(0xFFFFD54F)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFFD54F)),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Configure Object Pool (${selectedPhotoItems.size} items active) →",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Bottom CTA: Save & Arm Alarm ────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFFFFD54F), RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        val needsVoiceRecording = selectedMission == MissionType.VOICE_COMMITMENT ||
                                selectedMission == MissionType.VOICE_MEMO

                        if (needsVoiceRecording && !MemoStorage.hasSavedMemo(context)) {
                            Toast
                                .makeText(
                                    context,
                                    "Please record your 15-second voice promise first",
                                    Toast.LENGTH_LONG
                                )
                                .show()
                            selectedMission = MissionType.VOICE_COMMITMENT
                            voiceState = VoiceState.IDLE
                            return@clickable
                        }

                        AlarmScheduler.scheduleDailyAlarm(context, selectedHour, selectedMinute)
                        MissionPrefs.saveMission(context, selectedMission, qrCodeValue, selectedPhotoItems)
                        onSaveAndArm(selectedHour, selectedMinute, selectedMission)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SAVE & ARM ALARM",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── QR Code Dialog ────────────────────────────────────────────────────────
    if (showQrDialog) {
        var tempCode by remember { mutableStateOf(qrCodeValue) }
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            containerColor = Color(0xFF0A0A0F),
            title = {
                Text("Register Specific Code", color = Color(0xFFFFFFFF), fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Set custom barcode/QR payload to require at alarm time, or clear to allow any code.",
                        color = Color(0xFF999999),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempCode,
                        onValueChange = { tempCode = it },
                        singleLine = true,
                        placeholder = { Text("e.g. 012345678905", color = Color(0xFF555555)) },
                        colors = TextFieldColors(
                            focusedTextColor = Color(0xFFFFFFFF),
                            unfocusedTextColor = Color(0xFFFFFFFF),
                            disabledTextColor = Color(0xFF555555),
                            errorTextColor = Color(0xFFFF5252),
                            focusedContainerColor = Color.Unspecified,
                            unfocusedContainerColor = Color.Unspecified,
                            disabledContainerColor = Color.Unspecified,
                            errorContainerColor = Color.Unspecified,
                            cursorColor = Color(0xFFFFD54F),
                            errorCursorColor = Color(0xFFFF5252),
                            textSelectionColors = TextSelectionColors(
                    handleColor = Color(0xFFFFD54F),
                    backgroundColor = Color(0xFFFFD54F).copy(alpha = 0.3f)
                ),
                            focusedIndicatorColor = Color(0xFFFFD54F),
                            unfocusedIndicatorColor = Color(0xFF1F1F2E),
                            disabledIndicatorColor = Color.Unspecified,
                            errorIndicatorColor = Color(0xFFFF5252),
                            focusedLabelColor = Color.Unspecified,
                            unfocusedLabelColor = Color.Unspecified,
                            disabledLabelColor = Color.Unspecified,
                            errorLabelColor = Color.Unspecified,
                            focusedLeadingIconColor = Color.Unspecified,
                            unfocusedLeadingIconColor = Color.Unspecified,
                            disabledLeadingIconColor = Color.Unspecified,
                            errorLeadingIconColor = Color.Unspecified,
                            focusedTrailingIconColor = Color.Unspecified,
                            unfocusedTrailingIconColor = Color.Unspecified,
                            disabledTrailingIconColor = Color.Unspecified,
                            errorTrailingIconColor = Color.Unspecified,
                            focusedPlaceholderColor = Color(0xFF555555),
                            unfocusedPlaceholderColor = Color(0xFF555555),
                            disabledPlaceholderColor = Color(0xFF555555),
                            errorPlaceholderColor = Color(0xFFFF5252),
                            focusedSupportingTextColor = Color.Unspecified,
                            unfocusedSupportingTextColor = Color.Unspecified,
                            disabledSupportingTextColor = Color.Unspecified,
                            errorSupportingTextColor = Color.Unspecified,
                            focusedPrefixColor = Color.Unspecified,
                            unfocusedPrefixColor = Color.Unspecified,
                            disabledPrefixColor = Color.Unspecified,
                            errorPrefixColor = Color.Unspecified,
                            focusedSuffixColor = Color.Unspecified,
                            unfocusedSuffixColor = Color.Unspecified,
                            disabledSuffixColor = Color.Unspecified,
                            errorSuffixColor = Color.Unspecified
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    qrCodeValue = tempCode.trim()
                    MissionPrefs.saveMission(context, selectedMission, qrCodeValue, selectedPhotoItems)
                    showQrDialog = false
                }) {
                    Text("Save", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Cancel", color = Color(0xFF999999))
                }
            }
        )
    }

    // ── Photo Target Sheet (2-Column Bento Grid Modal) ─────────────────────────
    if (showPhotoSheet) {
        val itemEmojiMap = remember {
            mapOf(
                "Cup / Mug" to "☕",
                "Toothbrush" to "🪥",
                "Sink / Faucet" to "🚰",
                "Chair" to "🪑",
                "Keyboard" to "⌨️",
                "Computer Mouse" to "🖱️",
                "Shoe / Sneaker" to "👟",
                "Book" to "📖",
                "Water Bottle" to "🧴",
                "Glasses / Eyewear" to "👓",
                "Spoon" to "🥄",
                "Fork" to "🍴",
                "Houseplant" to "🪴",
                "Pillow / Cushion" to "🛏️",
                "Wristwatch" to "⌚",
                "Backpack / Bag" to "🎒",
                "Door / Doorhandle" to "🚪",
                "Monitor / TV" to "🖥️",
                "Laptop" to "💻",
                "Remote Control" to "📺",
                "Scissors" to "✂️",
                "Towel" to "🧺",
                "Desk / Table" to "🪵",
                "Lamp / Light" to "💡",
                "Mirror" to "🪞",
                "Pen / Pencil" to "🖊️",
                "Headphones / Earbuds" to "🎧",
                "Wallet" to "👛",
                "Keys / Keychain" to "🔑",
                "Wall Clock" to "🕰️"
            )
        }

        ModalBottomSheet(
            onDismissRequest = { showPhotoSheet = false },
            containerColor = Color(0xFF0A0A0F),
            contentColor = Color(0xFFFFFFFF),
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color(0xFF333344), RoundedCornerShape(2.dp))
                )
            }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Block
                item(span = { GridItemSpan(2) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Wake-Up Object Pool",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFFFFF)
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFD54F).copy(alpha = 0.15f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${selectedPhotoItems.size} Selected",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F)
                            )
                        }
                    }
                }

                // Subtitle
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "At alarm time, Morro randomly picks one object from your pool. You must photograph it to silence the alarm.",
                        fontSize = 13.sp,
                        color = Color(0xFF999999),
                        lineHeight = 18.sp
                    )
                }

                // Quick-Select Filter Pills
                item(span = { GridItemSpan(2) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { selectedPhotoItems = PhotoTargetPool.COMMON_10_ITEMS.toSet() },
                                label = { Text("✨ Common 10") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF161622),
                                    labelColor = Color(0xFFFFD54F)
                                ),
                                shape = RoundedCornerShape(50)
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { selectedPhotoItems = PhotoTargetPool.ALL_ITEMS.toSet() },
                                label = { Text("Select All 30") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF161622),
                                    labelColor = Color(0xFFFFD54F)
                                ),
                                shape = RoundedCornerShape(50)
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    selectedPhotoItems = setOf("Toothbrush", "Sink / Faucet", "Mirror", "Towel", "Scissors")
                                },
                                label = { Text("Bathroom") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF161622),
                                    labelColor = Color(0xFFFFD54F)
                                ),
                                shape = RoundedCornerShape(50)
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    selectedPhotoItems = setOf(
                                        "Keyboard", "Computer Mouse", "Monitor / TV", "Laptop",
                                        "Remote Control", "Headphones / Earbuds", "Desk / Table",
                                        "Pen / Pencil", "Lamp / Light"
                                    )
                                },
                                label = { Text("Desk & Tech") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF161622),
                                    labelColor = Color(0xFFFFD54F)
                                ),
                                shape = RoundedCornerShape(50)
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    selectedPhotoItems = setOf("Cup / Mug", "Water Bottle", "Spoon", "Fork")
                                },
                                label = { Text("Kitchen") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF161622),
                                    labelColor = Color(0xFFFFD54F)
                                ),
                                shape = RoundedCornerShape(50)
                            )
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { selectedPhotoItems = emptySet() },
                                label = { Text("Clear") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF161622),
                                    labelColor = Color(0xFF999999)
                                ),
                                shape = RoundedCornerShape(50)
                            )
                        }
                    }
                }

                // 2-Column Bento Grid Tiles (30 Items)
                items(PhotoTargetPool.ALL_ITEMS) { itemName ->
                    val isSelected = selectedPhotoItems.contains(itemName)
                    val emoji = itemEmojiMap[itemName] ?: "📦"

                    Box(
                        modifier = Modifier
                            .height(76.dp)
                            .fillMaxWidth()
                            .background(
                                if (isSelected) Color(0xFFFFD54F).copy(alpha = 0.10f) else Color(0xFF12121A),
                                RoundedCornerShape(16.dp)
                            )
                            .border(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) Color(0xFFFFD54F) else Color(0xFF1F1F2E),
                                RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                selectedPhotoItems = if (isSelected) {
                                    selectedPhotoItems - itemName
                                } else {
                                    selectedPhotoItems + itemName
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // 38.dp circular badge with distinct emoji
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFF1F1F2E), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = itemName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color(0xFFFFFFFF) else Color(0xFFCCCCDD),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Top-Right Corner: 18.dp circular indicator (solid #FFD54F with black ✓ selected, subtle #2A2A38 ring unselected)
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(
                                        if (isSelected) Color(0xFFFFD54F) else Color.Transparent,
                                        CircleShape
                                    )
                                    .border(
                                        1.5.dp,
                                        if (isSelected) Color(0xFFFFD54F) else Color(0xFF2A2A38),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Sticky Bottom Action Bar
                item(span = { GridItemSpan(2) }) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            MissionPrefs.saveMission(context, selectedMission, qrCodeValue, selectedPhotoItems)
                            showPhotoSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD54F),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF333344),
                            disabledContentColor = Color(0xFF888888)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        enabled = selectedPhotoItems.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "CONFIRM ${selectedPhotoItems.size} OBJECTS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (selectedPhotoItems.isNotEmpty()) Color.Black else Color(0xFF888888)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// ─── Section Label ───────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF999999),
        letterSpacing = 1.5.sp
    )
}

// ─── Mission Selector Card ───────────────────────────────────────────────────
@Composable
private fun MissionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    val borderColor = if (isSelected) Color(0xFFFFD54F) else Color(0xFF1F1F2E)
    val iconBgColor = if (isSelected) Color(0xFFFFD54F).copy(alpha = 0.12f) else Color(0xFF161622)
    val iconColor = if (isSelected) Color(0xFFFFD54F) else Color(0xFF888899)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D14), RoundedCornerShape(20.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: 48.dp squircle (14.dp radius) icon container
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(iconBgColor, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Center Column: Title in 17.sp SemiBold Warm White, Subtitle in 13.sp #999999
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFFFFF)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = Color(0xFF999999)
                    )
                }

                // Right: Custom 24.dp Circular Radio Indicator
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            if (isSelected) Color(0xFFFFD54F) else Color.Transparent,
                            CircleShape
                        )
                        .border(
                            1.5.dp,
                            if (isSelected) Color(0xFFFFD54F) else Color(0xFF333344),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Expanded Action Footer inside Selected Card
            content()
        }
    }
}
