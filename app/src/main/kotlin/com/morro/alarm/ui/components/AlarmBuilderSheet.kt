package com.morro.alarm.ui.components

import com.morro.alarm.AlarmVolumeGuard
import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.MemoPlayer
import com.morro.alarm.MemoRecorder
import com.morro.alarm.MemoStorage
import com.morro.alarm.mission.MissionPrefs
import com.morro.alarm.mission.MissionType
import com.morro.alarm.mission.PhotoTargetPool
import com.morro.alarm.puzzle.sudoku.SudokuMode
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.motion.staggeredReveal
import com.morro.alarm.ui.puzzle.CognitiveChallenge
import com.morro.alarm.ui.puzzle.CognitiveDifficulty
import com.morro.alarm.ui.puzzle.CognitivePrefs
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroGlassBorder
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

// ─── Voice Commitment States ─────────────────────────────────────────────────
enum class VoiceState {
    IDLE,
    RECORDING,
    RECORDED_READY
}

private enum class MissionGroup {
    VOICE, QR, PHOTO
}

private fun MissionType.toGroup(): MissionGroup = when (this) {
    MissionType.VOICE_COMMITMENT, MissionType.VOICE_MEMO -> MissionGroup.VOICE
    MissionType.QR_CODE, MissionType.QR_BARCODE -> MissionGroup.QR
    MissionType.PHOTO_MISSION, MissionType.PHOTO_OBJECT -> MissionGroup.PHOTO
}

/**
 * AlarmBuilderSheet — bottom sheet housing wake-up time and mission setup.
 */
@Composable
fun AlarmBuilderSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = morroColors()
    val shapes = morroShapes()

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

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val dismissAt = with(LocalDensity.current) { 120.dp.toPx() }

    BackHandler(enabled = isVisible) {
        onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
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
                .background(colors.surface.copy(alpha = 0.90f))
                .morroGlassBorder(radius = 24.dp, topAlpha = 0.24f, bottomAlpha = 0.04f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
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
 * AlarmBuilderSheetContent — streamlined, bento-grouped alarm & mission setup:
 *  1. Header with animated live countdown ("Rings in 7h 15m")
 *  2. Inline snapping dual-wheel time picker + quick time presets (no jarring system popup required)
 *  3. 3-tile bento mission bar + morphing detail card
 *  4. Pinned bottom Save & arm button
 */
@Composable
fun AlarmBuilderSheetContent(
    onSave: (hour: Int, minute: Int, missionType: MissionType, qrCode: String, photoItems: Set<String>) -> Unit,
    onDismiss: () -> Unit,
    initialHour: Int = 7,
    initialMinute: Int = 0
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reducedMotion = rememberReducedMotion()

    // ── Time state ────────────────────────────────────────────────────────────
    var selectedHour by rememberSaveable { mutableStateOf(initialHour) }
    var selectedMinute by rememberSaveable { mutableStateOf(initialMinute) }

    // ── Mission selection ─────────────────────────────────────────────────────
    var selectedMission by remember { mutableStateOf(MissionPrefs.getMissionType(context)) }
    val activeGroup = selectedMission.toGroup()

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

    val finishRecording = {
        if (voiceState == VoiceState.RECORDING) {
            voiceState = VoiceState.RECORDED_READY
            MemoRecorder.stopRecording(context)
            hasSaved.value = MemoStorage.saveMemo(context)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            recordingCountdown = 15
            hasSaved.value = false
            voiceState = VoiceState.RECORDING
            MemoRecorder.startRecording(
                context,
                onError = { reason ->
                    Toast.makeText(context, "Recording error: $reason", Toast.LENGTH_SHORT).show()
                    voiceState = VoiceState.IDLE
                },
                onMaxDurationReached = {
                    finishRecording()
                }
            )
        } else {
            Toast.makeText(context, "Microphone permission is required to record your voice promise.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(voiceState) {
        if (voiceState == VoiceState.RECORDING) {
            recordingCountdown = 15
            val startRealtime = SystemClock.elapsedRealtime()
            while (voiceState == VoiceState.RECORDING) {
                val elapsed = SystemClock.elapsedRealtime() - startRealtime
                val remainingMs = (15_000L - elapsed).coerceAtLeast(0L)
                recordingCountdown = ((remainingMs + 999L) / 1000L).toInt().coerceIn(0, 15)
                if (remainingMs <= 0L) {
                    finishRecording()
                    break
                }
                delay(100L)
            }
        }
    }

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
            .getString("onboarding_target", null)
            ?.trim()
            ?.ifBlank { null }
            ?: "mission"
    }

    val countdownText = remember(selectedHour, selectedMinute) {
        formatTimeUntil(selectedHour, selectedMinute)
    }

    DisposableEffect(Unit) {
        onDispose {
            MemoPlayer.stopPlayback()
            MemoRecorder.cancelRecording(context)
        }
    }

    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top Header (pinned above scroll) ─────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Wake-up",
                    style = typo.headlineLarge,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedContent(
                    targetState = countdownText,
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (fadeIn(tween(180, easing = MorroMotion.EnterEasing)) +
                                slideInVertically(tween(180, easing = MorroMotion.EnterEasing)) { it / 3 })
                                .togetherWith(
                                    fadeOut(tween(120, easing = MorroMotion.ExitEasing)) +
                                        slideOutVertically(tween(120, easing = MorroMotion.ExitEasing)) { -it / 3 }
                                )
                        }
                    },
                    label = "countdown"
                ) { text ->
                    Text(
                        text = text,
                        style = typo.bodyMedium,
                        color = colors.honey
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ── Scrollable Body ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // 1. Tactile Inline Time Wheel Bento Card
            TimeWheelCard(
                selectedHour = selectedHour,
                selectedMinute = selectedMinute,
                onTimeChange = { hour, minute ->
                    selectedHour = hour
                    selectedMinute = minute
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Mission Bento Header
            Text(
                text = "Mission",
                style = typo.labelLarge,
                color = colors.textTertiary,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // 3. 3-Tile Compact Bento Mission Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredReveal(0),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MissionBentoTile(
                    label = "Voice",
                    icon = Icons.Filled.Mic,
                    selected = activeGroup == MissionGroup.VOICE,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedMission = MissionType.VOICE_COMMITMENT
                    },
                    modifier = Modifier.weight(1f)
                )
                MissionBentoTile(
                    label = "QR scan",
                    icon = Icons.Filled.QrCodeScanner,
                    selected = activeGroup == MissionGroup.QR,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedMission = MissionType.QR_CODE
                    },
                    modifier = Modifier.weight(1f)
                )
                MissionBentoTile(
                    label = "Photo",
                    icon = Icons.Filled.PhotoCamera,
                    selected = activeGroup == MissionGroup.PHOTO,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedMission = MissionType.PHOTO_MISSION
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Unified Morphing Glass Detail Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredReveal(1)
                    .clip(shapes.Card)
                    .background(colors.surfaceCard.copy(alpha = 0.80f))
                    .morroGlassBorder(radius = 16.dp, topAlpha = 0.18f, bottomAlpha = 0.04f)
                    .animateContentSize(
                        animationSpec = if (reducedMotion) {
                            tween(0)
                        } else {
                            spring(dampingRatio = 1f, stiffness = 322f)
                        }
                    )
                    .padding(18.dp)
            ) {
                AnimatedContent(
                    targetState = activeGroup,
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (fadeIn(tween(MorroMotion.EnterMillis, easing = MorroMotion.EnterEasing)) +
                                slideInVertically(tween(MorroMotion.EnterMillis, easing = MorroMotion.EnterEasing)) { 18 })
                                .togetherWith(
                                    fadeOut(tween(MorroMotion.exitMillis(MorroMotion.EnterMillis), easing = MorroMotion.ExitEasing))
                                )
                        }
                    },
                    label = "missionDetail"
                ) { group ->
                    when (group) {
                        MissionGroup.VOICE -> VoiceExpandedContent(
                            voiceState = voiceState,
                            recordingCountdown = recordingCountdown,
                            isPlayingPreview = isPlayingPreview,
                            hasSaved = hasSaved,
                            wakeTimeStr = wakeTimeStr,
                            missionTargetName = missionTargetName,
                            onRecord = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onStop = finishRecording,
                            onPreview = {
                                val memoPath = MemoStorage.getLatestMemoPath(context)
                                if (memoPath == null) {
                                    Toast.makeText(context, "No saved voice promise yet.", Toast.LENGTH_SHORT).show()
                                } else {
                                    isPlayingPreview = true
                                    MemoPlayer.startPlayback(
                                        context = context,
                                        filePath = memoPath,
                                        onCompletion = { isPlayingPreview = false },
                                        onError = { isPlayingPreview = false }
                                    )
                                }
                            },
                            onRerecord = {
                                MemoPlayer.stopPlayback()
                                isPlayingPreview = false
                                hasSaved.value = false
                                voiceState = VoiceState.IDLE
                            }
                        )

                        MissionGroup.QR -> QrExpandedContent(
                            qrCodeValue = qrCodeValue,
                            showQrDialog = showQrDialog,
                            onQrDialogChange = { showQrDialog = it },
                            onQrCodeChange = { qrCodeValue = it }
                        )

                        MissionGroup.PHOTO -> PhotoExpandedContent(
                            selectedPhotoItems = selectedPhotoItems,
                            showPhotoSheet = showPhotoSheet,
                            onPhotoSheetChange = { showPhotoSheet = it },
                            onPhotoItemsChange = { selectedPhotoItems = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Pinned Bottom Action Bar ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
        ) {
            HorizontalDivider(thickness = 1.dp, color = colors.borderSubtle)
            val formattedTime = remember(selectedHour, selectedMinute) {
                String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
            }
            MorroPrimaryButton(
                text = "Save and arm • $formattedTime",
                onClick = {
                    val needsVoiceRecording = selectedMission == MissionType.VOICE_COMMITMENT ||
                        selectedMission == MissionType.VOICE_MEMO

                    if (needsVoiceRecording && !MemoStorage.hasSavedMemo(context)) {
                        Toast.makeText(
                            context,
                            "Please record your 15-second voice promise first",
                            Toast.LENGTH_LONG
                        ).show()
                        selectedMission = MissionType.VOICE_COMMITMENT
                        voiceState = VoiceState.IDLE
                        return@MorroPrimaryButton
                    }

                    val volumeSnapshot = AlarmVolumeGuard.readAlarmVolume(context)
                    if (volumeSnapshot.tooQuiet) {
                        Toast.makeText(
                            context,
                            "Alarm volume is low (${volumeSnapshot.volume}/${volumeSnapshot.max}). Turn it up so you don't sleep through it.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    onSave(selectedHour, selectedMinute, selectedMission, qrCodeValue.trim(), selectedPhotoItems)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    }
}

/**
 * TimeWheelCard — inline dual snapping drum picker + quick time presets.
 *
 * Keeps time selection inside Morro's own dark monochrome aesthetic with haptic ticks per step,
 * draw-phase depth scaling, and smooth wheel glides when tapping a quick preset.
 * Tapping the center time readout also opens the system picker as an optional shortcut.
 */
@Composable
private fun TimeWheelCard(
    selectedHour: Int,
    selectedMinute: Int,
    onTimeChange: (Int, Int) -> Unit
) {
    val colors = morroColors()
    val shapes = morroShapes()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()

    // Gentle breathing opacity on the center separator colon
    val colonTransition = rememberInfiniteTransition(label = "colonPulse")
    val colonAlpha by colonTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colonAlpha"
    )

    // Use a large centered virtual range so 23->00 and 59->00 wrap infinitely without hitting stops
    val hourBase = 12_000
    val minuteBase = 30_000
    val hourState = rememberLazyListState(initialFirstVisibleItemIndex = hourBase + selectedHour)
    val minuteState = rememberLazyListState(initialFirstVisibleItemIndex = minuteBase + selectedMinute)

    // Track wheel snaps and emit haptic ticks when user scrubs
    var syncingFromPreset by remember { mutableStateOf(false) }

    LaunchedEffect(hourState) {
        snapshotFlow {
            val layout = hourState.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index
        }
            .mapNotNull { it }
            .distinctUntilChanged()
            .drop(1)
            .collect { centeredIdx ->
                val newHour = ((centeredIdx % 24) + 24) % 24
                if (newHour != selectedHour && !syncingFromPreset) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTimeChange(newHour, selectedMinute)
                }
            }
    }

    LaunchedEffect(minuteState) {
        snapshotFlow {
            val layout = minuteState.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index
        }
            .mapNotNull { it }
            .distinctUntilChanged()
            .drop(1)
            .collect { centeredIdx ->
                val newMinute = ((centeredIdx % 60) + 60) % 60
                if (newMinute != selectedMinute && !syncingFromPreset) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTimeChange(selectedHour, newMinute)
                }
            }
    }

    fun glideWheelsTo(targetHour: Int, targetMinute: Int) {
        onTimeChange(targetHour, targetMinute)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            syncingFromPreset = true
            try {
                // Find nearest matching wrap index so the wheel takes the shortest path
                val currHIdx = hourState.firstVisibleItemIndex
                val currHMod = ((currHIdx % 24) + 24) % 24
                var diffH = targetHour - currHMod
                if (diffH > 12) diffH -= 24 else if (diffH < -12) diffH += 24
                val destH = currHIdx + diffH

                val currMIdx = minuteState.firstVisibleItemIndex
                val currMMod = ((currMIdx % 60) + 60) % 60
                var diffM = targetMinute - currMMod
                if (diffM > 30) diffM -= 60 else if (diffM < -30) diffM += 60
                val destM = currMIdx + diffM

                launch {
                    if (reducedMotion) hourState.scrollToItem(destH)
                    else hourState.animateScrollToItem(destH)
                }
                launch {
                    if (reducedMotion) minuteState.scrollToItem(destM)
                    else minuteState.animateScrollToItem(destM)
                }
            } finally {
                delay(280L)
                syncingFromPreset = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.Card)
            .background(colors.surfaceCard.copy(alpha = 0.80f))
            .morroGlassBorder(radius = 16.dp, topAlpha = 0.22f, bottomAlpha = 0.04f)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dual Drum Wheel Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp),
            contentAlignment = Alignment.Center
        ) {
            // Centered Smoked Glass Selection Lens
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(50.dp)
                    .clip(shapes.Button)
                    .background(colors.surfaceElevated.copy(alpha = 0.86f))
                    .morroGlassBorder(radius = 12.dp, topAlpha = 0.30f, bottomAlpha = 0.08f, tint = colors.honey)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        TimePickerDialog(
                            context,
                            { _, h, m -> glideWheelsTo(h, m) },
                            selectedHour,
                            selectedMinute,
                            true
                        ).show()
                    }
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelDrum(
                    state = hourState,
                    modulo = 24,
                    totalItems = 24_000,
                    onTapItem = { idx ->
                        val targetH = ((idx % 24) + 24) % 24
                        onTimeChange(targetH, selectedMinute)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            syncingFromPreset = true
                            if (reducedMotion) hourState.scrollToItem(idx)
                            else hourState.animateScrollToItem(idx)
                            delay(220L)
                            syncingFromPreset = false
                        }
                    },
                    modifier = Modifier.width(88.dp)
                )

                Text(
                    text = ":",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 36.sp,
                    color = colors.honey,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .graphicsLayer {
                            alpha = if (reducedMotion) 0.9f else colonAlpha
                        }
                )

                WheelDrum(
                    state = minuteState,
                    modulo = 60,
                    totalItems = 60_000,
                    onTapItem = { idx ->
                        val targetM = ((idx % 60) + 60) % 60
                        onTimeChange(selectedHour, targetM)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {
                            syncingFromPreset = true
                            if (reducedMotion) minuteState.scrollToItem(idx)
                            else minuteState.animateScrollToItem(idx)
                            delay(220L)
                            syncingFromPreset = false
                        }
                    },
                    modifier = Modifier.width(88.dp)
                )
            }

            // Soft top & bottom fade masks (single neutral surfaceCard fade, matching Morro rules)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(colors.surfaceCard, colors.surfaceCard.copy(alpha = 0f))
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(colors.surfaceCard.copy(alpha = 0f), colors.surfaceCard)
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Preset Chips (1-tap glide to popular wake-up times)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(6 to 0, 6 to 30, 7 to 0, 8 to 0).forEach { (h, m) ->
                val active = selectedHour == h && selectedMinute == m
                QuickTimeChip(
                    label = String.format(Locale.getDefault(), "%02d:%02d", h, m),
                    active = active,
                    onClick = { glideWheelsTo(h, m) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WheelDrum(
    state: androidx.compose.foundation.lazy.LazyListState,
    modulo: Int,
    totalItems: Int,
    onTapItem: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = morroColors()
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = state)

    LazyColumn(
        state = state,
        flingBehavior = snapBehavior,
        contentPadding = PaddingValues(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(144.dp)
    ) {
        items(
            count = totalItems,
            key = { it }
        ) { index ->
            val displayValue = ((index % modulo) + modulo) % modulo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .graphicsLayer {
                        val layout = state.layoutInfo
                        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                        val itemInfo = layout.visibleItemsInfo.firstOrNull { it.index == index }
                        if (itemInfo != null) {
                            val itemCenter = itemInfo.offset + itemInfo.size / 2f
                            val signedDist = itemCenter - viewportCenter
                            val maxDist = itemInfo.size * 1.15f
                            val signedFraction = (signedDist / maxDist).coerceIn(-1f, 1f)
                            val fraction = abs(signedFraction)
                            val s = 1f - 0.22f * fraction
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.72f * fraction
                            rotationX = -signedFraction * 26f
                            cameraDistance = 12f * density
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTapItem(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%02d", displayValue),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 38.sp,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QuickTimeChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = morroColors()
    val shapes = morroShapes()
    val interactionSource = remember { MutableInteractionSource() }
    val textColor by animateColorAsState(
        targetValue = if (active) colors.honey else colors.textSecondary,
        animationSpec = tween(180),
        label = "chipTextColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) colors.borderActive else colors.borderSubtle,
        animationSpec = tween(180),
        label = "chipBorderColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (active) colors.surfaceElevated else Color.Transparent,
        animationSpec = tween(180),
        label = "chipBgColor"
    )

    Box(
        modifier = modifier
            .pressScale(interactionSource, pressedScale = 0.96f)
            .clip(shapes.Chip)
            .background(bgColor)
            .morroHairline(radius = 12.dp, color = borderColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
    }
}

/**
 * MissionBentoTile — compact tile inside the 3-column bento bar.
 */
@Composable
private fun MissionBentoTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = morroColors()
    val shapes = morroShapes()
    val typo = morroTypography()
    val interactionSource = remember { MutableInteractionSource() }

    val bgColor by animateColorAsState(
        targetValue = if (selected) colors.surfaceElevated else colors.surfaceCard,
        animationSpec = tween(180),
        label = "tileBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.borderActive else colors.borderSubtle,
        animationSpec = tween(180),
        label = "tileBorder"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) colors.honey else colors.textSecondary,
        animationSpec = tween(180),
        label = "tileIcon"
    )

    Box(
        modifier = modifier
            .pressScale(interactionSource, pressedScale = 0.96f, releaseSpec = MorroSpring.FloatSpringPlayful)
            .clip(shapes.Card)
            .background(bgColor.copy(alpha = if (selected) 0.88f else 0.72f))
            .morroGlassBorder(
                radius = 16.dp,
                topAlpha = if (selected) 0.34f else 0.16f,
                bottomAlpha = if (selected) 0.12f else 0.04f,
                tint = if (selected) colors.honey else colors.textPrimary
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .heightIn(min = 76.dp)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        // Selected corner indicator
        androidx.compose.animation.AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.5f, animationSpec = tween(160, easing = MorroMotion.EnterEasing)),
            exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.5f),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = colors.honey,
                modifier = Modifier.size(14.dp)
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = typo.labelLarge,
                color = if (selected) colors.textPrimary else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * VoiceExpandedContent — simplified recording/preview & cognitive puzzle configuration.
 */
@Composable
private fun VoiceExpandedContent(
    voiceState: VoiceState,
    recordingCountdown: Int,
    isPlayingPreview: Boolean,
    hasSaved: MutableState<Boolean>,
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
    val haptics = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        when (voiceState) {
            VoiceState.IDLE -> {
                if (!hasSaved.value) {
                    Text(
                        text = "Voice promise",
                        style = typo.titleMedium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\u201CI am waking up at $wakeTimeStr tomorrow to protect my $missionTargetName.\u201D",
                        style = typo.bodyMedium,
                        color = colors.textSecondary,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Outlined elevated record button so bottom "Save and arm" remains the sole filled CTA
                    val recordInteraction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .pressScale(recordInteraction, pressedScale = 0.97f)
                            .clip(shapes.Button)
                            .background(colors.surfaceElevated)
                            .morroHairline(radius = 12.dp, color = colors.borderStrong)
                            .clickable(
                                interactionSource = recordInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = onRecord
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = colors.honey,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Record 15s promise",
                            style = typo.titleMedium,
                            color = colors.textPrimary
                        )
                    }
                } else {
                    VoiceReadyRow(
                        isPlayingPreview = isPlayingPreview,
                        onPreview = onPreview,
                        onRerecord = onRerecord
                    )
                }
            }

            VoiceState.RECORDING -> {
                val smoothProgress by animateFloatAsState(
                    targetValue = (recordingCountdown / 15f).coerceIn(0f, 1f),
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    label = "recProgress"
                )
                AnimatedWaveform(
                    isRecording = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.surfaceHigh)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(smoothProgress)
                            .background(colors.honey)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = String.format(Locale.getDefault(), "00:%02d", recordingCountdown),
                    style = typo.headlineMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(shapes.Button)
                        .background(colors.surfaceElevated)
                        .morroHairline(radius = 12.dp, color = colors.borderStrong)
                        .clickable(onClick = onStop),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        tint = colors.honey,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stop and lock",
                        style = typo.titleMedium,
                        color = colors.textPrimary
                    )
                }
            }

            VoiceState.RECORDED_READY -> {
                if (!hasSaved.value && MemoStorage.hasTempMemo(context)) {
                    LaunchedEffect(Unit) {
                        hasSaved.value = MemoStorage.saveMemo(context)
                    }
                }
                VoiceReadyRow(
                    isPlayingPreview = isPlayingPreview,
                    onPreview = onPreview,
                    onRerecord = onRerecord
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(thickness = 1.dp, color = colors.borderSubtle)
        Spacer(modifier = Modifier.height(14.dp))

        // ── Animated Cognitive Challenge & Difficulty Selector ────────────────
        var cognitiveChallenge by remember { mutableStateOf(CognitivePrefs.getChallenge(context)) }
        var cognitiveDifficulty by remember { mutableStateOf(CognitivePrefs.getDifficulty(context)) }
        var sudokuMode by remember { mutableStateOf(CognitivePrefs.getSudokuMode(context)) }
        val reducedMotion = rememberReducedMotion()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Opening puzzle",
                style = typo.titleMedium,
                color = colors.textPrimary
            )
            AnimatedContent(
                targetState = Triple(cognitiveChallenge, cognitiveDifficulty, sudokuMode),
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (fadeIn(tween(180)) + slideInVertically { it / 2 })
                            .togetherWith(fadeOut(tween(120)))
                    }
                },
                label = "puzzleSummary"
            ) { (chal, diff, sMode) ->
                Text(
                    text = when (chal) {
                        CognitiveChallenge.MATH ->
                            if (diff == CognitiveDifficulty.STANDARD) "Quick sums & tables" else "2-digit × & 3-digit sums"
                        CognitiveChallenge.CHESS ->
                            if (diff == CognitiveDifficulty.STANDARD) "Mate in 1" else "Mate in 2"
                        CognitiveChallenge.SUDOKU -> sMode.detail
                    },
                    style = typo.bodyMedium,
                    fontSize = 12.sp,
                    color = colors.honey
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sliding Segmented Bar: Math | Chess | Sudoku
        SlidingSegmentedBar(
            items = CognitiveChallenge.values().toList(),
            selectedIndex = CognitiveChallenge.values().indexOf(cognitiveChallenge),
            labelOf = { it.label },
            accentActive = true,
            reducedMotion = reducedMotion,
            onSelect = { _, challenge ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                cognitiveChallenge = challenge
                CognitivePrefs.setChallenge(context, challenge)
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Sliding Segmented Bar: Easy | Medium | Hard (for Sudoku) or Easy | Hard (for Math & Chess)
        if (cognitiveChallenge == CognitiveChallenge.SUDOKU) {
            SlidingSegmentedBar(
                items = SudokuMode.values().toList(),
                selectedIndex = SudokuMode.values().indexOf(sudokuMode),
                labelOf = { it.label },
                accentActive = false,
                reducedMotion = reducedMotion,
                onSelect = { _, mode ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    sudokuMode = mode
                    CognitivePrefs.setSudokuMode(context, mode)
                }
            )
        } else {
            SlidingSegmentedBar(
                items = CognitiveDifficulty.values().toList(),
                selectedIndex = CognitiveDifficulty.values().indexOf(cognitiveDifficulty),
                labelOf = { diff ->
                    if (diff == CognitiveDifficulty.STANDARD) "Easy" else "Hard"
                },
                accentActive = false,
                reducedMotion = reducedMotion,
                onSelect = { _, diff ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    cognitiveDifficulty = diff
                    CognitivePrefs.setDifficulty(context, diff)
                }
            )
        }
    }
}

/**
 * Apple-style sliding segmented control with spring-driven elevated glass pill.
 */
@Composable
private fun <T> SlidingSegmentedBar(
    items: List<T>,
    selectedIndex: Int,
    labelOf: (T) -> String,
    accentActive: Boolean,
    reducedMotion: Boolean,
    onSelect: (Int, T) -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()
    val safeIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shapes.Chip)
            .background(colors.canvas.copy(alpha = 0.50f))
            .morroHairline(radius = 12.dp, color = colors.borderSubtle)
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / items.size.coerceAtLeast(1)
        val targetOffset = segmentWidth * safeIndex
        val indicatorOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = if (reducedMotion) {
                tween(0)
            } else {
                spring(dampingRatio = 0.78f, stiffness = 420f)
            },
            label = "segmentSlide"
        )

        // Sliding elevated glass pill
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(9.dp))
                .background(if (accentActive) colors.surfaceElevated else colors.surfaceHigh)
                .morroGlassBorder(
                    radius = 9.dp,
                    topAlpha = if (accentActive) 0.34f else 0.24f,
                    bottomAlpha = 0.06f,
                    tint = if (accentActive) colors.honey else colors.textPrimary
                )
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { idx, item ->
                val active = idx == safeIndex
                val interactionSource = remember { MutableInteractionSource() }
                val textColor by animateColorAsState(
                    targetValue = when {
                        active && accentActive -> colors.honey
                        active -> colors.textPrimary
                        else -> colors.textSecondary
                    },
                    animationSpec = tween(160),
                    label = "segText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressScale(interactionSource, pressedScale = 0.96f)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.RadioButton
                        ) { onSelect(idx, item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelOf(item),
                        style = if (accentActive) typo.labelLarge else typo.bodyMedium.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                        color = textColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceReadyRow(
    isPlayingPreview: Boolean,
    onPreview: () -> Unit,
    onRerecord: () -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                    style = typo.titleMedium,
                    color = colors.textPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPreview,
                enabled = !isPlayingPreview,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = shapes.Button,
                border = BorderStroke(1.dp, colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = colors.honey,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPlayingPreview) "Playing" else "Play back",
                    style = typo.labelLarge,
                    color = colors.textPrimary
                )
            }
            OutlinedButton(
                onClick = onRerecord,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = shapes.Button,
                border = BorderStroke(1.dp, colors.borderSubtle),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary)
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
                    style = typo.labelLarge,
                    color = colors.textSecondary
                )
            }
        }
    }
}

/**
 * QrExpandedContent — Barcode registration for QR mission.
 */
@Composable
private fun QrExpandedContent(
    qrCodeValue: String,
    showQrDialog: Boolean,
    onQrDialogChange: (Boolean) -> Unit,
    onQrCodeChange: (String) -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Scan to silence",
                style = typo.titleMedium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (qrCodeValue.isNotBlank()) "Locked to $qrCodeValue" else "Any QR code or barcode will do.",
                style = typo.bodyMedium,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .pressScale(interactionSource, pressedScale = 0.96f)
                .clip(shapes.Chip)
                .background(colors.surfaceElevated)
                .morroHairline(radius = 12.dp, color = colors.borderStrong)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onQrDialogChange(true) }
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (qrCodeValue.isNotBlank()) "Change" else "Set code",
                style = typo.labelLarge,
                color = colors.textPrimary
            )
        }
    }

    if (showQrDialog) {
        QrCodeDialog(
            qrCodeValue = qrCodeValue,
            onDismiss = { onQrDialogChange(false) },
            onSave = { newCode ->
                onQrCodeChange(newCode.trim())
                onQrDialogChange(false)
            }
        )
    }
}

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
            Text("Specific code", style = typo.headlineMedium, color = colors.textPrimary)
        },
        text = {
            Column {
                Text(
                    "Set a custom barcode or QR payload to require at alarm time, or leave blank to allow any code.",
                    style = typo.bodyMedium,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = tempCode,
                    onValueChange = { tempCode = it },
                    singleLine = true,
                    placeholder = { Text("e.g. 012345678905", style = typo.bodyMedium, color = colors.textDisabled) },
                    label = { Text("Barcode or QR payload", style = typo.bodyMedium, color = colors.textSecondary) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(tempCode) }) {
                Text("Save", style = typo.labelLarge.copy(fontWeight = FontWeight.Medium), color = colors.honey)
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
 * PhotoExpandedContent — Object pool summary + picker trigger.
 */
@Composable
private fun PhotoExpandedContent(
    selectedPhotoItems: Set<String>,
    showPhotoSheet: Boolean,
    onPhotoSheetChange: (Boolean) -> Unit,
    onPhotoItemsChange: (Set<String>) -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Object pool",
                style = typo.titleMedium,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (selectedPhotoItems.isEmpty()) {
                    "Nothing chosen yet"
                } else {
                    "${selectedPhotoItems.size} of ${PhotoTargetPool.ITEMS.size} objects chosen"
                },
                style = typo.bodyMedium,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .pressScale(interactionSource, pressedScale = 0.96f)
                .clip(shapes.Chip)
                .background(colors.surfaceElevated)
                .morroHairline(radius = 12.dp, color = colors.borderStrong)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onPhotoSheetChange(true) }
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Choose",
                style = typo.labelLarge,
                color = colors.textPrimary
            )
        }
    }

    if (showPhotoSheet) {
        PhotoTargetSheet(
            selectedPhotoItems = selectedPhotoItems,
            onDismiss = { onPhotoSheetChange(false) },
            onSave = { newItems ->
                onPhotoItemsChange(newItems)
                onPhotoSheetChange(false)
            }
        )
    }
}

@Composable
private fun PhotoTargetSheet(
    selectedPhotoItems: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()
    var localSelectedItems by remember { mutableStateOf(selectedPhotoItems) }

    BackHandler(onBack = onDismiss)

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
                .clip(shapes.Sheet)
                .background(colors.surface)
                .navigationBarsPadding()
                .imePadding()
        ) {
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
                    val bathroomItems = setOf("toothbrush", "sink", "mirror", "towel", "scissors")
                    PoolPreset(
                        label = "Bathroom",
                        active = localSelectedItems == bathroomItems,
                        onClick = { localSelectedItems = bathroomItems }
                    )
                }
                item {
                    val deskItems = setOf(
                        "keyboard", "mouse", "monitor", "laptop",
                        "remote", "headphones", "desk", "pen", "lamp"
                    )
                    PoolPreset(
                        label = "Desk",
                        active = localSelectedItems == deskItems,
                        onClick = { localSelectedItems = deskItems }
                    )
                }
                item {
                    val kitchenItems = setOf("cup", "bottle", "spoon", "fork")
                    PoolPreset(
                        label = "Kitchen",
                        active = localSelectedItems == kitchenItems,
                        onClick = { localSelectedItems = kitchenItems }
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

@Composable
private fun PoolPreset(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = morroColors()
    val shapes = morroShapes()
    Text(
        text = label,
        fontSize = 12.sp,
        color = if (active) colors.textPrimary else colors.textSecondary,
        modifier = Modifier
            .clip(shapes.Chip)
            .background(if (active) colors.surfaceElevated else Color.Transparent)
            .morroHairline(
                radius = 12.dp,
                color = if (active) colors.borderActive else colors.borderSubtle
            )
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 14.dp)
    )
}

@Composable
private fun PoolTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val colors = morroColors()
    val shapes = morroShapes()
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(shapes.Chip)
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
                    tint = colors.canvas,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
private fun AnimatedWaveform(
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = morroColors()
    val phases = remember { List(12) { it * 0.15f } }
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val heights = phases.map { phase ->
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(offsetMillis = (phase * 1000).toInt())
            ),
            label = "bar$phase"
        )
    }

    Row(
        modifier = modifier
            .height(52.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { animState ->
            val heightFraction by animState
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height((44 * heightFraction).dp)
                    .background(
                        color = if (isRecording) colors.honey else colors.borderSubtle,
                        shape = RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

/** Formats duration until the next ring of [hour]:[minute] cleanly. */
private fun formatTimeUntil(hour: Int, minute: Int): String {
    val now = Calendar.getInstance()
    val target = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
    }
    val diffMins = ((target.timeInMillis - now.timeInMillis) / 60_000L).toInt().coerceAtLeast(1)
    val h = diffMins / 60
    val m = diffMins % 60
    return when {
        h == 0 -> "Rings in $m min"
        m == 0 -> "Rings in $h hr"
        else -> "Rings in ${h}h ${m}m"
    }
}

private fun CoroutineScope.settleDrag(from: Float, onValue: (Float) -> Unit) {
    launch {
        animate(
            initialValue = from,
            targetValue = 0f,
            animationSpec = MorroMotion.PressRelease
        ) { value, _ -> onValue(value) }
    }
}
