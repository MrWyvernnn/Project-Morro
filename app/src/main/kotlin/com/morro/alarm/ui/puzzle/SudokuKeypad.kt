package com.morro.alarm.ui.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.morro.alarm.puzzle.sudoku.SudokuMode
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography

/**
 * Keypad for entering Sudoku digits and clearing cells.
 *
 * Touch targets are at least 56dp tall for reliable morning operation.
 * Adheres strictly to the Morro design system:
 * - morroShapes().Button radius (12dp)
 * - morroHairline border
 * - pressScale via MorroMotion/MorroSpring
 * - Tokenized palette via morroColors() and typography via morroTypography()
 */
@Composable
fun SudokuKeypad(
    maxDigit: Int,
    onDigitClick: (Int) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    require(maxDigit == 4 || maxDigit == 9) { "SudokuKeypad only supports maxDigit 4 or 9 (got $maxDigit)" }

    val items = when (maxDigit) {
        4 -> listOf(
            listOf(
                KeypadAction.Digit(1),
                KeypadAction.Digit(2),
                KeypadAction.Digit(3),
                KeypadAction.Digit(4),
                KeypadAction.Clear
            )
        )
        else -> listOf(
            listOf(
                KeypadAction.Digit(1),
                KeypadAction.Digit(2),
                KeypadAction.Digit(3),
                KeypadAction.Digit(4),
                KeypadAction.Digit(5)
            ),
            listOf(
                KeypadAction.Digit(6),
                KeypadAction.Digit(7),
                KeypadAction.Digit(8),
                KeypadAction.Digit(9),
                KeypadAction.Clear
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in items) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (action in row) {
                    when (action) {
                        is KeypadAction.Digit -> {
                            SudokuKeypadButton(
                                text = action.digit.toString(),
                                onClick = { onDigitClick(action.digit) },
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                                isAction = false
                            )
                        }
                        is KeypadAction.Clear -> {
                            SudokuKeypadButton(
                                text = "Clear",
                                onClick = onClearClick,
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                                isAction = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Overload for [SudokuMode] (STANDARD = 4 digits, HARD = 9 digits).
 */
@Composable
fun SudokuKeypad(
    mode: SudokuMode,
    onDigitClick: (Int) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = SudokuKeypad(
    maxDigit = if (mode == SudokuMode.STANDARD) 4 else 9,
    onDigitClick = onDigitClick,
    onClearClick = onClearClick,
    modifier = modifier,
    enabled = enabled
)

@Composable
private fun SudokuKeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isAction: Boolean = false
) {
    val colors = morroColors()
    val shapes = morroShapes()
    val typo = morroTypography()
    val haptics = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .height(56.dp)
            .pressScale(
                interactionSource = interactionSource,
                enabled = enabled,
                releaseSpec = MorroSpring.FloatSpringPlayful
            )
            .clip(shapes.Button)
            .background(colors.surfaceCard)
            .morroHairline(
                radius = 12.dp,
                color = colors.borderSubtle
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = if (isAction) {
                typo.labelLarge.copy(fontWeight = FontWeight.Medium)
            } else {
                typo.headlineMedium.copy(fontWeight = FontWeight.Medium)
            },
            color = if (isAction) colors.textSecondary else colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private sealed interface KeypadAction {
    data class Digit(val digit: Int) : KeypadAction
    data object Clear : KeypadAction
}
