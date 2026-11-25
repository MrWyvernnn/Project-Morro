package com.morro.alarm.ui.components

import androidx.compose.foundation.background
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
import com.morro.alarm.ui.theme.MorroColors
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.rememberMorroSpring
import com.morro.alarm.ui.theme.springToAlpha
import com.morro.alarm.ui.theme.springToColor

/**
 * MorroCard — the standard panel: a flat surface, a hairline, and nothing else.
 *
 * Depth is a step in tone plus a border at 8% opacity; there is no shadow at rest and no glow, so
 * a screen of cards stays calm. The only state change is focus/press, which lifts the hairline to
 * 35% and adds a ring outside it — a pointer affordance, not decoration.
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
    val cardRadiusDp = 16f

    val spring = rememberMorroSpring(
        initialValue = if (isFocused || isPressed) 1f else 0f
    )

    LaunchedEffect(isFocused, isPressed) {
        spring.animateTo(
            targetValue = if (isFocused || isPressed) 1f else 0f,
            animationSpec = if (isPressed) MorroSpring.FloatSpringPress else MorroSpring.FloatSpringDefault
        )
    }

    val borderColor = springToColor(spring, colors.borderSubtle, colors.borderActive)
    val ringAlpha = springToAlpha(spring, 0f, if (isFocused) 0.5f else 0f)

    Box(
        modifier = modifier
            .clip(shapes.Card)
            .background(colors.surfaceCard)
            .drawBehind {
                val radius = cardRadiusDp * density
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 1.dp.toPx())
                )
                if (ringAlpha > 0f) {
                    val inset = 2f * density
                    drawRoundRect(
                        color = MorroColors.Honey.copy(alpha = ringAlpha),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2f, size.height - inset * 2f),
                        cornerRadius = CornerRadius(radius - inset, radius - inset),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
    ) {
        content(Modifier.padding(20.dp))
    }
}
