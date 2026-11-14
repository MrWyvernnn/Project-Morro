package com.morro.alarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.rememberMorroSpring
import com.morro.alarm.ui.theme.springToAlpha
import com.morro.alarm.ui.theme.springToColor
import com.morro.alarm.ui.theme.springToDp

/**
 * MorroCard — quiet panel container (20dp squarcle) with a blooming focus ring.
 *
 * One Animatable drives border width, border colour and the ring together, so a card can never be
 * half-focused. The ring is drawn inside the card's own bounds; a circle centred on a wide card
 * would land outside the clip and never be seen.
 */
@Composable
fun MorroCard(
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    isPressed: Boolean = false,
    content: @Composable (Modifier) -> Unit
) {
    val colors = morroColors()
    val shapes = morroShapes()
    val density = LocalDensity.current.density
    val cardRadiusDp = 20f

    val spring = rememberMorroSpring(
        initialValue = if (isFocused || isPressed) 1f else 0f,
        spec = if (isPressed) MorroSpring.FloatSpringPress else MorroSpring.FloatSpringDefault
    )

    LaunchedEffect(isFocused, isPressed) {
        val target = if (isFocused || isPressed) 1f else 0f
        spring.animateTo(
            targetValue = target,
            animationSpec = if (isPressed) MorroSpring.FloatSpringPress else MorroSpring.FloatSpringDefault
        )
    }

    val animatedElevation = springToDp(spring, 0.dp, 4.dp)
    val animatedBorderWidth = springToDp(spring, 1.dp, 2.dp)
    val animatedBorderColor = springToColor(spring, colors.borderSubtle, colors.accentTactical)
    val ringAlpha = springToAlpha(spring, 0f, 1f)
    val cardRadiusPx = cardRadiusDp * density

    Box(
        modifier = modifier
            .clip(shapes.Card)
            .background(colors.surfaceCard)
            .border(animatedBorderWidth, animatedBorderColor, shapes.Card)
            // Elevation is a state change, not a resting shadow: zero at rest, 4dp when touched.
            .drawBehind {
                if (animatedElevation.value > 0.1f) {
                    drawRoundRect(
                        color = colors.canvas.copy(alpha = (animatedElevation.value / 4f) * 0.35f),
                        topLeft = Offset(2f * density, 2f * density),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(cardRadiusPx, cardRadiusPx)
                    )
                }
                if (ringAlpha > 0f) {
                    val inset = 1.5f * density
                    drawRoundRect(
                        color = colors.accentBubbly.copy(alpha = ringAlpha * 0.9f),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2f, size.height - inset * 2f),
                        cornerRadius = CornerRadius(cardRadiusPx - inset, cardRadiusPx - inset),
                        style = Stroke(width = 2f * density)
                    )
                }
            }
    ) {
        content(Modifier.padding(20.dp))
    }
}
