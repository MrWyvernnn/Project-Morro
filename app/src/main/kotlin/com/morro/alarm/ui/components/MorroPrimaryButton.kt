package com.morro.alarm.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.lerpColor
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography

/**
 * MorroPrimaryButton — the one filled action on a screen.
 *
 * On a black interface the most deliberate primary is not a colour, it is light: a warm off-white
 * fill with near-black text. It reads as the brightest thing on the screen without adding a hue.
 *
 * Press feel: the button dips to 0.96 on a critically damped spring (instant, no lag), then returns
 * on an under-damped spring so it overshoots by about 1% and settles. That small pop is the whole
 * playfulness here — a bigger bounce would read as a toy.
 */
@Composable
fun MorroPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isFocused: Boolean = false
) {
    val colors = morroColors()
    val shapes = morroShapes()
    val typo = morroTypography()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Press: fast and exact. Release: playful, so it lands with a 1% overshoot.
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = if (isPressed) MorroSpring.FloatSpringPress else MorroSpring.FloatSpringPlayful,
        label = "primaryPress"
    )

    val fill = when {
        !enabled -> colors.surfaceElevated
        isPressed -> lerpColor(colors.textPrimary, Color(0xFFDFD3BC), 0.55f)
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                translationY = if (isPressed) 1f else 0f
            }
            .clip(shapes.Button)
            .background(fill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typo.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = if (enabled) MorroColors.Canvas else colors.textTertiary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}
