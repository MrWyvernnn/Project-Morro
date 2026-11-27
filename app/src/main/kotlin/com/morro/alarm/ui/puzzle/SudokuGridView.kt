package com.morro.alarm.ui.puzzle

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.puzzle.sudoku.SudokuGenerator
import com.morro.alarm.puzzle.sudoku.SudokuGrid
import com.morro.alarm.puzzle.sudoku.SudokuMode
import com.morro.alarm.puzzle.sudoku.SudokuPuzzle
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroTypography
import kotlinx.coroutines.launch
import kotlin.math.min

// Exact Easybrain Sudoku.com Dark Mode tokens
private val SudokuDarkBg = Color(0xFF161B26)
private val SudokuDarkHeaderBg = Color(0xFF1A202D)
private val SudokuCrosshairBg = Color(0xFF21293A)
private val SudokuSameDigitBg = Color(0xFF293D5E)
private val SudokuSelectedBg = Color(0xFF334E7A)
private val SudokuSelectedRing = Color(0xFF5B9BFF)
private val SudokuConflictBg = Color(0xFF3E1E26)
private val SudokuSubLine = Color(0xFF2B3448)
private val SudokuBoxLine = Color(0xFF586784)
private val SudokuGivenInk = Color(0xFFF3F4F6)
private val SudokuUserInk = Color(0xFF68A8FF)
private val SudokuNoteInk = Color(0xFF94A3B8)
private val SudokuConflictInk = Color(0xFFF87171)
private val SudokuMutedInk = Color(0xFF9CA3AF)
private val SudokuToolSurface = Color(0xFF222A3A)

private data class SudokuSnapshot(
    val grid: SudokuGrid,
    val notes: Map<Pair<Int, Int>, Set<Int>>,
)

private fun sudokuGridSaver(template: SudokuGrid): Saver<SudokuGrid, IntArray> = Saver(
    save = { grid ->
        IntArray(grid.size * grid.size) { idx ->
            grid[idx / grid.size, idx % grid.size]
        }
    },
    restore = { flat ->
        val n = template.size
        if (flat.size != n * n) {
            template
        } else {
            val cells = Array(n) { r -> IntArray(n) { c -> flat[r * n + c].coerceIn(0, n) } }
            SudokuGrid(n, template.boxRows, template.boxCols, cells)
        }
    }
)

private fun notesMapSaver(gridSize: Int): Saver<Map<Pair<Int, Int>, Set<Int>>, IntArray> = Saver(
    save = { map ->
        val masks = IntArray(gridSize * gridSize)
        map.forEach { (rc, digits) ->
            val idx = rc.first * gridSize + rc.second
            if (idx in masks.indices) {
                var mask = 0
                digits.forEach { d -> if (d in 1..gridSize) mask = mask or (1 shl d) }
                masks[idx] = mask
            }
        }
        masks
    },
    restore = { masks ->
        if (masks.size != gridSize * gridSize) {
            emptyMap()
        } else {
            buildMap {
                for (r in 0 until gridSize) {
                    for (c in 0 until gridSize) {
                        val mask = masks[r * gridSize + c]
                        if (mask != 0) {
                            val set = (1..gridSize).filter { (mask and (1 shl it)) != 0 }.toSet()
                            if (set.isNotEmpty()) put(r to c, set)
                        }
                    }
                }
            }
        }
    }
)

private val NullablePairSaver: Saver<Pair<Int, Int>?, IntArray> = Saver(
    save = { pair -> if (pair == null) intArrayOf() else intArrayOf(pair.first, pair.second) },
    restore = { arr -> if (arr.size == 2) arr[0] to arr[1] else null }
)

/**
 * Canvas-rendered Sudoku grid modeled faithfully on Easybrain's Sudoku.com Dark Mode:
 * - Deep charcoal-navy grid canvas (`#161B26`) with crisp `1.dp` cell hairlines and `2.dp` box boundaries.
 * - Full Sudoku.com highlighting: row/column/box crosshair (`#21293A`), matching-digit highlight
 *   across the board (`#293D5E`), royal-blue active cell (`#334E7A` + `#5B9BFF` stroke), and soft
 *   crimson conflict shading (`#3E1E26` / `#F87171`).
 * - Crisp `#F3F4F6` given numbers, `#68A8FF` user entries, and `2×2` / `3×3` pencil candidate marks.
 */
@Composable
fun SudokuGridView(
    initialGrid: SudokuGrid,
    currentGrid: SudokuGrid,
    selectedCell: Pair<Int, Int>?,
    conflicts: Set<Pair<Int, Int>>,
    onCellSelected: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier,
    notes: Map<Pair<Int, Int>, Set<Int>> = emptyMap(),
    lastEnteredCell: Pair<Int, Int>? = null,
    entryPopScale: Float = 1f,
    onInspectGivenCell: ((row: Int, col: Int) -> Unit)? = null,
) {
    val typo = morroTypography()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 48)
    val currentOnCellSelected by rememberUpdatedState(onCellSelected)
    val currentOnInspectGiven by rememberUpdatedState(onInspectGivenCell)

    val subtleStrokePx = with(density) { 1.dp.toPx() }
    val strongStrokePx = with(density) { 2.dp.toPx() }
    val expandPx = with(density) { 8.dp.toPx() }

    val gridSize = initialGrid.size
    val boxRows = initialGrid.boxRows
    val boxCols = initialGrid.boxCols

    val selectedDigit = selectedCell?.let { (r, c) ->
        if (r in 0 until gridSize && c in 0 until gridSize) currentGrid[r, c] else 0
    } ?: 0

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(SudokuDarkBg)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(initialGrid, expandPx, gridSize) {
                    detectTapGestures { offset ->
                        val emptyTarget = resolveNearestEmptyCell(
                            tapX = offset.x,
                            tapY = offset.y,
                            gridWidthPx = size.width.toFloat(),
                            gridHeightPx = size.height.toFloat(),
                            initialGrid = initialGrid,
                            expandPx = expandPx
                        )
                        if (emptyTarget != null) {
                            currentOnCellSelected(emptyTarget.first, emptyTarget.second)
                        } else if (currentOnInspectGiven != null) {
                            val col = ((offset.x / size.width.toFloat()) * gridSize).toInt().coerceIn(0, gridSize - 1)
                            val row = ((offset.y / size.height.toFloat()) * gridSize).toInt().coerceIn(0, gridSize - 1)
                            currentOnInspectGiven?.invoke(row, col)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val cellWidth = width / gridSize
            val cellHeight = height / gridSize

            // 1. Crosshair (row, col, box) & matching digit highlight
            selectedCell?.let { (sr, sc) ->
                if (sr in 0 until gridSize && sc in 0 until gridSize) {
                    val sBoxR = sr / boxRows
                    val sBoxC = sc / boxCols
                    for (r in 0 until gridSize) {
                        for (c in 0 until gridSize) {
                            val inCrosshair = (r == sr || c == sc || (r / boxRows == sBoxR && c / boxCols == sBoxC))
                            val sameDigit = selectedDigit != 0 && currentGrid[r, c] == selectedDigit && (r to c) != selectedCell
                            if (sameDigit) {
                                drawRect(
                                    color = SudokuSameDigitBg,
                                    topLeft = Offset(c * cellWidth, r * cellHeight),
                                    size = Size(cellWidth, cellHeight)
                                )
                            } else if (inCrosshair && (r to c) != selectedCell) {
                                drawRect(
                                    color = SudokuCrosshairBg,
                                    topLeft = Offset(c * cellWidth, r * cellHeight),
                                    size = Size(cellWidth, cellHeight)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Conflict shading on incorrect user digits
            for ((r, c) in conflicts) {
                if (r in 0 until gridSize && c in 0 until gridSize) {
                    drawRect(
                        color = SudokuConflictBg,
                        topLeft = Offset(c * cellWidth, r * cellHeight),
                        size = Size(cellWidth, cellHeight)
                    )
                }
            }

            // 3. Selected cell royal-blue focus fill + crisp border ring
            selectedCell?.let { (r, c) ->
                if (r in 0 until gridSize && c in 0 until gridSize) {
                    if ((r to c) !in conflicts) {
                        drawRect(
                            color = SudokuSelectedBg,
                            topLeft = Offset(c * cellWidth, r * cellHeight),
                            size = Size(cellWidth, cellHeight)
                        )
                    }
                    val activeStroke = strongStrokePx * 0.95f
                    val half = activeStroke / 2f
                    drawRect(
                        color = if ((r to c) in conflicts) SudokuConflictInk else SudokuSelectedRing,
                        topLeft = Offset(c * cellWidth + half, r * cellHeight + half),
                        size = Size(cellWidth - activeStroke, cellHeight - activeStroke),
                        style = Stroke(width = activeStroke)
                    )
                }
            }

            // 4. Inner cell thin hairlines
            for (c in 1 until gridSize) {
                if (c % boxCols != 0) {
                    val x = c * cellWidth
                    drawLine(
                        color = SudokuSubLine,
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = subtleStrokePx
                    )
                }
            }
            for (r in 1 until gridSize) {
                if (r % boxRows != 0) {
                    val y = r * cellHeight
                    drawLine(
                        color = SudokuSubLine,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = subtleStrokePx
                    )
                }
            }

            // 5. Bold sub-grid box boundaries & outer perimeter
            for (c in boxCols until gridSize step boxCols) {
                val x = c * cellWidth
                drawLine(
                    color = SudokuBoxLine,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = strongStrokePx
                )
            }
            for (r in boxRows until gridSize step boxRows) {
                val y = r * cellHeight
                drawLine(
                    color = SudokuBoxLine,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = strongStrokePx
                )
            }
            val halfStrong = strongStrokePx / 2f
            drawRect(
                color = SudokuBoxLine,
                topLeft = Offset(halfStrong, halfStrong),
                size = Size(width - strongStrokePx, height - strongStrokePx),
                style = Stroke(width = strongStrokePx)
            )

            // 6. Cell digits & Pencil Notes mini candidate marks
            val targetFontSp = with(density) { (min(cellWidth, cellHeight) * 0.56f).toSp() }
            val cellFontSp = minOf(targetFontSp.value, typo.headlineMedium.fontSize.value).sp
            val digitStyle = typo.headlineMedium.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = cellFontSp,
                lineHeight = cellFontSp,
                textAlign = TextAlign.Center
            )
            val noteFontSp = with(density) { (min(cellWidth / boxCols, cellHeight / boxRows) * 0.64f).toSp() }
            val noteStyle = typo.labelLarge.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = noteFontSp.value.coerceIn(7f, 13f).sp,
                lineHeight = noteFontSp.value.coerceIn(7f, 13f).sp,
                textAlign = TextAlign.Center
            )

            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    val value = currentGrid[r, c]
                    if (value != 0) {
                        val isGiven = initialGrid[r, c] != 0
                        val textColor = when {
                            isGiven -> SudokuGivenInk
                            (r to c) in conflicts -> SudokuConflictInk
                            else -> SudokuUserInk
                        }
                        val layoutResult = textMeasurer.measure(
                            text = value.toString(),
                            style = digitStyle.copy(
                                color = textColor,
                                fontWeight = if (isGiven) FontWeight.SemiBold else FontWeight.Medium
                            )
                        )
                        val x = c * cellWidth + (cellWidth - layoutResult.size.width) / 2f
                        val y = r * cellHeight + (cellHeight - layoutResult.size.height) / 2f
                        val cellScale = if ((r to c) == lastEnteredCell) entryPopScale else 1f
                        if (cellScale != 1f) {
                            val cellCenter = Offset(c * cellWidth + cellWidth / 2f, r * cellHeight + cellHeight / 2f)
                            scale(scale = cellScale, pivot = cellCenter) {
                                drawText(textLayoutResult = layoutResult, topLeft = Offset(x, y))
                            }
                        } else {
                            drawText(textLayoutResult = layoutResult, topLeft = Offset(x, y))
                        }
                    } else {
                        val cellNotes = notes[r to c].orEmpty()
                        if (cellNotes.isNotEmpty()) {
                            val subW = cellWidth / boxCols
                            val subH = cellHeight / boxRows
                            for (digit in cellNotes) {
                                val idx = digit - 1
                                val sr = idx / boxCols
                                val sc = idx % boxCols
                                val isMatchingSelected = (selectedDigit == digit)
                                val noteLayout = textMeasurer.measure(
                                    text = digit.toString(),
                                    style = noteStyle.copy(
                                        color = if (isMatchingSelected) SudokuSelectedRing else SudokuNoteInk,
                                        fontWeight = if (isMatchingSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                                val nx = c * cellWidth + sc * subW + (subW - noteLayout.size.width) / 2f
                                val ny = r * cellHeight + sr * subH + (subH - noteLayout.size.height) / 2f
                                drawText(textLayoutResult = noteLayout, topLeft = Offset(nx, ny))
                            }
                        }
                    }
                }
            }
        }

        // TalkBack & Switch-Access semantic grid overlay (non-clickable so Canvas tap snapping remains intact)
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until gridSize) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    for (c in 0 until gridSize) {
                        val isGiven = initialGrid[r, c] != 0
                        val value = currentGrid[r, c]
                        val isSelected = selectedCell == (r to c)
                        val isConflict = (r to c) in conflicts

                        val desc = buildString {
                            append("Row ${r + 1}, Column ${c + 1}, ")
                            if (isGiven) {
                                append("given $value")
                            } else if (value == 0) {
                                append("empty")
                            } else {
                                append("entered $value")
                                if (isConflict) append(", conflict")
                            }
                            if (isSelected) append(", selected")
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .focusable(enabled = !isGiven)
                                .clearAndSetSemantics {
                                    contentDescription = desc
                                    selected = isSelected
                                    if (!isGiven) {
                                        role = Role.Button
                                        onClick(label = "Select cell") {
                                            currentOnCellSelected(r, c)
                                            true
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Overload taking a [SudokuPuzzle].
 */
@Composable
fun SudokuGridView(
    puzzle: SudokuPuzzle,
    currentGrid: SudokuGrid,
    selectedCell: Pair<Int, Int>?,
    conflicts: Set<Pair<Int, Int>>,
    onCellSelected: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier
) = SudokuGridView(
    initialGrid = puzzle.grid,
    currentGrid = currentGrid,
    selectedCell = selectedCell,
    conflicts = conflicts,
    onCellSelected = onCellSelected,
    modifier = modifier
)

/**
 * Full-screen Sudoku gate matching Easybrain's Sudoku.com Dark Mode layout:
 * - Header bar with Difficulty, Mistakes counter, and Remaining count.
 * - Crisp dark slate 4×4 / 9×9 board with row/col/box crosshair + matching digit highlights.
 * - Horizontal Action Toolbar (`Undo`, `Erase`, `Notes` toggle with auto-clearing pencil marks).
 * - Clean single-row number strip (`1..4` or `1..9`) with completion fade.
 */
@Composable
fun SudokuGateScreen(
    onGateSolved: () -> Unit,
    modifier: Modifier = Modifier,
    puzzle: SudokuPuzzle? = null,
    mode: SudokuMode = SudokuMode.STANDARD,
    seed: Long = rememberSaveable { System.currentTimeMillis() },
    onUserAction: () -> Unit = {},
    onGenerationFailed: () -> Unit = {}
) {
    val typo = morroTypography()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val currentOnGateSolved by rememberUpdatedState(onGateSolved)
    val currentOnUserAction by rememberUpdatedState(onUserAction)
    val currentOnGenerationFailed by rememberUpdatedState(onGenerationFailed)

    val activePuzzleResult = remember(puzzle, seed, mode) {
        runCatching { puzzle ?: SudokuGenerator.generate(seed, mode) }
    }
    val activePuzzle = activePuzzleResult.getOrNull()

    if (activePuzzle == null) {
        LaunchedEffect(activePuzzleResult.exceptionOrNull()) {
            currentOnGenerationFailed()
        }
        return
    }

    val initialGrid = activePuzzle.grid
    var currentGrid by rememberSaveable(activePuzzle, stateSaver = sudokuGridSaver(initialGrid)) {
        mutableStateOf(initialGrid)
    }
    var notesMap by rememberSaveable(activePuzzle, stateSaver = notesMapSaver(initialGrid.size)) {
        mutableStateOf(emptyMap())
    }
    var selectedCell by rememberSaveable(activePuzzle, stateSaver = NullablePairSaver) {
        mutableStateOf(initialGrid.emptyCells().firstOrNull())
    }
    var notesMode by rememberSaveable(activePuzzle) { mutableStateOf(false) }
    var mistakesCount by rememberSaveable(activePuzzle) { mutableIntStateOf(0) }
    var isSolved by rememberSaveable(activePuzzle) { mutableStateOf(false) }

    var undoStack by remember(activePuzzle) { mutableStateOf<List<SudokuSnapshot>>(emptyList()) }
    var lastEnteredCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val entryPop = remember { Animatable(1f) }
    val gridShakeOffset = remember { Animatable(0f) }

    val conflicts by remember(currentGrid, activePuzzle) {
        derivedStateOf { computeConflicts(activePuzzle, currentGrid) }
    }

    val remainingEmptyCount by remember(currentGrid) {
        derivedStateOf { currentGrid.emptyCells().size }
    }
    val completedDigits by remember(currentGrid, conflicts) {
        derivedStateOf {
            val n = currentGrid.size
            val counts = IntArray(n + 1)
            for (r in 0 until n) {
                for (c in 0 until n) {
                    val v = currentGrid[r, c]
                    if (v in 1..n && (r to c) !in conflicts) {
                        counts[v]++
                    }
                }
            }
            (1..n).filter { counts[it] >= n }.toSet()
        }
    }

    LaunchedEffect(currentGrid, activePuzzle, isSolved) {
        if (!isSolved && (currentGrid == activePuzzle.solution || SudokuGrid.isSolved(currentGrid))) {
            isSolved = true
            selectedCell = null
            currentOnGateSolved()
        }
    }

    val onDigitClick: (Int) -> Unit = { digit ->
        currentOnUserAction()
        if (!isSolved) {
            selectedCell?.let { (r, c) ->
                if (initialGrid[r, c] == 0) {
                    if (notesMode) {
                        undoStack = (undoStack + SudokuSnapshot(currentGrid, notesMap)).takeLast(40)
                        val existing = notesMap[r to c].orEmpty()
                        val updated = if (digit in existing) existing - digit else existing + digit
                        val nextNotes = notesMap.toMutableMap()
                        if (updated.isEmpty()) nextNotes.remove(r to c) else nextNotes[r to c] = updated
                        // Clear any conflicting digit so pencil notes show visibly in the cell
                        if (currentGrid[r, c] != 0) {
                            currentGrid = currentGrid.set(r, c, 0)
                        }
                        notesMap = nextNotes
                    } else {
                        undoStack = (undoStack + SudokuSnapshot(currentGrid, notesMap)).takeLast(40)
                        val newGrid = currentGrid.set(r, c, digit)
                        currentGrid = newGrid
                        notesMap = clearPeerNotesForEntry(
                            notes = notesMap,
                            row = r,
                            col = c,
                            digit = digit,
                            boxRows = initialGrid.boxRows,
                            boxCols = initialGrid.boxCols
                        )
                        lastEnteredCell = r to c
                        val isCorrect = (digit == activePuzzle.solution[r, c])
                        if (!reducedMotion) {
                            scope.launch {
                                entryPop.snapTo(0.74f)
                                entryPop.animateTo(1f, MorroSpring.FloatSpringPlayful)
                            }
                        }
                        if (!isCorrect) {
                            mistakesCount++
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!reducedMotion) {
                                scope.launch {
                                    gridShakeOffset.snapTo(0f)
                                    for (dpOff in MorroMotion.WrongShakeOffsetsDp) {
                                        gridShakeOffset.animateTo(dpOff, tween(MorroMotion.ShakeStepMillis))
                                    }
                                }
                            }
                        } else {
                            selectedCell = nextTargetCellAfterEntry(activePuzzle, newGrid, r, c)
                        }
                    }
                }
            }
        }
    }

    val onEraseClick: () -> Unit = {
        currentOnUserAction()
        if (!isSolved) {
            selectedCell?.let { (r, c) ->
                if (initialGrid[r, c] == 0 && (currentGrid[r, c] != 0 || !notesMap[r to c].isNullOrEmpty())) {
                    undoStack = (undoStack + SudokuSnapshot(currentGrid, notesMap)).takeLast(40)
                    currentGrid = currentGrid.set(r, c, 0)
                    if (!notesMap[r to c].isNullOrEmpty()) {
                        notesMap = notesMap - (r to c)
                    }
                }
            }
        }
    }

    val onUndoClick: () -> Unit = {
        currentOnUserAction()
        if (!isSolved && undoStack.isNotEmpty()) {
            val prev = undoStack.last()
            undoStack = undoStack.dropLast(1)
            currentGrid = prev.grid
            notesMap = prev.notes
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Sudoku.com Dark Mode Top Stats Strip (Difficulty · Mistakes · Remaining)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SudokuDarkHeaderBg)
                .morroHairline(radius = 12.dp, color = SudokuSubLine)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (activePuzzle.mode == SudokuMode.STANDARD) "Quick 4×4" else "Classic 9×9",
                style = typo.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = SudokuGivenInk
            )

            Text(
                text = "Mistakes: $mistakesCount",
                style = typo.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
                color = if (mistakesCount > 0) SudokuConflictInk else SudokuMutedInk
            )

            AnimatedContent(
                targetState = remainingEmptyCount,
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (fadeIn(tween(150)) + slideInVertically { it / 2 })
                            .togetherWith(fadeOut(tween(100)))
                    }
                },
                label = "sudokuRemaining"
            ) { rem ->
                Text(
                    text = if (rem == 0) "Solved" else "$rem left",
                    style = typo.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    color = SudokuUserInk
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Board Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = with(density) { gridShakeOffset.value.dp.toPx() }
                }
                .clip(RoundedCornerShape(10.dp))
                .background(SudokuDarkBg)
                .morroHairline(radius = 10.dp, color = SudokuBoxLine)
                .padding(2.dp)
        ) {
            SudokuGridView(
                initialGrid = initialGrid,
                currentGrid = currentGrid,
                selectedCell = selectedCell,
                conflicts = conflicts,
                notes = notesMap,
                onCellSelected = { r, c ->
                    currentOnUserAction()
                    if (!isSolved) selectedCell = r to c
                },
                onInspectGivenCell = { r, c ->
                    currentOnUserAction()
                    if (!isSolved) selectedCell = r to c
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                lastEnteredCell = lastEnteredCell,
                entryPopScale = entryPop.value
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Sudoku.com Action Toolbar: Undo · Erase · Notes (no Cheat Hint in alarm gate)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SudokuToolActionButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = "Undo",
                enabled = !isSolved && undoStack.isNotEmpty(),
                onClick = onUndoClick
            )
            SudokuToolActionButton(
                icon = Icons.Filled.Backspace,
                label = "Erase",
                enabled = !isSolved && selectedCell != null,
                onClick = onEraseClick
            )
            SudokuToolActionButton(
                icon = Icons.Filled.Edit,
                label = "Notes",
                badgeText = if (notesMode) "ON" else "OFF",
                active = notesMode,
                enabled = !isSolved,
                onClick = {
                    currentOnUserAction()
                    notesMode = !notesMode
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SudokuKeypad(
            mode = activePuzzle.mode,
            onDigitClick = onDigitClick,
            onClearClick = onEraseClick,
            enabled = !isSolved,
            completedDigits = completedDigits,
            notesMode = notesMode,
            includeClearKey = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SudokuToolActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    badgeText: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val circleBg by animateColorAsState(
        targetValue = when {
            active -> SudokuSelectedBg
            enabled -> SudokuToolSurface
            else -> SudokuToolSurface.copy(alpha = 0.45f)
        },
        animationSpec = tween(160),
        label = "sudokuToolBg"
    )
    val contentTint = when {
        active -> SudokuUserInk
        enabled -> SudokuGivenInk
        else -> SudokuMutedInk.copy(alpha = 0.45f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
            .pressScale(interaction, enabled = enabled, releaseSpec = MorroSpring.FloatSpringPlayful)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(circleBg)
                    .morroHairline(
                        radius = 22.dp,
                        color = if (active) SudokuSelectedRing else SudokuSubLine
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (badgeText != null) {
                Text(
                    text = badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color.White else SudokuMutedInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) SudokuSelectedRing else SudokuDarkBg)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = contentTint
        )
    }
}

/**
 * Clears conflicting pencil candidate [digit] from the same cell, row, column, and sub-box.
 */
internal fun clearPeerNotesForEntry(
    notes: Map<Pair<Int, Int>, Set<Int>>,
    row: Int,
    col: Int,
    digit: Int,
    boxRows: Int,
    boxCols: Int,
): Map<Pair<Int, Int>, Set<Int>> {
    if (notes.isEmpty()) return notes
    val boxR = row / boxRows
    val boxC = col / boxCols
    return buildMap {
        notes.forEach { (cell, set) ->
            val (r, c) = cell
            if (r == row && c == col) {
                // Cell now holds a definite digit
                return@forEach
            }
            val samePeer = (r == row || c == col || (r / boxRows == boxR && c / boxCols == boxC))
            val updated = if (samePeer) set - digit else set
            if (updated.isNotEmpty()) put(cell, updated)
        }
    }
}

/**
 * Picks the next target cell after a correct entry at ([row], [col]):
 * 1. Next empty cell in row-major order wrapping around.
 * 2. If all cells are filled, next conflicting user cell (wrapping around), or null if solved.
 */
internal fun nextTargetCellAfterEntry(
    puzzle: SudokuPuzzle,
    currentGrid: SudokuGrid,
    row: Int,
    col: Int
): Pair<Int, Int>? {
    val n = currentGrid.size
    val startIdx = row * n + col
    val empty = currentGrid.emptyCells()
    if (empty.isNotEmpty()) {
        return empty.firstOrNull { (r, c) -> r * n + c > startIdx } ?: empty.first()
    }
    val remainingConflicts = computeConflicts(puzzle, currentGrid)
    if (remainingConflicts.isNotEmpty()) {
        return remainingConflicts.firstOrNull { (r, c) -> r * n + c > startIdx }
            ?: remainingConflicts.minByOrNull { (r, c) -> r * n + c }
    }
    return null
}

/**
 * Evaluates touch coordinates [tapX], [tapY] against the grid.
 * ONLY empty cells (cells that were not givens) are tappable. Givens are inert.
 * Expanded hit region: cell bounds + [expandPx] on each side, resolved to nearest empty cell.
 */
fun resolveNearestEmptyCell(
    tapX: Float,
    tapY: Float,
    gridSizePx: Float,
    initialGrid: SudokuGrid,
    expandPx: Float
): Pair<Int, Int>? = resolveNearestEmptyCell(
    tapX = tapX,
    tapY = tapY,
    gridWidthPx = gridSizePx,
    gridHeightPx = gridSizePx,
    initialGrid = initialGrid,
    expandPx = expandPx
)

fun resolveNearestEmptyCell(
    tapX: Float,
    tapY: Float,
    gridWidthPx: Float,
    gridHeightPx: Float,
    initialGrid: SudokuGrid,
    expandPx: Float
): Pair<Int, Int>? {
    val size = initialGrid.size
    if (size <= 0 || gridWidthPx <= 0f || gridHeightPx <= 0f) return null
    val cellWidth = gridWidthPx / size
    val cellHeight = gridHeightPx / size

    var nearestCell: Pair<Int, Int>? = null
    var minDistanceSq = Float.MAX_VALUE

    for (r in 0 until size) {
        for (c in 0 until size) {
            if (initialGrid[r, c] == 0) {
                val left = c * cellWidth
                val top = r * cellHeight
                val right = left + cellWidth
                val bottom = top + cellHeight

                if (tapX in (left - expandPx)..(right + expandPx) &&
                    tapY in (top - expandPx)..(bottom + expandPx)
                ) {
                    val centerX = (left + right) / 2f
                    val centerY = (top + bottom) / 2f
                    val dx = tapX - centerX
                    val dy = tapY - centerY
                    val distSq = dx * dx + dy * dy
                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq
                        nearestCell = r to c
                    }
                }
            }
        }
    }
    return nearestCell
}

/**
 * Computes conflicting user cells:
 * Flags only non-given cells with non-zero digits that differ from [SudokuPuzzle.solution].
 * Givens and correct user entries are never shaded as errors.
 */
fun computeConflicts(
    puzzle: SudokuPuzzle,
    currentGrid: SudokuGrid
): Set<Pair<Int, Int>> {
    val conflicts = mutableSetOf<Pair<Int, Int>>()
    val size = puzzle.grid.size

    for (r in 0 until size) {
        for (c in 0 until size) {
            val userVal = currentGrid[r, c]
            val isGiven = puzzle.grid[r, c] != 0
            if (!isGiven && userVal != 0 && userVal != puzzle.solution[r, c]) {
                conflicts.add(r to c)
            }
        }
    }
    return conflicts
}
