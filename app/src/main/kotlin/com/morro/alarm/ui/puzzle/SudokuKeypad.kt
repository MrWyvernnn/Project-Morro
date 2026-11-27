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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.puzzle.sudoku.SudokuMode
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography

/**
 * Morro monochrome keypad for Sudoku digit entry and clearing cells.
 *
 * - Strictly Morro chrome (`morroColors`, `morroShapes().Button`, `morroHairline`, `pressScale`).
 * - Minimum 56dp tall touch targets (`1–4 + Clear` or `1–9 + Clear`).
 * - Optional remaining-count badge per digit in `textTertiary` at `10.sp`; hides badge at 0
 *   remaining and never disables the digit button.
 */
@Composable
fun SudokuKeypad(
    maxDigit: Int,
    onDigitClick: (Int) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    remainingCounts: Map<Int, Int> = emptyMap(),
    notesMode: Boolean = false,
) {
    require(maxDigit == 4 || maxDigit == 9) { "SudokuKeypad only supports maxDigit 4 or 9 (got $maxDigit)" }

    val items: List<List<KeypadAction>> = when (maxDigit) {
        4 -> listOf(
            listOf(1, 2, 3, 4).map { KeypadAction.Digit(it) } + KeypadAction.Clear
        )
        else -> listOf(
            listOf(1, 2, 3, 4, 5).map { KeypadAction.Digit(it) },
            listOf(6, 7, 8, 9).map { KeypadAction.Digit(it) } + KeypadAction.Clear
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (row in items) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (action in row) {
                    when (action) {
                        is KeypadAction.Digit -> {
                            val remaining = remainingCounts[action.digit]
                            SudokuKeypadButton(
                                text = action.digit.toString(),
                                onClick = { onDigitClick(action.digit) },
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                                isAction = false,
                                notesMode = notesMode,
                                remainingCount = if (remaining != null && remaining > 0) remaining else null
                            )
                        }
                        is KeypadAction.Clear -> {
                            SudokuKeypadButton(
                                text = "Clear",
                                onClick = onClearClick,
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                                isAction = true,
                                notesMode = false,
                                remainingCount = null
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Overload for [SudokuMode] (`EASY` = 4 digits, `MEDIUM` / `HARD` = 9 digits).
 */
@Composable
fun SudokuKeypad(
    mode: SudokuMode,
    onDigitClick: (Int) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    remainingCounts: Map<Int, Int> = emptyMap(),
    notesMode: Boolean = false,
) = SudokuKeypad(
    maxDigit = if (mode == SudokuMode.EASY) 4 else 9,
    onDigitClick = onDigitClick,
    onClearClick = onClearClick,
    modifier = modifier,
    enabled = enabled,
    remainingCounts = remainingCounts,
    notesMode = notesMode,
)

@Composable
private fun SudokuKeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isAction: Boolean = false,
    notesMode: Boolean = false,
    remainingCount: Int? = null,
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
                pressedScale = MorroMotion.PressedScale,
                releaseSpec = MorroSpring.FloatSpringPlayful
            )
            .clip(shapes.Button)
            .background(colors.surfaceCard)
            .morroHairline(
                radius = 12.dp,
                color = if (notesMode && !isAction) colors.borderStrong else colors.borderSubtle
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
                typo.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = if (notesMode) 18.sp else 20.sp
                )
            },
            color = when {
                isAction -> colors.textSecondary
                notesMode -> colors.textSecondary
                else -> colors.textPrimary
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )

        if (remainingCount != null) {
            Text(
                text = remainingCount.toString(),
                fontSize = 10.sp,
                lineHeight = 10.sp,
                color = colors.textTertiary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 4.dp)
            )
        }
    }
}

private sealed interface KeypadAction {
    data class Digit(val digit: Int) : KeypadAction
    data object Clear : KeypadAction
}
