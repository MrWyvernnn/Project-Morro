package com.morro.alarm.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.components.MorroGhostMascot
import com.morro.alarm.ui.components.MorroPrimaryButton
import com.morro.alarm.ui.components.MorroSky
import com.morro.alarm.ui.components.MorroSkyScrim
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.motion.staggeredReveal
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography

/**
 * One plan. Names and prices are sample layout copy until Play Billing lands in Slice 4.
 *
 * Internal rather than private so `PaywallPlansTest` holds the offer to its shape
 * (three plans, Free first, at most one highlighted) without starting a device.
 */
internal data class Plan(
    val name: String,
    val price: String,
    val period: String,
    val blurb: String,
    val popular: Boolean = false,
)

internal val PaywallPlans: List<Plan> = listOf(
    Plan(
        name = "Free",
        price = "$0",
        period = "forever",
        blurb = "One alarm, your voice promise, and Math, Chess & Sudoku gates."
    ),
    Plan(
        name = "Plus",
        price = "$2.99",
        period = "per month",
        blurb = "Adds Photo hunt, QR room scan, and 3-min Wake-up check.",
        popular = true
    ),
    Plan(
        name = "Pro",
        price = "$19.99",
        period = "per year",
        blurb = "Every gate and safety check unlocked · equivalent to $1.67/mo."
    )
)

private data class BentoCapability(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val paidOnly: Boolean
)

private val BentoCapabilities = listOf(
    BentoCapability(
        title = "Voice promise",
        detail = "Your own voice plays before the alarm ends.",
        icon = Icons.Filled.Mic,
        paidOnly = false
    ),
    BentoCapability(
        title = "Mind gates",
        detail = "Math, Chess mates, and Sudoku grids.",
        icon = Icons.Filled.GridView,
        paidOnly = false
    ),
    BentoCapability(
        title = "Room missions",
        detail = "Photo object hunt and QR code scan.",
        icon = Icons.Filled.PhotoCamera,
        paidOnly = true
    ),
    BentoCapability(
        title = "Wake-up check",
        detail = "Quiet follow-up so you never drift back.",
        icon = Icons.Filled.NotificationsActive,
        paidOnly = true
    )
)

/**
 * Paywall — hero value showcase, interactive 2×2 Bento capability matrix,
 * anchored tier cards with annual value framing, and sticky bottom CTA dock.
 *
 * Follows Morro's design tokens strictly: pure AMOLED black canvas, warm neutral
 * surfaces, 1px concentric hairlines, sentence case everywhere, and Honey restricted
 * to at most two elements (the featured badge and the active selection check).
 */
@Composable
fun PaywallScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = morroColors()
    val typo = morroTypography()
    val reducedMotion = rememberReducedMotion()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val isPaidSelected = selected > 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        MorroSky(intensity = 0.45f)
        MorroSkyScrim(alpha = 0.90f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Top bar: quiet Skip with >= 48dp touch target
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onContinue,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Skip",
                        style = typo.labelLarge,
                        color = colors.textSecondary
                    )
                }
            }

            // Scrollable editorial + bento + plan tier body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                ) {
                    // Hero mascot stage with single-neutral warm tone vignette (no hue gradient)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredReveal(0),
                        contentAlignment = Alignment.Center
                    ) {
                        val haloInner = colors.surfaceHigh.copy(alpha = 0.72f)
                        val haloOuter = colors.canvas.copy(alpha = 0f)
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .drawWithCache {
                                    val haloBrush = Brush.radialGradient(colors = listOf(haloInner, haloOuter))
                                    onDrawBehind {
                                        drawCircle(brush = haloBrush)
                                    }
                                }
                        )
                        MorroGhostMascot(
                            modifier = Modifier.size(width = 76.dp, height = 90.dp),
                            happy = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Own your mornings",
                        style = typo.displayLarge,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredReveal(1)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "No snooze loops. One promise in your voice, backed by gates built to wake your mind.",
                        style = typo.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredReveal(1)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Factual on-device capability spec bar (tabular figures, zero fake stats)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredReveal(2)
                            .clip(morroShapes().Chip)
                            .background(colors.surfaceCard)
                            .morroHairline(radius = 12.dp, color = colors.borderSubtle)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpecMetric(value = "320", label = "Chess mates")
                        SpecDivider()
                        SpecMetric(value = "4×4 · 9×9", label = "Sudoku")
                        SpecDivider()
                        SpecMetric(value = "100%", label = "On-device")
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Interactive 2x2 Bento capability showcase (lights up paid gates dynamically)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredReveal(2),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BentoCapabilities.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { cap ->
                                    BentoCapabilityCard(
                                        capability = cap,
                                        unlocked = !cap.paidOnly || isPaidSelected,
                                        reducedMotion = reducedMotion,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Text(
                        text = "Choose your plan",
                        style = typo.headlineSmall,
                        color = colors.textPrimary,
                        modifier = Modifier.staggeredReveal(3)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PaywallPlans.forEachIndexed { index, plan ->
                            PlanCard(
                                plan = plan,
                                selected = index == selected,
                                index = index + 3,
                                reducedMotion = reducedMotion,
                                onSelect = { selected = index }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Sticky bottom conversion dock (always visible, thumb-reachable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
            ) {
                HorizontalDivider(thickness = 1.dp, color = colors.borderSubtle)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val ctaLabel = when (selected) {
                            0 -> "Continue with Free"
                            1 -> "Unlock Plus · ${PaywallPlans[1].price}/mo"
                            else -> "Unlock Pro · ${PaywallPlans[2].price}/yr"
                        }

                        MorroPrimaryButton(
                            text = ctaLabel,
                            onClick = onContinue,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Local-first privacy · Tied to your Google Play account",
                                style = typo.labelSmall,
                                color = colors.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Billing is not connected in this build. Sample layout only — nothing is charged.",
                            style = typo.labelSmall,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecMetric(value: String, label: String) {
    val colors = morroColors()
    val typo = morroTypography()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = typo.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textPrimary
        )
        Text(
            text = label,
            style = typo.labelSmall,
            color = colors.textTertiary
        )
    }
}

@Composable
private fun SpecDivider() {
    val colors = morroColors()
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(colors.borderSubtle)
    )
}

@Composable
private fun BentoCapabilityCard(
    capability: BentoCapability,
    unlocked: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()
    val animDuration = if (reducedMotion) 0 else MorroMotion.EnterMillis

    val surfaceColor by animateColorAsState(
        targetValue = if (unlocked) colors.surfaceElevated else colors.surfaceCard,
        animationSpec = tween(durationMillis = animDuration),
        label = "bentoFill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (unlocked && capability.paidOnly) colors.borderStrong else colors.borderSubtle,
        animationSpec = tween(durationMillis = animDuration),
        label = "bentoBorder"
    )
    val titleColor by animateColorAsState(
        targetValue = if (unlocked) colors.textPrimary else colors.textTertiary,
        animationSpec = tween(durationMillis = animDuration),
        label = "bentoTitle"
    )

    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .clip(shapes.Card)
            .background(surfaceColor)
            .morroHairline(radius = 16.dp, color = borderColor)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = capability.icon,
                contentDescription = null,
                tint = if (unlocked) colors.textPrimary else colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (!capability.paidOnly) "Included" else if (unlocked) "Unlocked" else "Plus / Pro",
                style = typo.labelSmall,
                color = if (unlocked) colors.textSecondary else colors.textTertiary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column {
            Text(
                text = capability.title,
                style = typo.titleMedium,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = capability.detail,
                style = typo.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = if (unlocked) colors.textSecondary else colors.textDisabled
            )
        }
    }
}

/**
 * A plan row: name, badge / savings cue, concise difference, price and selection check.
 * Three signals — border, fill and check — so selection is never colour alone.
 */
@Composable
private fun PlanCard(
    plan: Plan,
    selected: Boolean,
    index: Int,
    reducedMotion: Boolean,
    onSelect: () -> Unit,
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val animDuration = if (reducedMotion) 0 else MorroMotion.EnterMillis

    val container by animateColorAsState(
        targetValue = if (selected) colors.surfaceElevated else colors.surfaceCard,
        animationSpec = tween(durationMillis = animDuration),
        label = "planFill"
    )
    val border by animateColorAsState(
        targetValue = if (selected) colors.borderActive else colors.borderSubtle,
        animationSpec = tween(durationMillis = animDuration),
        label = "planBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredReveal(index)
            .pressScale(interactionSource, pressedScale = MorroMotion.RowPressedScale)
            .clip(shapes.Card)
            .background(container)
            .morroHairline(radius = 16.dp, color = border)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(color = colors.textPrimary),
                role = Role.RadioButton,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect()
                }
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plan.name,
                        style = typo.headlineSmall,
                        color = colors.textPrimary
                    )
                    if (plan.popular) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // Honey mark #1: highlighted plan badge
                        Text(
                            text = "Most chosen",
                            style = typo.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.honey,
                            modifier = Modifier
                                .clip(shapes.Chip)
                                .background(colors.honey.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    } else if (plan.name == "Pro") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save 44%",
                            style = typo.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.textSecondary,
                            modifier = Modifier
                                .clip(shapes.Chip)
                                .background(colors.surfaceHigh)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = plan.blurb,
                    style = typo.bodyMedium.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = plan.price,
                    style = typo.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textPrimary
                )
                Text(
                    text = plan.period,
                    style = typo.labelSmall,
                    color = colors.textTertiary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Honey mark #2: active selection check indicator
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (selected) colors.honey.copy(alpha = 0.14f) else colors.surfaceHigh,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(tween(if (reducedMotion) 0 else 140)) + scaleIn(
                        initialScale = if (reducedMotion) 1f else 0.5f,
                        animationSpec = tween(if (reducedMotion) 0 else 140, easing = MorroMotion.EnterEasing)
                    ),
                    exit = fadeOut(tween(if (reducedMotion) 0 else 100))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.honey,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
