package com.morro.alarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ButtonDefaults
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography
import com.morro.alarm.ui.theme.rememberMorroSpring
import com.morro.alarm.ui.theme.springToAlpha
import com.morro.alarm.ui.theme.springToDp
import com.morro.alarm.ui.theme.springToScale
import kotlinx.coroutines.flow.map

/**
 * MorroPrimaryButton — Full-width terminal action button (CTA).
 *
 * SPEC (per Manifesto):
 * - Height: 56dp (exceeds 48dp minimum touch target), full-width, radius: 14dp
 * - Background: AccentTactical (#E85D2A) enabled / 40% alpha disabled
 * - Text: Black, Bold, 16sp, 1sp letter-spacing, UPPERCASE
 * - Pressed: scale 0.97, elevation 4dp (response 0.12s, SpringSpecPress)
 * - Focus ring: blooming ring at radius + 2dp (16dp concentric)
 *
 * Touch target: 56dp height × full width — comfortably exceeds the 48dp minimum.
 * All state reactions (press scale, elevation, focus ring) drive from a single MorroSpring
 * so the button responds instantly to touch with no animation desync.
 *
 * Usage:
 * ```
 * MorroPrimaryButton(
 *     text = "CONTINUE",
 *     enabled = true,
 *     onClick = { /* navigate */ }
 * )
 * ```
 *
 * @param text uppercase label shown inside the button
 * @param onClick fired on tap
 * @param modifier outer modifier
 * @param enabled false = 40% alpha background, non-interactive
 * @param isFocused true when focused (keyboard/accessibility)
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
    val density = LocalDensity.current.density

    // ── Press state from interaction source ──
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState()

    // ── Two springs: press (fast 0.12s) + focus (default 0.35s) ──
    val pressSpring = rememberMorroSpring(
        initialValue = if (isPressed.value) 1f else 0f,
        spec = MorroSpring.FloatSpringPress
    )
    val focusSpring = rememberMorroSpring(
        initialValue = if (isFocused) 1f else 0f,
        spec = MorroSpring.FloatSpringDefault
    )

    LaunchedEffect(isPressed.value) {
        pressSpring.animateTo(if (isPressed.value) 1f else 0f)
    }
    LaunchedEffect(isFocused) {
        focusSpring.animateTo(if (isFocused) 1f else 0f)
    }

    // ── Derived press values ──
    val pressScale = springToScale(pressSpring, 1f, 0.97f)
    val pressElevation = springToDp(pressSpring, 1.dp, 4.dp)

    // ── Derived focus ring values ──
    val focusRingAlpha = springToAlpha(focusSpring, 0f, 1f)
    val focusRingScale = springToScale(focusSpring, 1f, 1.02f)

    // Focus ring radius = button radius (14dp) + 2dp = 16dp, converted to px
    val focusRingRadiusPx = (14f + 2f) * density // 16dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp) // Exceeds 48dp minimum touch target
            .clip(shapes.Button)
            .background(
                if (enabled) colors.accentTactical else colors.accentTactical.copy(alpha = 0.4f)
            )
            // Press scale + elevation via graphicsLayer (transform/opacity only — no layout thrash)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                shadowElevation = pressElevation.value
            }
            // Blooming focus ring behind the button
            .drawBehind {
                if (focusRingAlpha > 0f) {
                    val ringRadius = (focusRingRadiusPx + 2f * density) * focusRingScale
                    val ringColor = colors.accentTactical.copy(alpha = focusRingAlpha)
                    drawCircle(
                        color = ringColor,
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = ringRadius,
                        style = Stroke(width = 2f * density)
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = text.uppercase(),
            style = typo.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = if (enabled) Color(0xFF000000) else Color(0xFF000000).copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Collects press state from a MutableInteractionSource into a Compose State.
 * Properly tracks Press (true) and non-press (false).
 */
@Composable
private fun MutableInteractionSource.collectIsPressedAsState(): androidx.compose.runtime.State<Boolean> {
    return interactions
        .map { interaction ->
            when (interaction) {
                is PressInteraction.Press -> true
                else -> false
            }
        }
        .collectAsState(false)
}