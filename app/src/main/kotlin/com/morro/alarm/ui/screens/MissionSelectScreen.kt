package com.morro.alarm.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroTypography
import com.morro.alarm.ui.theme.MorroTheme
import com.morro.alarm.ui.components.MorroCard
import com.morro.alarm.ui.components.MorroPrimaryButton
import com.morro.alarm.ui.components.AlarmBuilderSheet
import com.morro.alarm.ui.components.AlarmBuilderSheetContent
import com.morro.alarm.mission.MissionType

/**
 * MissionSelectScreen — Bento Template C (2-column vertical).
 *
 * SPEC (per Manifesto + Bento rules):
 * - Template C: 2-column vertical hierarchy, 6dp fixed gaps (NOT 4dp, NOT 8dp)
 * - 5 cards exactly: 1 Hero (armed mission, spans 2 cols) + 3 Stat cards (Voice/QR/Photo) + 1 Quote card
 * - Every cell filled — no empty cells
 * - Wrapped in MorroTheme with warm #FFF8F0 canvas background
 * - NO MaterialTheme slots anywhere
 *
 * @param initialHour initial hour for time picker
 * @param initialMinute initial minute for time picker
 * @param onAlarmBuilder callback when user wants to configure alarm
 * @param onSaveAndArm callback when alarm is saved and armed
 * @param isSheetVisible controls sheet visibility
 * @param onSheetDismiss called when sheet is dismissed
 * @param fabCenter center of triggering FAB in window coordinates
 */
@Composable
fun MissionSelectScreen(
    initialHour: Int = 7,
    initialMinute: Int = 0,
    onAlarmBuilder: () -> Unit = {},
    onSaveAndArm: (hour: Int, minute: Int, missionType: MissionType) -> Unit = { _, _, _ -> },
    isSheetVisible: Boolean = false,
    onSheetDismiss: () -> Unit = {},
    fabCenter: Offset = Offset(0f, 0f)
) {
    val colors = morroColors()
    val typo = morroTypography()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        MorroTheme {
            // ── Bento Grid: Template C, 2-column vertical, 6dp gap ──
            // Using Column + Row instead of LazyVerticalGrid to avoid span API issues
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: Hero card (full width, spans 2 columns)
                BentoHeroCard(
                    missionLabel = "Voice Commitment",
                    missionTime = "7:00 AM",
                    onClick = onAlarmBuilder,
                    modifier = Modifier.fillMaxWidth()
                )

                // Row 2: Voice + QR cards (2 columns)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BentoStatCard(
                        tagLabel = "VOICE",
                        title = "Voice Memo",
                        subtitle = "15s personal commitment",
                        accent = colors.accentTactical,
                        onClick = onAlarmBuilder,
                        modifier = Modifier.weight(1f, fill = true)
                    )
                    BentoStatCard(
                        tagLabel = "QR",
                        title = "QR / Barcode",
                        subtitle = "Scan to dismiss",
                        accent = colors.accentBubbly,
                        onClick = onAlarmBuilder,
                        modifier = Modifier.weight(1f, fill = true)
                    )
                }

                // Row 3: Photo card (full width) - Quote card below spans full width
                BentoStatCard(
                    tagLabel = "PHOTO",
                    title = "Object Hunt",
                    subtitle = "30-item household pool",
                    accent = colors.accentTactical,
                    onClick = onAlarmBuilder,
                    modifier = Modifier.fillMaxWidth()
                )

                // Row 4: Quote card (full width, spans 2 columns)
                BentoQuoteCard(
                    quote = "An alarm that won't shut off until you hear yourself promise what you're doing today.",
                    attribution = "— Morro",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ── + FAB (bottom-right) using MorroPrimaryButton ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            MorroPrimaryButton(
                text = "+",
                onClick = onAlarmBuilder,
                modifier = Modifier.size(56.dp),
                enabled = true
            )
        }

        // ── Alarm Builder Sheet (anchored to FAB center) ──
        AlarmBuilderSheet(
            isVisible = isSheetVisible,
            onDismiss = onSheetDismiss,
            fabCenter = fabCenter
        ) {
            AlarmBuilderSheetContent(
                initialHour = initialHour,
                initialMinute = initialMinute,
                onDismiss = onSheetDismiss,
                onSave = { hour, minute, missionType ->
                    onSaveAndArm(hour, minute, missionType)
                    onSheetDismiss()
                }
            )
        }
    }
}

private enum class BentoCardData { Hero, Voice, Qr, Photo, Quote }

/**
 * BentoHeroCard — Hero card spanning 2 columns.
 * Full-width, subtitle + badge or pill tags to feel "full".
 */
@Composable
private fun BentoHeroCard(
    missionLabel: String,
    missionTime: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = morroColors()
    val typo = morroTypography()

    MorroCard(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable(onClick = onClick),
        content = { contentModifier ->
            Column(
                modifier = contentModifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Tag pill
                Box(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .background(colors.surfaceLow)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = missionLabel.uppercase(),
                        style = typo.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12f.sp
                        ),
                        color = colors.accentTactical
                    )
                }

                // Mission title
                Text(
                    text = "Active Mission",
                    style = typo.headlineLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Mission time
                Text(
                    text = missionTime,
                    style = typo.bodyLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Mission status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircleOutline,
                        contentDescription = "Mission active",
                        tint = colors.accentTactical,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ARMED — RING AT 7:00 AM",
                        style = typo.labelLarge,
                        color = colors.accentTactical,
                        modifier = Modifier.weight(1f, fill = true)
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = colors.textDisabled,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    )
}

/**
 * BentoStatCard — Single mission stat/selection card (1 column).
 * Subtitle + badge or pill tags to feel "full".
 */
@Composable
private fun BentoStatCard(
    tagLabel: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = morroColors()
    val typo = morroTypography()

    MorroCard(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        content = { contentModifier ->
            Column(
                modifier = contentModifier
                    .padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Tag label
                Text(
                    text = tagLabel,
                    style = typo.labelLarge.copy(
                        color = accent
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Title
                Text(
                    text = title,
                    style = typo.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Subtitle
                Text(
                    text = subtitle,
                    style = typo.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Pill tag (accent color)
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(24.dp)
                        .background(accent.copy(alpha = 0.1f))
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SELECT",
                        style = typo.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = accent,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    )
}

/**
 * BentoQuoteCard — Quote card spanning 2 columns.
 * SurfaceVariant surface with accent text.
 */
@Composable
private fun BentoQuoteCard(
    quote: String,
    attribution: String,
    modifier: Modifier = Modifier
) {
    val colors = morroColors()
    val typo = morroTypography()

    MorroCard(
        modifier = modifier.fillMaxWidth(),
        content = { contentModifier ->
            Column(
                modifier = contentModifier
                    .padding(24.dp)
                    .background(colors.surfaceVariant)
                    .clip(RoundedCornerShape(16.dp)),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = quote,
                    style = typo.bodyLarge.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = attribution,
                    style = typo.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.accentTactical
                )
            }
        }
    )
}