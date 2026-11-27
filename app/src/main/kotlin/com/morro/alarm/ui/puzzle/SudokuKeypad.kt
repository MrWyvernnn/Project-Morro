package com.morro.alarm.ui.puzzle

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroTypography

private val KeypadDarkSurface = Color(0xFF1F2634)
private val KeypadCompletedSurface = Color(0xFF141822)
private val KeypadDigitBlue = Color(0xFF68A8FF)
private val KeypadNoteSlate = Color(0xFF94A3B8)
private val KeypadBorder = Color(0xFF2D374B)

/**
 * Sudoku.com Dark Mode bottom keypad strip.
 *
 * When [includeClearKey] is false (default for Sudoku.com mode, where Erase sits in the toolbar above),
 * digits render in one crisp horizontal bar (`1..4` or `1..9`). Passing [includeClearKey] = true
 * preserves legacy standalone Erase key callers cleanly.
 */
@Composable
fun SudokuKeypad(
    maxDigit: Int,
    onDigitClick: (Int) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    completedDigits: Set<Int> = emptySet(),
    notesMode: Boolean = false,
    includeClearKey: Boolean = false,
) {
    require(maxDigit == 4 || maxDigit == 9) { "SudokuKeypad only supports maxDigit 4 or 9 (got $maxDigit)" }

    val items: List<List<KeypadAction>> = if (includeClearKey) {
        when (maxDigit) {
            4 -> listOf(
                listOf(1, 2, 3, 4).map { KeypadAction.Digit(it) } + KeypadAction.Clear
            )
            else -> listOf(
                listOf(1, 2, 3, 4, 5).map { KeypadAction.Digit(it) },
                listOf(6, 7, 8, 9).map { KeypadAction.Digit(it) } + KeypadAction.Clear
            )
        }
    } else {
        listOf((1..maxDigit).map { KeypadAction.Digit(it) })
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in items) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (maxDigit == 9 && !includeClearKey) 5.dp else 8.dp)
            ) {
                for (action in row) {
                    when (action) {
                        is KeypadAction.Digit -> {
                            SudokuKeypadButton(
                                text = action.digit.toString(),
                                onClick = { onDigitClick(action.digit) },
                                modifier = Modifier.weight(1f),
                                enabled = enabled && (notesMode || action.digit !in completedDigits),
                                isAction = false,
                                isCompleted = !notesMode && (action.digit in completedDigits),
                                notesMode = notesMode
                            )
                        }
                        is KeypadAction.Clear -> {
                            SudokuKeypadButton(
                                text = "Erase",
                                onClick = onClearClick,
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                                isAction = true,
                                isCompleted = false,
                                notesMode = notesMode
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
    enabled: Boolean = true,
    completedDigits: Set<Int> = emptySet(),
    notesMode: Boolean = false,
    includeClearKey: Boolean = false,
) = SudokuKeypad(
    maxDigit = if (mode == SudokuMode.STANDARD) 4 else 9,
    onDigitClick = onDigitClick,
    onClearClick = onClearClick,
    modifier = modifier,
    enabled = enabled,
    completedDigits = completedDigits,
    notesMode = notesMode,
    includeClearKey = includeClearKey,
)

@Composable
private fun SudokuKeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isAction: Boolean = false,
    isCompleted: Boolean = false,
    notesMode: Boolean = false,
) {
    val typo = morroTypography()
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val contentAlpha by animateFloatAsState(
        targetValue = if (isCompleted) 0.25f else 1f,
        animationSpec = tween(180),
        label = "keyAlpha"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isCompleted) KeypadCompletedSurface else KeypadDarkSurface,
        animationSpec = tween(180),
        label = "keyBg"
    )
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .height(58.dp)
            .pressScale(
                interactionSource = interactionSource,
                enabled = enabled,
                releaseSpec = MorroSpring.FloatSpringPlayful
            )
            .clip(shape)
            .background(bgColor)
            .morroHairline(
                radius = 10.dp,
                color = if (isCompleted) KeypadCompletedSurface else KeypadBorder
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
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = if (isAction) {
                typo.labelLarge.copy(fontWeight = FontWeight.Medium)
            } else {
                typo.headlineMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = if (notesMode) FontWeight.Medium else FontWeight.SemiBold,
                    fontSize = if (notesMode) 20.sp else 24.sp
                )
            },
            color = when {
                isAction -> Color(0xFF9CA3AF)
                notesMode -> KeypadNoteSlate
                else -> KeypadDigitBlue
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = contentAlpha }
        )
    }
}

private sealed interface KeypadAction {
    data class Digit(val digit: Int) : KeypadAction
    data object Clear : KeypadAction
}
