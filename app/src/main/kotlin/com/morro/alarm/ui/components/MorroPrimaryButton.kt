package com.morro.alarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.ui.theme.MorroGradients
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography
import androidx.compose.animation.core.animateFloatAsState

/**
 * MorroPrimaryButton — the one full-width action on a screen.
 *
 * 56dp tall, 16dp squarcle, filled with the signature orange -> pink ribbon. Label is black:
 * black on either end of the gradient measures 6.55:1 and 7.68:1, so it survives both the
 * accessibility check and the founder's "no muddy text" rule.
 *
 * Press feedback is a 0.97 scale on a critically damped 0.12s spring, matching Apple's press
 * response. Disabled is a flat charcoal fill with tertiary text, never a faded gradient.
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
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = MorroSpring.FloatSpringPress,
        label = "primaryPress"
    )

    val fill: Brush = if (enabled) {
        MorroGradients.horizontal()
    } else {
        Brush.horizontalGradient(listOf(colors.surfaceElevated, colors.surfaceElevated))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shapes.Button)
            .background(fill, shapes.Button)
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
            style = typo.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            ),
            color = if (enabled) Color.Black else colors.textTertiary,
            textAlign = TextAlign.Center
        )
    }
}
