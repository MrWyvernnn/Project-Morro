package com.morro.alarm.ui.components

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.morro.alarm.MemoPlayer
import com.morro.alarm.MemoRecorder
import com.morro.alarm.MemoStorage
import com.morro.alarm.mission.MissionPrefs
import com.morro.alarm.mission.MissionType
import com.morro.alarm.mission.PhotoTargetPool
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// ─── Voice Commitment States ─────────────────────────────────────────────────
enum class VoiceState {
    IDLE,
    RECORDING,
    RECORDED_READY
}

/**
 * AlarmBuilderSheet — Anchored bottom sheet for alarm configuration.
 *
 * SPEC (per Manifesto):
 * - Anchored to FAB center (bottom-right), NOT full-width slide-up
 * - Background: Surface at 92% alpha (NO backdrop-filter blur)
 * - Radius: 24dp top-only
 * - Enter: spring from FAB (scale 0.1→1.0, y: FAB.y→0), damping 0.82, response 0.32s (Momentum)
 * - Elevation: 0→8dp spring on enter, 8→0 on exit
 *
 * @param isVisible controls sheet visibility
 * @param onDismiss called when user taps backdrop or dismisses
 * @param fabCenter center of triggering FAB in window coordinates (for anchor animation)
 * @param content sheet content slot
 */
@Composable
fun AlarmBuilderSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    fabCenter: Offset,
    content: @Composable () -> Unit
) {
    val colors = morroColors()
    val shapes = morroShapes()

    if (!isVisible) return

    // One Animatable drives the scrim, the lift and the fade, so the sheet is never half-visible.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, MorroSpring.FloatSpringMomentum)
    }
    val progress = appear.value

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim at 88% black: the dashboard clock and dock never read through the sheet.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f * progress))
                .clickable(onClick = onDismiss)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .offset {
                    IntOffset(x = 0, y = ((1f - progress) * 80f * density).roundToInt())
                }
                .clip(shapes.Sheet)
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, shapes.Sheet)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Drag handle: a single 40dp line, with the air around it done by padding.
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderSubtle)
            )

            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}
/**
 * AlarmBuilderSheetContent — Complete alarm configuration content.
 * Includes time picker, mission selection, and mission-specific configuration.
 *
 * STRUCTURE (per screenshot):
 * - Header: "Configure Alarm" title with "X" close icon
 * - Time Target: Section label "WAKE-UP TARGET", huge digital time display tap-to-adjust
 * - Mission List: Section label "WAKE-UP MISSION", three MorroOptionChip cards:
 *   1. Voice Commitment (record button + yellow checkmark active state)
 *   2. QR / Barcode Scan
 *   3. Object Photo Hunt
 * - Surface: Sheet uses MorroTokens.Surface at 92% alpha
 */
@Composable
fun AlarmBuilderSheetContent(
    onSave: (hour: Int, minute: Int, missionType: MissionType) -> Unit,
    onDismiss: () -> Unit,
    initialHour: Int = 7,
    initialMinute: Int = 0,
    onVoicePermission: () -> Unit = {},
    onVoicePreview: () -> Unit = {},
    onVoiceRerecord: () -> Unit = {}
) {
    val context = LocalContext.current

    // ── Time state ────────────────────────────────────────────────────────────
    var selectedHour by remember { mutableStateOf(initialHour) }
    var selectedMinute by remember { mutableStateOf(initialMinute) }

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
    val hasSaved = remember { mutableStateOf(MemoStorage.hasSavedMemo(context)) }

    // ── Permission launcher ───────────────────────────────────────────────────
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            recordingCountdown = 15
            hasSaved.value = false
            MemoRecorder.startRecording(
                context,
                onError = { reason ->
                    Toast.makeText(context, "Recording error: $reason", Toast.LENGTH_SHORT).show()
                    voiceState = VoiceState.IDLE
                },
                onMaxDurationReached = {
                    voiceState = VoiceState.RECORDED_READY
                    hasSaved.value = MemoStorage.saveMemo(context)
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
            val startRealtime = SystemClock.elapsedRealtime()
            while (voiceState == VoiceState.RECORDING) {
                val elapsed = SystemClock.elapsedRealtime() - startRealtime
                val remainingMs = (15_000L - elapsed).coerceAtLeast(0L)
                recordingCountdown = ((remainingMs + 999L) / 1000L).toInt().coerceIn(0, 15)
                if (remainingMs <= 0L) {
                    MemoRecorder.stopRecording(context)
                    voiceState = VoiceState.RECORDED_READY
                    hasSaved.value = MemoStorage.saveMemo(context)
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

    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header: what this sheet does, and the way out ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Wake-up",
                    style = typo.headlineLarge,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pick a time and the promise you have to keep.",
                    style = typo.bodyMedium,
                    color = colors.textSecondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Section 1: Wake-Up Time Card ──
        Text(
            text = "Time",
            style = typo.labelLarge,
            color = colors.textTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )

        // Huge digital time display, tap to adjust
        TimePickerCard(
            selectedHour = selectedHour,
            selectedMinute = selectedMinute,
            onTimeChange = { hour, minute ->
                selectedHour = hour
                selectedMinute = minute
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Section 2: Mission Selector ──
        Text(
            text = "Mission",
            style = typo.labelLarge,
            color = colors.textTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        )

        // Three distinct MorroOptionChip cards stacked vertically
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Voice Commitment (with record button and yellow checkmark active state)
            MorroOptionChip(
                title = "Voice Commitment",
                subtitle = "15s personal commitment recorded in-app",
                icon = Icons.Filled.Mic,
                accent = colors.accentBubbly,
                isSelected = selectedMission == MissionType.VOICE_COMMITMENT || selectedMission == MissionType.VOICE_MEMO,
                onSelect = { selectedMission = MissionType.VOICE_COMMITMENT },
                showExpanded = selectedMission == MissionType.VOICE_COMMITMENT || selectedMission == MissionType.VOICE_MEMO,
                expandedContent = {
                    VoiceExpandedContent(
                        voiceState = voiceState,
                        recordingCountdown = recordingCountdown,
                        isPlayingPreview = isPlayingPreview,
                        hasSaved = hasSaved,
                        wakeTimeStr = wakeTimeStr,
                        missionTargetName = missionTargetName,
                        onRecord = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onStop = { MemoRecorder.stopRecording(context); voiceState = VoiceState.RECORDED_READY; hasSaved.value = MemoStorage.saveMemo(context) },
                        onPreview = onVoicePreview,
                        onRerecord = onVoiceRerecord
                    )
                }
            )

            // 2. QR / Barcode Scan
            MorroOptionChip(
                title = "QR / Barcode Scan",
                subtitle = "CameraX + offline ML Kit barcode scanner",
                icon = Icons.Filled.QrCodeScanner,
                isSelected = selectedMission == MissionType.QR_CODE || selectedMission == MissionType.QR_BARCODE,
                onSelect = { selectedMission = MissionType.QR_CODE },
                showExpanded = selectedMission == MissionType.QR_CODE || selectedMission == MissionType.QR_BARCODE,
                expandedContent = {
                    QrExpandedContent(
                        qrCodeValue = qrCodeValue,
                        showQrDialog = showQrDialog,
                        onQrDialogChange = { showQrDialog = it },
                        context = context,
                        selectedMission = selectedMission
                    )
                }
            )

            // 3. Object Photo Hunt
            MorroOptionChip(
                title = "Object Photo Hunt",
                subtitle = "30-item household pool, 1 random item on ring",
                icon = Icons.Filled.PhotoCamera,
                isSelected = selectedMission == MissionType.PHOTO_MISSION || selectedMission == MissionType.PHOTO_OBJECT,
                onSelect = { selectedMission = MissionType.PHOTO_MISSION },
                showExpanded = selectedMission == MissionType.PHOTO_MISSION || selectedMission == MissionType.PHOTO_OBJECT,
                expandedContent = {
                    PhotoExpandedContent(
                        selectedPhotoItems = selectedPhotoItems,
                        showPhotoSheet = showPhotoSheet,
                        onPhotoSheetChange = { showPhotoSheet = it },
                        context = context,
                        selectedMission = selectedMission
                    )
                }
            )

            // 4. Math puzzle — reserved. Not wired end to end, so it is shown honestly as SOON.
            MorroOptionChip(
                title = "Math Puzzle",
                subtitle = "Wiring in progress — not part of this build",
                icon = Icons.Filled.Calculate,
                isSelected = false,
                onSelect = {},
                enabled = false
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Bottom CTA: Save & Arm Alarm ──
        MorroPrimaryButton(
            text = "Save and arm",
            onClick = {
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
                    return@MorroPrimaryButton
                }

                onSave(selectedHour, selectedMinute, selectedMission)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * TimePickerCard — one bento card: the wake-up time at display scale and a pill that says what to
 * do with it. Tapping anywhere on the card opens the system time picker.
 */
@Composable
private fun TimePickerCard(
    selectedHour: Int,
    selectedMinute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.Card)
            .background(colors.surfaceCard)
            .morroHairline(radius = 16.dp)
            .clickable {
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute -> onTimeChange(hourOfDay, minute) },
                    selectedHour,
                    selectedMinute,
                    true
                ).show()
            }
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
            style = typo.displayHero.copy(fontSize = 62.sp, lineHeight = 66.sp),
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Tap to change",
                color = colors.textTertiary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * MorroOptionChip — one mission. Selected takes the ribbon border, a pink icon and a pink check;
 * a disabled mission says SOON and explains itself in the subtitle instead of pretending to work.
 */
@Composable
private fun MorroOptionChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    showExpanded: Boolean = false,
    accent: Color = Color.Unspecified,
    enabled: Boolean = true,
    expandedContent: @Composable (() -> Unit)? = null
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()
    val accentColor = if (accent == Color.Unspecified) colors.accentBubbly else accent

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = MorroSpring.FloatSpringPlayful,
        label = "missionPress"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(shapes.Card)
                .background(if (isSelected) colors.surfaceElevated else colors.surfaceCard)
                .morroHairline(
                    radius = 16.dp,
                    color = if (isSelected) colors.borderActive else colors.borderSubtle
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onSelect
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) colors.accentTactical
                        else if (enabled) colors.textSecondary else colors.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = title,
                            style = typo.titleMedium,
                            color = if (enabled) colors.textPrimary else colors.textTertiary
                        )
                        Text(
                            text = subtitle,
                            style = typo.bodyMedium,
                            color = if (enabled) colors.textSecondary else colors.textTertiary
                        )
                    }
                }

                if (!enabled) {
                    Text(
                        text = "Soon",
                        color = colors.textTertiary,
                        fontSize = 11.sp
                    )
                } else if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = colors.accentTactical,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (showExpanded && enabled && expandedContent != null) {
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedVisibility(visible = true) {
                MorroCard(modifier = Modifier.fillMaxWidth()) {
                    expandedContent()
                }
            }
        }
    }
}

/**
 * VoiceExpandedContent — Recording/Preview/Re-record UI for Voice mission.
 */
@Composable
private fun VoiceExpandedContent(
    voiceState: VoiceState,
    recordingCountdown: Int,
    isPlayingPreview: Boolean,
    hasSaved: androidx.compose.runtime.MutableState<Boolean>,
    wakeTimeStr: String,
    missionTargetName: String,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onPreview: () -> Unit,
    onRerecord: () -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        when (voiceState) {
            VoiceState.IDLE -> {
                if (!hasSaved.value) {
                    Text(
                        text = "Speak clearly: \"I am waking up at $wakeTimeStr tomorrow to protect my $missionTargetName.\"",
                        style = typo.bodyMedium,
                        color = colors.textSecondary,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    MorroPrimaryButton(
                        text = "Record your promise",
                        onClick = onRecord,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = true
                    )
                } else {
                    // Show locked state with yellow checkmark
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = colors.accentTactical,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voice promise ready",
                                style = typo.bodyMedium,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPreview,
                            enabled = !isPlayingPreview,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.textPrimary
                            )
                        ) {
                            Text(
                                text = if (isPlayingPreview) "Playing" else "Play back",
                                style = typo.labelLarge.copy(fontWeight = FontWeight.Medium),
                                color = colors.textPrimary
                            )
                        }
                        OutlinedButton(
                            onClick = onRerecord,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.textSecondary
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Re-record",
                                    style = typo.labelLarge.copy(fontWeight = FontWeight.Medium),
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            VoiceState.RECORDING -> {
                // Animated Waveform
                AnimatedWaveform(
                    isRecording = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Countdown timer
                Text(
                    text = String.format(Locale.getDefault(), "00:%02d", recordingCountdown),
                    style = typo.displayLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Stop & Lock button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceElevated)
                        .morroHairline(radius = 12.dp, color = colors.borderStrong)
                        .clickable(onClick = onStop),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Stop and lock",
                            style = typo.titleMedium,
                            color = colors.textPrimary
                        )
                    }
                }
            }

            VoiceState.RECORDED_READY -> {
                if (!hasSaved.value && MemoStorage.hasTempMemo(context)) {
                    LaunchedEffect(Unit) {
                        MemoStorage.saveMemo(context)
                        hasSaved.value = true
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = colors.accentTactical,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Voice promise ready",
                            style = typo.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onPreview,
                        enabled = !isPlayingPreview,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textPrimary
                        )
                    ) {
                        Text(
                            text = if (isPlayingPreview) "Playing" else "Play back",
                            style = typo.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary
                        )
                    }
                    OutlinedButton(
                        onClick = onRerecord,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.textSecondary
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Re-record",
                                style = typo.labelLarge.copy(fontWeight = FontWeight.Medium),
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * QrExpandedContent — Optional barcode registration for QR mission.
 */
@Composable
private fun QrExpandedContent(
    qrCodeValue: String,
    showQrDialog: Boolean,
    onQrDialogChange: (Boolean) -> Unit,
    context: Context,
    selectedMission: MissionType
) {
    val colors = morroColors()
    val typo = morroTypography()

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceLow)
                .clip(RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (qrCodeValue.isNotBlank()) "Target: $qrCodeValue" else "Status: Any QR/Barcode accepted",
                style = typo.bodyMedium,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        MorroPrimaryButton(
            text = "SCAN & LOCK TARGET BARCODE (OPTIONAL)",
            onClick = { onQrDialogChange(true) },
            modifier = Modifier.fillMaxWidth(),
            enabled = true
        )
    }

    // QR Code Dialog
    if (showQrDialog) {
        QrCodeDialog(
            qrCodeValue = qrCodeValue,
            onDismiss = { onQrDialogChange(false) },
            onSave = { newCode ->
                val trimmed = newCode.trim()
                MissionPrefs.saveMission(
                    context,
                    selectedMission,
                    trimmed,
                    MissionPrefs.getSelectedPhotoItems(context)
                )
                onQrDialogChange(false)
            }
        )
    }
}

/**
 * QrCodeDialog — Register specific barcode/QR payload.
 */
@Composable
private fun QrCodeDialog(
    qrCodeValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()
    var tempCode by remember { mutableStateOf(qrCodeValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("Register Specific Code", style = typo.headlineMedium, color = colors.textPrimary)
        },
        text = {
            Column {
                Text(
                    "Set custom barcode/QR payload to require at alarm time, or clear to allow any code.",
                    style = typo.bodyMedium,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = tempCode,
                    onValueChange = { tempCode = it },
                    singleLine = true,
                    placeholder = { Text("e.g. 012345678905", style = typo.bodyMedium, color = colors.textDisabled) },
                    label = { Text("Barcode/QR Payload", style = typo.bodyMedium, color = colors.textSecondary) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(tempCode)
            }) {
                Text("Save", style = typo.labelLarge.copy(fontWeight = FontWeight.Bold), color = colors.accentTactical)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = typo.labelLarge, color = colors.textSecondary)
            }
        }
    )
}

/**
 * PhotoExpandedContent — Configure object pool for Photo mission.
 */
@Composable
private fun PhotoExpandedContent(
    selectedPhotoItems: Set<String>,
    showPhotoSheet: Boolean,
    onPhotoSheetChange: (Boolean) -> Unit,
    context: Context,
    selectedMission: MissionType
) {
    val colors = morroColors()
    val typo = morroTypography()

    MorroPrimaryButton(
        text = "CONFIGURE OBJECT POOL (${selectedPhotoItems.size} ITEMS ACTIVE) →",
        onClick = { onPhotoSheetChange(true) },
        modifier = Modifier.fillMaxWidth(),
        enabled = true
    )

    // Photo Target Sheet (2-Column Bento Grid Modal)
    if (showPhotoSheet) {
        PhotoTargetSheet(
            selectedPhotoItems = selectedPhotoItems,
            onDismiss = { onPhotoSheetChange(false) },
            onSave = { newItems ->
                MissionPrefs.saveMission(
                    context,
                    selectedMission,
                    MissionPrefs.getQrCode(context),
                    newItems
                )
                onPhotoSheetChange(false)
            }
        )
    }
}

/**
 * PhotoTargetSheet — 2-Column Bento Grid for 30 household items.
 */
@Composable
private fun PhotoTargetSheet(
    selectedPhotoItems: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()
    var localSelectedItems by remember { mutableStateOf(selectedPhotoItems) }

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

    // Modal overlay approach
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(onClick = onDismiss)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(colors.surface)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .align(Alignment.BottomCenter),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(colors.borderSubtle)
                        .clip(RoundedCornerShape(2.dp))
                        .padding(top = 12.dp, bottom = 16.dp)
                )

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wake-Up Object Pool",
                        style = typo.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Box(
                        modifier = Modifier
                            .background(colors.accentTactical.copy(alpha = 0.15f))
                            .clip(RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${localSelectedItems.size} Selected",
                            style = typo.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.accentTactical
                        )
                    }
                }

                // Subtitle
                Text(
                    text = "At alarm time, Morro randomly picks one object from your pool. You must photograph it to silence the alarm.",
                    style = typo.bodyMedium,
                    color = colors.textSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 16.dp)
                )

                // Quick-Select Filter Pills
                FilterPillsRow(
                    localSelectedItems = localSelectedItems,
                    onSelectionChange = { localSelectedItems = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2-Column Bento Grid Tiles (30 Items)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(PhotoTargetPool.ITEMS) { item ->
                        val isSelected = localSelectedItems.contains(item.id)
                        val emoji = itemEmojiMap[item.displayName] ?: "📦"

                        Box(
                            modifier = Modifier
                                .height(76.dp)
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) colors.accentTactical.copy(alpha = 0.10f) else colors.surfaceLow,
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) colors.accentTactical else colors.borderSubtle,
                                    RoundedCornerShape(16.dp)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    localSelectedItems = if (isSelected) {
                                        localSelectedItems - item.id
                                    } else {
                                        localSelectedItems + item.id
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
                                            .background(colors.surfaceVariant, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 18.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = item.displayName,
                                        style = typo.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Top-Right Corner: 18.dp circular indicator
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(
                                            if (isSelected) colors.accentTactical else Color.Transparent,
                                            CircleShape
                                        )
                                        .border(
                                            1.5.dp,
                                            if (isSelected) colors.accentTactical else colors.borderSubtle,
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
                }

                // Sticky Bottom Action Bar
                Spacer(modifier = Modifier.height(16.dp))
                MorroPrimaryButton(
                    text = "CONFIRM ${localSelectedItems.size} OBJECTS",
                    onClick = {
                        onSave(localSelectedItems)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 24.dp),
                    enabled = localSelectedItems.isNotEmpty()
                )
            }
        }
    }
}

/**
 * FilterPillsRow — Quick-select filter chips for Photo mission.
 */
@Composable
private fun FilterPillsRow(
    localSelectedItems: Set<String>,
    onSelectionChange: (Set<String>) -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        item {
            FilterChip(
                selected = false,
                onClick = { onSelectionChange(PhotoTargetPool.COMMON_10_IDS.toSet()) },
                label = { Text("✨ Common 10", style = typo.labelLarge) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceLow,
                    labelColor = colors.accentTactical
                ),
                shape = RoundedCornerShape(50)
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = { onSelectionChange(PhotoTargetPool.ITEMS.map { it.id }.toSet()) },
                label = { Text("Select All 30", style = typo.labelLarge) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceLow,
                    labelColor = colors.accentTactical
                ),
                shape = RoundedCornerShape(50)
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = { onSelectionChange(setOf("toothbrush", "sink", "mirror", "towel", "scissors")) },
                label = { Text("Bathroom", style = typo.labelLarge) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceLow,
                    labelColor = colors.accentTactical
                ),
                shape = RoundedCornerShape(50)
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = { onSelectionChange(setOf("keyboard", "mouse", "monitor", "laptop", "remote", "headphones", "desk", "pen", "lamp")) },
                label = { Text("Desk & Tech", style = typo.labelLarge) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceLow,
                    labelColor = colors.accentTactical
                ),
                shape = RoundedCornerShape(50)
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = { onSelectionChange(setOf("cup", "bottle", "spoon", "fork")) },
                label = { Text("Kitchen", style = typo.labelLarge) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceLow,
                    labelColor = colors.accentTactical
                ),
                shape = RoundedCornerShape(50)
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = { onSelectionChange(emptySet()) },
                label = { Text("Clear", style = typo.labelLarge) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceLow,
                    labelColor = colors.textDisabled
                ),
                shape = RoundedCornerShape(50)
            )
        }
    }
}

/**
 * AnimatedWaveform — Concentric amplitude rings for voice recording.
 */
@Composable
private fun AnimatedWaveform(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = morroColors()
    val density = LocalDensity.current.density

    // 12 bars, each with its own phase offset
    val phases = remember {
        List(12) { it * 0.15f }
    }

    // Use infinite transition for smooth animation
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "waveform")

    val heights = phases.map { phase ->
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 600,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
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
        heights.forEachIndexed { _, animState: androidx.compose.runtime.State<Float> ->
            val heightFraction by animState

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height((50 * heightFraction).dp)
                    .background(
                        color = if (isRecording) colors.accentTactical else colors.borderSubtle,
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}