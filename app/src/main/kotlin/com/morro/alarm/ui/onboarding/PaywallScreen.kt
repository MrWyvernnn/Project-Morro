package com.morro.alarm.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.components.MorroGhostMascot
import com.morro.alarm.ui.components.MorroPrimaryButton
import com.morro.alarm.ui.components.MorroSky
import com.morro.alarm.ui.components.MorroSkyScrim
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.motion.staggeredReveal
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.morroHairline

/**
 * One plan. Names and prices are placeholders: nothing here is priced, stored or charged.
 *
 * Internal rather than private so `PaywallPlansTest` can hold the offer to its shape (three plans,
 * one free plan first, at most one highlighted) without starting a device.
 */
internal data class Plan(
    val name: String,
    val price: String,
    val period: String,
    val blurb: String,
    val popular: Boolean = false,
)

/**
 * Three plans, written as sample copy. The prices are deliberately arbitrary — this screen is a
 * layout, not a price list, and it says so in one quiet line at the bottom.
 */
internal val PaywallPlans: List<Plan> = listOf(
    Plan(
        name = "Free",
        price = "$0",
        period = "forever",
        blurb = "One alarm, one promise, the math and voice gates."
    ),
    Plan(
        name = "Plus",
        price = "$2.99",
        period = "per month",
        blurb = "Everything in Free, plus photo and QR missions.",
        popular = true
    ),
    Plan(
        name = "Pro",
        price = "$19.99",
        period = "per year",
        blurb = "Everything in Plus, paid once a year.",
    )
)

/**
 * Paywall — a visual placeholder between the questions and the permissions screen.
 *
 * There is no billing in this build and no network access, so the plan list only selects and the
 * Continue button moves the flow on. The structure is the real one a paywall would need: a default
 * selection (Free, so nothing is pre-chosen against the person), one highlighted plan, and one
 * filled action. Adding Play Billing later swaps the selection callback and the CTA.
 *
 * This screen is quiet on purpose: no clouds, no mascot, no badge colour. The plans are the content
 * and the selected card is the only thing that brightens.
 */
@Composable
fun PaywallScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MorroColors.Canvas)
    ) {
        MorroSky(intensity = 0.5f)
        MorroSkyScrim(alpha = 0.90f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onContinue) {
                Text(
                    text = "Skip",
                    color = MorroColors.TextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp
                )
            }
        }

        Column(
            modifier = Modifier
                // Cap before filling: the other order pins the block to the tablet width.
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .staggeredReveal(0)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // The same smiling ghost that opens the app, small enough not to compete with the
            // prices: the character carries the screen, the plans carry the decision.
            MorroGhostMascot(
                modifier = Modifier
                    .size(width = 76.dp, height = 88.dp)
                    .align(Alignment.CenterHorizontally),
                happy = true
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Choose your plan",
                color = MorroColors.TextPrimary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 30.sp,
                lineHeight = 37.sp,
                letterSpacing = (-0.4).sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Every plan keeps the alarm, the mission and your own voice. Paid plans add more ways to prove you are up.",
                color = MorroColors.TextSecondary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PaywallPlans.forEachIndexed { index, plan ->
                    PlanCard(
                        plan = plan,
                        selected = index == selected,
                        index = index + 1,
                        onSelect = { selected = index }
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            MorroPrimaryButton(
                text = if (selected == 0) "Continue with Free" else "Continue",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Billing is not connected in this build. These plans are sample copy and nothing is charged.",
                color = MorroColors.TextTertiary,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
}

/**
 * A plan row: name and price at reading scale, the one-line difference underneath, and a check when
 * chosen. Three signals — border, fill and check — so selection is never colour alone.
 */
@Composable
private fun PlanCard(
    plan: Plan,
    selected: Boolean,
    index: Int,
    onSelect: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val container by animateColorAsState(
        targetValue = if (selected) MorroColors.SurfaceElevated else MorroColors.SurfaceCard,
        animationSpec = tween(durationMillis = MorroMotion.EnterMillis),
        label = "planFill"
    )
    val border by animateColorAsState(
        targetValue = if (selected) MorroColors.BorderActive else MorroColors.BorderSubtle,
        animationSpec = tween(durationMillis = MorroMotion.EnterMillis),
        label = "planBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredReveal(index)
            .pressScale(interactionSource, pressedScale = MorroMotion.RowPressedScale)
            .clip(MorroShapes.Card)
            .background(container)
            .morroHairline(radius = 16.dp, color = border)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(color = MorroColors.TextPrimary),
                role = Role.RadioButton,
                onClick = {
                    // Same confirmation the question cards give: the choice registers in the hand.
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
                        color = MorroColors.TextPrimary,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (plan.popular) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Most chosen",
                            color = MorroColors.Honey,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MorroColors.Honey.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = plan.blurb,
                    color = MorroColors.TextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = plan.price,
                    color = MorroColors.TextPrimary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = plan.period,
                    color = MorroColors.TextTertiary,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (selected) MorroColors.Honey.copy(alpha = 0.14f) else MorroColors.SurfaceHigh,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(tween(140)) + scaleIn(
                        initialScale = 0.5f,
                        animationSpec = tween(140, easing = MorroMotion.EnterEasing)
                    ),
                    exit = fadeOut(tween(100))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MorroColors.Honey,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
