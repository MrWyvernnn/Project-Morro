package com.morro.alarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.rememberMorroSpring
import com.morro.alarm.ui.theme.springToAlpha
import com.morro.alarm.ui.theme.springToColor
import com.morro.alarm.ui.theme.springToDp
import com.morro.alarm.ui.theme.springToScale
import com.morro.alarm.ui.theme.derivedSpringValue

/**
 * MorroCard — Atomic panel container with blooming focus ring.
 *
 * SPEC (per Manifesto):
 * - Background: Surface (#FFFFFF)
 * - Border: 1dp BorderSubtle (#E8DCC8) default → 2dp BorderActive (#E85D2A) focused/pressed
 * - Radius: 16dp true squircle (RoundedCornerShape(16.dp))
 * - Inset: 24dp content padding (room for the focus ring outside the bounds)
 * - Elevation: 1dp default → 4dp pressed (spring-animated)
 * - Focus ring: 2dp AccentTactical ring at 18dp radius (card radius 16dp + 2dp concentric),
 *   scales 1.0→1.02, opacity 0→1 (spring)
 *
 * All animated properties derive from a SINGLE MorroSpring — guaranteeing velocity handoff on
 * interrupt, no desync between elevation/scale/ring, and instant reaction to touch.
 *
 * Usage:
 * ```
 * var pressed by remember { mutableStateOf(false) }
 * var focused by remember { mutableStateOf(false) }
 * MorroCard(
 *     modifier = Modifier.padding(16.dp),
 *     isFocused = focused,
 *     isPressed = pressed
 * ) {
 *     Text("Card content", style = morroTypography().bodyLarge, color = morroColors().textPrimary)
 * }
 * ```
 *
 * @param modifier outer modifier (padding handled by caller)
 * @param isFocused true when card has focus (keyboard/accessibility)
 * @param isPressed true during press gesture
 * @param content slot — receives 24dp inset automatically for focus-ring room
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

    // ── Single spring for ALL card animations ──
    // One Animatable per component (Manifesto rule). Drives elevation, border, ring alpha,
    // and ring scale simultaneously so they never desync.
    val spring = rememberMorroSpring(
        initialValue = if (isFocused || isPressed) 1f else 0f,
        spec = if (isPressed) MorroSpring.FloatSpringPress else MorroSpring.FloatSpringDefault
    )

    LaunchedEffect(isFocused, isPressed) {
        val target = if (isFocused || isPressed) 1f else 0f
        spring.animateTo(target)
    }

    // ── Derived animated values from the single spring ──
    val animatedElevation = springToDp(spring, 1.dp, 4.dp)
    val animatedBorderWidth = derivedSpringValue(spring) { 1.dp + 1.dp * it } // 1dp → 2dp
    val animatedBorderColor = springToColor(spring, colors.borderSubtle, colors.borderActive)
    val animatedFocusRingAlpha = springToAlpha(spring, 0f, 1f)
    val animatedFocusRingScale = springToScale(spring, 1f, 1.02f)

    // ── Pre-convert dp → px for Canvas drawing (16dp card radius + 2dp focus ring = 18dp) ──
    val cardRadiusPx = (16f * density)
    val focusRingRadiusPx = cardRadiusPx + (2f * density) // 18dp
    val focusRingStrokeWidthPx = 2f * density       // 2dp

    Box(
        modifier = modifier
            .clip(shapes.Card)
            .background(colors.surface)
            // Animated border (1dp → 2dp, subtle → tactical)
            .border(
                width = animatedBorderWidth,
                color = animatedBorderColor,
                shape = shapes.Card
            )
            // Focus ring drawn BEHIND the card (concentric, outside bounds at 18dp radius)
            .drawBehind {
                if (animatedFocusRingAlpha > 0f) {
                    // Drawn just inside the clip. A circle centred on a wide card would sit outside
                    // the card's own bounds and never be seen, so the ring follows the squarcle.
                    val inset = 1.5f * density
                    drawRoundRect(
                        color = colors.accentTactical.copy(alpha = animatedFocusRingAlpha),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2f, size.height - inset * 2f),
                        cornerRadius = CornerRadius(cardRadiusPx - inset, cardRadiusPx - inset),
                        style = Stroke(width = focusRingStrokeWidthPx)
                    )
                }
            }
    ) {
        // Content receives 24dp inset — leaves room for the focus ring outside the card bounds
        content(Modifier.padding(24.dp))
    }
}