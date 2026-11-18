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
import androidx.compose.animation.core.animate
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.MemoRecorder
import com.morro.alarm.MemoStorage
import com.morro.alarm.mission.MissionPrefs
import com.morro.alarm.mission.MissionType
import com.morro.alarm.mission.PhotoTargetPool
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.motion.staggeredReveal
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * @param content sheet content slot
 */
@Composable
fun AlarmBuilderSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = morroColors()
    val shapes = morroShapes()

    // Enter eases out from a 0.95 scale, exit runs at 80% of that duration back to 0.95. The sheet
    // stays composed until its exit finishes, so dismissing does not cut the animation off.
    val appear = remember { Animatable(0f) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(isVisible) {
        if (reduced) {
            appear.snapTo(if (isVisible) 1f else 0f)
        } else {
            appear.animateTo(
                targetValue = if (isVisible) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (isVisible) 240 else MorroMotion.exitMillis(240),
                    easing = if (isVisible) MorroMotion.EnterEasing else MorroMotion.ExitEasing
                )
            )
        }
    }
    val progress = appear.value
    if (!isVisible && progress == 0f) return

    // The handle is the only drag target, so it cannot fight the sheet's inner scroll.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val dismissAt = with(LocalDensity.current) { 120.dp.toPx() }


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
                .graphicsLayer {
                    val s = 0.95f + 0.05f * progress
                    scaleX = s
                    scaleY = s
                    alpha = progress
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationY = (1f - progress) * 40f * density + dragOffset
                }
                .clip(shapes.Sheet)
                .background(colors.surface)
                .border(1.dp, colors.borderSubtle, shapes.Sheet)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Drag handle: a single 40dp line that stretches to 112% while pulled, the way a
            // physical knob deforms under a finger. It is the sheet's only drag target.
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    // Stretch is applied in the draw phase so a drag does not recompose the sheet.
                    .graphicsLayer {
                        scaleX = 1f + 0.12f * (dragOffset / dismissAt).coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderSubtle)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffset > dismissAt * 0.5f) onDismiss()
                                scope.settleDrag(dragOffset) { dragOffset = it }
                            },
                            onDragCancel = { scope.settleDrag(dragOffset) { dragOffset = it } }
                        ) { _, delta ->
                            val raw = dragOffset + delta
                            // Pulling past the top is resisted, never blocked.
                            dragOffset = if (raw < 0f) {
                                MorroMotion.rubberBand(raw, dismissAt * 4f)
                            } else {
                                raw
                            }
                        }
                    }
            )

            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}
/**
 * AlarmBuilderSheetContent — the alarm setup, in one scroll.
 *
 * Order is the order you think in: title, the time at display size (the whole card opens the system
 * picker), then the mission list, then one filled action. Selected mission rows take a 35% border
 * and a honey check; nothing glows.
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
    var selectedHour by rememberSaveable { mutableStateOf(initialHour) }
    var selectedMinute by rememberSaveable { mutableStateOf(initialMinute) }

    // ── Mission selection ─────────────────────────────────────────────────────
    var selectedMission by remember { mutableStateOf(MissionPrefs.getMissionType(context)) }

    // ── QR code ───────────────────────────────────────────────────────────────
    var qrCodeValue by remember { mutableStateOf(MissionPrefs.getQrCode(context)) }
    var showQrDialog by rememberSaveable { mutableStateOf(false) }

    // ── Photo items ───────────────────────────────────────────────────────────
    var selectedPhotoItems by remember { mutableStateOf(MissionPrefs.getSelectedPhotoItems(context)) }
    var showPhotoSheet by rememberSaveable { mutableStateOf(false) }

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
                    text = "Set the time and how you want to wake.",
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

        // One row per mission, stacked with a single gap so the list reads as one block.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Voice Commitment (with record button and yellow checkmark active state)
            MorroOptionChip(
                title = "Voice Commitment",
                subtitle = "Record a line in your own voice",
                staggerIndex = 0,
                icon = Icons.Filled.Mic,
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
                subtitle = "Scan a code you keep by the bed",
                staggerIndex = 1,
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
                subtitle = "Morro picks one object from your pool",
                staggerIndex = 2,
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

            // 4. Math puzzle — reserved. Not wired end to end, so it is shown honestly as "Soon".
            MorroOptionChip(
                title = "Math Puzzle",
                subtitle = "Not in this build yet",
                staggerIndex = 3,
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

    // Every change of time lands with a small spring, so the numbers feel set rather than typed.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selectedHour, selectedMinute) {
        // A set time snaps, then settles. No bounce: this happens on every minute change.
        pop.snapTo(0.97f)
        pop.animateTo(1f, MorroMotion.PressRelease)
    }

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
            .padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute),
            style = typo.displayHero.copy(fontSize = 60.sp, lineHeight = 64.sp),
            color = colors.textPrimary,
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
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

        Spacer(modifier = Modifier.height(14.dp))

        // Three common times. A default beats an open question (Parkinson), and three choices are
        // cheaper than a full picker when one of them fits (Hick).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6 to 0, 7 to 0, 8 to 0).forEach { (hour, minute) ->
                val active = selectedHour == hour && selectedMinute == minute
                Text(
                    text = String.format(Locale.getDefault(), "%02d:00", hour),
                    fontSize = 12.sp,
                    color = if (active) colors.honey else colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .morroHairline(
                            radius = 50.dp,
                            color = if (active) colors.borderStrong else colors.borderSubtle
                        )
                        .clickable { onTimeChange(hour, minute) }
                        .heightIn(min = 48.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/**
 * MorroOptionChip — one mission. Selected takes a 35% border, a honey icon and a check; a disabled
 * mission says "Soon" and explains itself in the subtitle instead of pretending to work.
 */
@Composable
private fun MorroOptionChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    staggerIndex: Int = 0,
    showExpanded: Boolean = false,
    enabled: Boolean = true,
    expandedContent: @Composable (() -> Unit)? = null
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = Modifier.fillMaxWidth().staggeredReveal(staggerIndex)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource, enabled, MorroMotion.RowPressedScale)
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
                        tint = if (isSelected) colors.honey
                        else if (enabled) colors.textSecondary else colors.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
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
                        tint = colors.honey,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (enabled && expandedContent != null) {
            AnimatedVisibility(
                visible = showExpanded,
                enter = fadeIn(tween(MorroMotion.ExpanderMillis, easing = MorroMotion.EnterEasing)) +
                    expandVertically(
                        animationSpec = tween(MorroMotion.ExpanderMillis, easing = MorroMotion.EnterEasing),
                        expandFrom = Alignment.Top
                    ),
                exit = fadeOut(tween(MorroMotion.exitMillis(MorroMotion.ExpanderMillis), easing = MorroMotion.ExitEasing)) +
                    shrinkVertically(
                        animationSpec = tween(MorroMotion.exitMillis(MorroMotion.ExpanderMillis), easing = MorroMotion.ExitEasing),
                        shrinkTowards = Alignment.Top
                    )
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    MorroCard(modifier = Modifier.fillMaxWidth()) { cardModifier ->
                        // MorroCard hands its inner modifier back so the card's own padding is applied.
                        // Dropping it put the panel's text flush against the card border.
                        Box(modifier = cardModifier.fillMaxWidth()) {
                            expandedContent()
                        }
                    }
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
                        text = "SAY THIS CLEARLY",
                        style = typo.labelSmall,
                        color = colors.textTertiary,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "\u201CI am waking up at $wakeTimeStr tomorrow to protect my $missionTargetName.\u201D",
                        style = typo.bodyMedium,
                        color = colors.textPrimary,
                        lineHeight = 21.sp,
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
                                tint = colors.honey,
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
                            tint = colors.honey,
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (qrCodeValue.isNotBlank()) {
                "Locked to $qrCodeValue"
            } else {
                "Any QR code or barcode will do."
            },
            style = typo.bodyMedium,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (qrCodeValue.isNotBlank()) "Change the code" else "Lock it to one code",
            style = typo.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .morroHairline(radius = 12.dp, color = colors.borderStrong)
                .clickable { onQrDialogChange(true) }
                // 48dp tall, so the row is a proper target rather than a 47dp near miss.
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = 12.dp)
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
                Text("Save", style = typo.labelLarge.copy(fontWeight = FontWeight.Bold), color = colors.honey)
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Object pool",
                style = typo.bodyMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (selectedPhotoItems.isEmpty()) {
                    "Nothing chosen yet"
                } else {
                    "${selectedPhotoItems.size} of 30 objects chosen"
                },
                style = typo.titleMedium,
                color = colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "Choose",
            style = typo.labelLarge,
            color = colors.textPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .morroHairline(radius = 50.dp, color = colors.borderStrong)
                .clickable { onPhotoSheetChange(true) }
                .padding(horizontal = 16.dp, vertical = 11.dp)
        )
    }

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
 * PhotoTargetSheet — the object pool, as a plain list of choices.
 *
 * Two columns of text tiles, each with a check; presets as quiet outlined chips; one save action.
 * The list is a normal Column inside one scroll container, not a lazy grid nested in a scrolling
 * parent, so the sheet measures and scrolls as one surface.
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

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .clickable(onClick = onDismiss)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(colors.surface)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Drag handle: the line sits inside its own padding, not inside a 4dp box.
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderSubtle)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Object pool",
                        style = typo.headlineLarge,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Morro picks one at random when the alarm rings.",
                        style = typo.bodyMedium,
                        color = colors.textSecondary
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Close",
                        style = typo.labelLarge,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${localSelectedItems.size} of ${PhotoTargetPool.ITEMS.size} selected",
                style = typo.labelLarge,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                item {
                    PoolPreset(
                        label = "Common ten",
                        active = localSelectedItems == PhotoTargetPool.COMMON_10_IDS.toSet(),
                        onClick = { localSelectedItems = PhotoTargetPool.COMMON_10_IDS.toSet() }
                    )
                }
                item {
                    PoolPreset(
                        label = "Everything",
                        active = localSelectedItems.size == PhotoTargetPool.ITEMS.size,
                        onClick = { localSelectedItems = PhotoTargetPool.ITEMS.map { it.id }.toSet() }
                    )
                }
                item {
                    PoolPreset(
                        label = "Bathroom",
                        active = false,
                        onClick = {
                            localSelectedItems = setOf("toothbrush", "sink", "mirror", "towel", "scissors")
                        }
                    )
                }
                item {
                    PoolPreset(
                        label = "Desk",
                        active = false,
                        onClick = {
                            localSelectedItems = setOf(
                                "keyboard", "mouse", "monitor", "laptop",
                                "remote", "headphones", "desk", "pen", "lamp"
                            )
                        }
                    )
                }
                item {
                    PoolPreset(
                        label = "Kitchen",
                        active = false,
                        onClick = { localSelectedItems = setOf("cup", "bottle", "spoon", "fork") }
                    )
                }
                item {
                    PoolPreset(
                        label = "Clear",
                        active = localSelectedItems.isEmpty(),
                        onClick = { localSelectedItems = emptySet() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PhotoTargetPool.ITEMS.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { item ->
                            PoolTile(
                                label = item.displayName,
                                selected = localSelectedItems.contains(item.id),
                                modifier = Modifier.weight(1f),
                                onToggle = {
                                    localSelectedItems = if (localSelectedItems.contains(item.id)) {
                                        localSelectedItems - item.id
                                    } else {
                                        localSelectedItems + item.id
                                    }
                                }
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            MorroPrimaryButton(
                text = if (localSelectedItems.isEmpty()) {
                    "Choose at least one"
                } else {
                    "Save ${localSelectedItems.size} objects"
                },
                onClick = { onSave(localSelectedItems) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 24.dp),
                enabled = localSelectedItems.isNotEmpty()
            )
        }
    }
}

/** One pool chip. Selected is a filled surface plus a stronger hairline, nothing louder. */
@Composable
private fun PoolPreset(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = morroColors()
    Text(
        text = label,
        fontSize = 12.sp,
        color = if (active) colors.textPrimary else colors.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) colors.surfaceElevated else Color.Transparent)
            .morroHairline(
                radius = 50.dp,
                color = if (active) colors.borderActive else colors.borderSubtle
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    )
}

/** One object in the pool: its name on the left, a check on the right. No icon, no badge. */
@Composable
private fun PoolTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val colors = morroColors()
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.surfaceElevated else colors.surfaceCard)
            .morroHairline(
                radius = 12.dp,
                color = if (selected) colors.borderActive else colors.borderSubtle
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) colors.honey else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (selected) colors.honey else colors.borderSubtle,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
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
                        color = if (isRecording) colors.honey else colors.borderSubtle,
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

/** One place that returns the drag offset to rest, however the gesture ended. */
private fun CoroutineScope.settleDrag(from: Float, onValue: (Float) -> Unit) {
    launch {
        animate(
            initialValue = from,
            targetValue = 0f,
            animationSpec = MorroMotion.PressRelease
        ) { value, _ -> onValue(value) }
    }
}