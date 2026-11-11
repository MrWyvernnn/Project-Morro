package com.morro.alarm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.morro.alarm.ui.theme.MorroShapes
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography
import com.morro.alarm.ui.theme.rememberMorroSpring
import com.morro.alarm.ui.theme.springToAlpha
import com.morro.alarm.ui.theme.springToColor
import com.morro.alarm.ui.theme.springToScale
import com.morro.alarm.ui.theme.derivedSpringValue

// ─────────────────────────────────────────────────────────────────────────────
// MorroOptionChip — Mission select chip
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MorroOptionChip — Single-select option row (radio-button-style).
 *
 * SPEC:
 * - Height: 56dp, radius: 12dp
 * - States: idle → selected → focused → pressed
 * - Selected: BorderActive + filled Tactical pip
 * - Focus ring: same blooming ring as MorroCard (radius + 2dp)
 *
 * @param title primary label (uppercase, bold)
 * @param subtitle supporting description (secondary color)
 * @param tagLabel monospace protocol tag (e.g. "VOICE", "QR", "PHOTO")
 * @param isSelected true = armed / selected state
 * @param isFocused true when focused (keyboard/accessibility)
 * @param onSelect called when the chip is tapped
 */
@Composable
fun MorroOptionChip(
    title: String,
    subtitle: String,
    tagLabel: String,
    isSelected: Boolean,
    isFocused: Boolean = false,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = morroColors()
    val shapes = morroShapes()
    val typo = morroTypography()
    val density = LocalDensity.current.density

    // Single spring for chip animations
    val spring = rememberMorroSpring(
        initialValue = if (isSelected || isFocused) 1f else 0f,
        spec = MorroSpring.FloatSpringDefault
    )

    LaunchedEffect(isSelected, isFocused) {
        val target = if (isSelected || isFocused) 1f else 0f
        spring.animateTo(target)
    }

    // Derived values
    val animatedBorderWidth = derivedSpringValue(spring) { 1.dp + 1.dp * it } // 1dp → 2dp
    val animatedBorderColor = springToColor(spring, colors.borderSubtle, colors.borderActive)
    val animatedBackground = springToColor(spring, colors.surfaceLow, colors.surfaceVariant)
    val animatedTagColor = springToColor(spring, colors.textSecondary, colors.accentTactical)
    val animatedPipColor = springToColor(spring, colors.borderSubtle, colors.accentTactical)
    val animatedFocusRingAlpha = springToAlpha(spring, 0f, 1f)
    val animatedFocusRingScale = springToScale(spring, 1f, 1.02f)

    val interactionSource = remember { MutableInteractionSource() }

    // Focus ring radius = chip radius (12dp) + 2dp = 14dp, converted to px
    val focusRingRadiusPx = (12f + 2f) * density // 14dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shapes.Chip)
            .background(animatedBackground)
            .border(
                width = animatedBorderWidth,
                color = animatedBorderColor,
                shape = shapes.Chip
            )
            .drawBehind {
                if (animatedFocusRingAlpha > 0f) {
                    val ringRadius = (focusRingRadiusPx + MorroShapes.FocusRing.value.toFloat() * density) * animatedFocusRingScale
                    val ringColor = colors.accentTactical.copy(alpha = animatedFocusRingAlpha)
                    drawCircle(
                        color = ringColor,
                        radius = ringRadius,
                        style = Stroke(width = 2f * density)
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tag label — monospace protocol tag
            Text(
                text = tagLabel,
                style = typo.labelLarge,
                color = animatedTagColor
            )

            Spacer(modifier = Modifier.weight(1f))

            // Pip indicator
            PipIndicator(
                isSelected = isSelected,
                pipColor = animatedPipColor
            )
        }
    }
}

/**
 * PipIndicator — 20dp circular selection indicator.
 * Filled circle with check when selected, open ring when unselected.
 */
@Composable
fun PipIndicator(
    isSelected: Boolean,
    pipColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (isSelected) pipColor else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = pipColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = Color(0xFFFFFFFF), // White check on tactical orange background
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: collectIsPressedAsState for MutableInteractionSource
// ─────────────────────────────────────────────────────────────────────────────

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
