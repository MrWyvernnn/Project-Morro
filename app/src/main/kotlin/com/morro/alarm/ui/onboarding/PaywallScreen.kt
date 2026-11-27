package com.morro.alarm.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroGlassBorder
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

private data class TimelineNode(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val lit: Boolean,
)

/**
 * Paywall — clarity-first vertical connected timeline + 3-column bento plan comparison dock.
 *
 * Structure inspired by transparent trial timelines:
 *  - Top half: vertical connected node rail showing what happens right now, what unlocks, and
 *    how local privacy + Google Play billing behave (updates smoothly as plans are selected).
 *  - Bottom half: smoked-glass conversion dock with 3 side-by-side plan columns (`Free` first and
 *    selected by default, `Plus` lifted with "Most chosen", `Pro` with "Save 44%") and one CTA.
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

    val timelineNodes = remember(selected) {
        if (!isPaidSelected) {
            listOf(
                TimelineNode(
                    title = "Setup complete",
                    detail = "Your morning commitment and alarm rules stay right on this phone.",
                    icon = Icons.Filled.Check,
                    lit = true
                ),
                TimelineNode(
                    title = "Today: Core gates open",
                    detail = "Reliable lock-screen alarm, 15s voice promise, plus Math, Chess & Sudoku.",
                    icon = Icons.Filled.LockOpen,
                    lit = true
                ),
                TimelineNode(
                    title = "Upgrade anytime later",
                    detail = "Photo hunt, QR room scan, and 3-min Wake-up check live in Plus & Pro.",
                    icon = Icons.Filled.Lock,
                    lit = false
                )
            )
        } else {
            listOf(
                TimelineNode(
                    title = "Setup complete",
                    detail = "Your morning commitment and alarm rules stay right on this phone.",
                    icon = Icons.Filled.Check,
                    lit = true
                ),
                TimelineNode(
                    title = "Today: All gates unlocked",
                    detail = "Photo object hunt, QR room scan, Wake-up check, Voice, Math, Chess & Sudoku.",
                    icon = Icons.Filled.LockOpen,
                    lit = true
                ),
                TimelineNode(
                    title = "Clear renewal reminder",
                    detail = "Google Play sends a reminder before billing renews. Cancel in seconds.",
                    icon = Icons.Filled.NotificationsActive,
                    lit = true
                ),
                TimelineNode(
                    title = "Zero accounts, zero cloud",
                    detail = "Entitlement follows your Play account; voice memos never leave this device.",
                    icon = Icons.Filled.VerifiedUser,
                    lit = false
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
    ) {
        MorroSky(intensity = 0.50f)
        MorroSkyScrim(alpha = 0.84f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Top bar: mini companion mascot + Poke button + quiet Skip
            var headerPokeSignal by remember { mutableIntStateOf(0) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MorroGhostMascot(
                        modifier = Modifier.size(width = 40.dp, height = 46.dp),
                        happy = true,
                        pokeSignal = headerPokeSignal
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (headerPokeSignal == 0) "Poke" else "Hehe! ($headerPokeSignal)",
                        style = typo.labelLarge,
                        color = colors.honey,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.surfaceCard.copy(alpha = 0.78f))
                            .morroGlassBorder(radius = 50.dp, topAlpha = 0.22f, bottomAlpha = 0.04f)
                            .clickable { headerPokeSignal++ }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
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

            // Scrollable editorial + connected vertical timeline
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
                    Spacer(modifier = Modifier.height(6.dp))

                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else {
                                (fadeIn(tween(200)) + slideInVertically { it / 4 })
                                    .togetherWith(fadeOut(tween(130)))
                            }
                        },
                        label = "paywallHeadline"
                    ) { planIdx ->
                        val title = when (planIdx) {
                            0 -> "How Morro works for you"
                            1 -> "Unlock every morning gate"
                            else -> "Full year of mornings, unlocked"
                        }
                        val sub = when (planIdx) {
                            0 -> "Start free with voice promise, Math, Chess & Sudoku."
                            1 -> "Just ${PaywallPlans[1].price}/month — cancel anytime in Play Store."
                            else -> "Just ${PaywallPlans[2].price}/year — equivalent to \$1.67/month."
                        }
                        Column {
                            Text(
                                text = title,
                                style = typo.headlineLarge,
                                color = colors.textPrimary,
                                modifier = Modifier.staggeredReveal(0)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = sub,
                                style = typo.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                                color = colors.textSecondary,
                                modifier = Modifier.staggeredReveal(1)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Vertical connected timeline rail
                    AnimatedContent(
                        targetState = isPaidSelected,
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else {
                                (fadeIn(tween(220, easing = MorroMotion.EnterEasing)) +
                                    slideInVertically(tween(220)) { 14 })
                                    .togetherWith(fadeOut(tween(140)))
                            }
                        },
                        label = "timelineContent"
                    ) { _ ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            timelineNodes.forEachIndexed { idx, node ->
                                TimelineStepRow(
                                    node = node,
                                    isLast = idx == timelineNodes.lastIndex,
                                    stepIndex = idx,
                                    reducedMotion = reducedMotion
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // Smoked Dark Glass Bottom Conversion Dock (3-column side-by-side plan grid + CTA)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(colors.surface.copy(alpha = 0.92f))
                    .morroGlassBorder(radius = 24.dp, topAlpha = 0.24f, bottomAlpha = 0.02f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 3-Column Bento Plan Comparison Cards
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            PaywallPlans.forEachIndexed { index, plan ->
                                CompactPlanColumn(
                                    plan = plan,
                                    selected = index == selected,
                                    reducedMotion = reducedMotion,
                                    onSelect = { selected = index },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Dynamic 1-line plan blurb for whichever plan is highlighted
                        AnimatedContent(
                            targetState = PaywallPlans[selected],
                            transitionSpec = {
                                if (reducedMotion) {
                                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                } else {
                                    fadeIn(tween(160)) togetherWith fadeOut(tween(110))
                                }
                            },
                            label = "planBlurb"
                        ) { activePlan ->
                            Text(
                                text = activePlan.blurb,
                                style = typo.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

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

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sample layout only · Nothing is charged in this build",
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
}

@Composable
private fun TimelineStepRow(
    node: TimelineNode,
    isLast: Boolean,
    stepIndex: Int,
    reducedMotion: Boolean,
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    val iconBg by animateColorAsState(
        targetValue = if (node.lit) colors.honey.copy(alpha = 0.16f) else colors.surfaceElevated,
        animationSpec = tween(if (reducedMotion) 0 else 220),
        label = "nodeBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (node.lit) colors.honey else colors.textTertiary,
        animationSpec = tween(if (reducedMotion) 0 else 220),
        label = "nodeTint"
    )
    val connectorColor by animateColorAsState(
        targetValue = if (node.lit) colors.honey.copy(alpha = 0.42f) else colors.borderSubtle,
        animationSpec = tween(if (reducedMotion) 0 else 240),
        label = "connector"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .staggeredReveal(stepIndex + 1)
    ) {
        // Vertical track & node column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(34.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(shapes.Chip)
                    .background(iconBg)
                    .morroHairline(
                        radius = 12.dp,
                        color = if (node.lit) colors.honey.copy(alpha = 0.42f) else colors.borderSubtle
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = node.icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(connectorColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp, bottom = if (isLast) 4.dp else 18.dp)
        ) {
            Text(
                text = node.title,
                style = typo.titleMedium,
                color = if (node.lit) colors.textPrimary else colors.textSecondary
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = node.detail,
                style = typo.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun CompactPlanColumn(
    plan: Plan,
    selected: Boolean,
    reducedMotion: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    val liftDp by animateDpAsState(
        targetValue = if (selected) (-6).dp else if (plan.popular) (-2).dp else 0.dp,
        animationSpec = if (reducedMotion) tween(0) else spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "planLift"
    )
    val scaleVal by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = if (reducedMotion) tween(0) else MorroSpring.FloatSpringPlayful,
        label = "planScale"
    )
    val fill by animateColorAsState(
        targetValue = if (selected) colors.surfaceElevated else colors.surfaceCard,
        animationSpec = tween(if (reducedMotion) 0 else 180),
        label = "colFill"
    )

    Box(
        modifier = modifier
            .offset(y = liftDp)
            .graphicsLayer {
                scaleX = scaleVal
                scaleY = scaleVal
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .pressScale(interactionSource, pressedScale = 0.96f, releaseSpec = MorroSpring.FloatSpringPlayful)
                .clip(shapes.Card)
                .background(fill)
                .morroGlassBorder(
                    radius = 16.dp,
                    width = if (selected) 1.5.dp else 1.dp,
                    topAlpha = if (selected) 0.42f else 0.18f,
                    bottomAlpha = if (selected) 0.14f else 0.05f,
                    tint = if (selected) colors.honey else colors.textPrimary
                )
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
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = plan.name,
                style = typo.labelLarge,
                color = if (selected) colors.honey else colors.textSecondary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = plan.price,
                    style = typo.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp
                    ),
                    color = colors.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = plan.period,
                    style = typo.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subtle cue or active honey check pip
            Box(
                modifier = Modifier.height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.5f),
                    exit = fadeOut(tween(100))
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(colors.honey),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = colors.canvas,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
                if (!selected && plan.name == "Pro") {
                    Text(
                        text = "Save 44%",
                        style = typo.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.textSecondary
                    )
                }
            }
        }

        // Overlapping top pill badge on Popular plan (inspired by Bloom screenshot, styled in Morro tokens)
        if (plan.popular) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-9).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) colors.honey else colors.surfaceHigh)
                    .morroHairline(
                        radius = 8.dp,
                        color = if (selected) colors.honey else colors.borderStrong
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Popular",
                    style = typo.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                    color = if (selected) colors.canvas else colors.honey,
                    maxLines = 1
                )
            }
        }
    }
}
