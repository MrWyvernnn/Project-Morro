package com.morro.alarm.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.theme.MorroColors

/**
 * The arm switch.
 *
 * Material's Switch is replaced because Morro's shell is monochrome: a honey track with a
 * near-black thumb is the only filled accent on the dashboard.
 *
 * Feel: on press the thumb stretches to 112% of its width — the same 12% a physical knob stretches
 * when pushed — and returns on release. Movement is a critically damped spring, never a bounce.
 * The whole 52×48 area is the target, not just the painted track.
 */
@Composable
fun MorroSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val thumbStretch by animateFloatAsState(
        targetValue = if (pressed && enabled) 1.12f else 1f,
        animationSpec = tween(MorroMotion.PressMillis, easing = MorroMotion.EnterEasing),
        label = "switchStretch"
    )
    val thumbProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = MorroMotion.PressRelease,
        label = "switchPosition"
    )
    val track by animateColorAsState(
        targetValue = if (checked) MorroColors.Honey else MorroColors.SurfaceHigh,
        animationSpec = tween(140),
        label = "switchTrack"
    )

    val travel = with(LocalDensity.current) { 20.dp.toPx() }

    Box(
        modifier = modifier
            .width(52.dp)
            .height(48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(track)
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .graphicsLayer { translationX = travel * thumbProgress }
                    .size(width = (24f * thumbStretch).dp, height = 24.dp)
                    .clip(CircleShape)
                    .background(MorroColors.Canvas)
            )
        }
    }
}
