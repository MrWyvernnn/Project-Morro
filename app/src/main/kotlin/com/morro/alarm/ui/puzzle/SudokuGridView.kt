package com.morro.alarm.ui.puzzle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.morro.alarm.puzzle.sudoku.SudokuGenerator
import com.morro.alarm.puzzle.sudoku.SudokuGrid
import com.morro.alarm.puzzle.sudoku.SudokuMode
import com.morro.alarm.puzzle.sudoku.SudokuPuzzle
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography
import kotlin.math.min

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

private val NullablePairSaver: Saver<Pair<Int, Int>?, IntArray> = Saver(
    save = { pair -> if (pair == null) intArrayOf() else intArrayOf(pair.first, pair.second) },
    restore = { arr -> if (arr.size == 2) arr[0] to arr[1] else null }
)

/**
 * Canvas-rendered Sudoku grid strictly adhering to Morro design system rules:
 * - Canvas grid: cell lines borderSubtle, box boundaries borderStrong at 1.5.dp
 * - Givens: textPrimary, inert
 * - User entries: honey, scaled cleanly to cell size
 * - Conflict state: error at 16% background on wrong user entries only
 * - Expanded hit region: cell bounds + 8dp on each side, resolved to nearest empty cell
 * - Selected cell indicator: borderActive hairline + honey at 12% fill (omitted on error cells)
 * - Full TalkBack & Switch-Access support without intercepting Canvas touch snapping
 */
@Composable
fun SudokuGridView(
    initialGrid: SudokuGrid,
    currentGrid: SudokuGrid,
    selectedCell: Pair<Int, Int>?,
    conflicts: Set<Pair<Int, Int>>,
    onCellSelected: (row: Int, col: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = morroColors()
    val typo = morroTypography()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 24)
    val currentOnCellSelected by rememberUpdatedState(onCellSelected)

    val subtleStrokePx = with(density) { 1.dp.toPx() }
    val strongStrokePx = with(density) { 1.5.dp.toPx() }
    val expandPx = with(density) { 8.dp.toPx() }

    val gridSize = initialGrid.size
    val boxRows = initialGrid.boxRows
    val boxCols = initialGrid.boxCols

    Box(
        modifier = modifier.aspectRatio(1f)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(initialGrid, expandPx) {
                    detectTapGestures { offset ->
                        val cell = resolveNearestEmptyCell(
                            tapX = offset.x,
                            tapY = offset.y,
                            gridWidthPx = size.width.toFloat(),
                            gridHeightPx = size.height.toFloat(),
                            initialGrid = initialGrid,
                            expandPx = expandPx
                        )
                        cell?.let { (r, c) ->
                            currentOnCellSelected(r, c)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val cellWidth = width / gridSize
            val cellHeight = height / gridSize

            // 1. Conflict cell backgrounds: error at 16% alpha
            for ((r, c) in conflicts) {
                if (r in 0 until gridSize && c in 0 until gridSize) {
                    drawRect(
                        color = colors.error.copy(alpha = 0.16f),
                        topLeft = Offset(c * cellWidth, r * cellHeight),
                        size = Size(cellWidth, cellHeight)
                    )
                }
            }

            // 2. Selected cell: honey at 12% fill (unless already shaded in conflict) + borderActive hairline
            selectedCell?.let { (r, c) ->
                if (r in 0 until gridSize && c in 0 until gridSize) {
                    if ((r to c) !in conflicts) {
                        drawRect(
                            color = colors.honey.copy(alpha = 0.12f),
                            topLeft = Offset(c * cellWidth, r * cellHeight),
                            size = Size(cellWidth, cellHeight)
                        )
                    }
                    val halfSubtle = subtleStrokePx / 2f
                    drawRect(
                        color = colors.borderActive,
                        topLeft = Offset(c * cellWidth + halfSubtle, r * cellHeight + halfSubtle),
                        size = Size(cellWidth - subtleStrokePx, cellHeight - subtleStrokePx),
                        style = Stroke(width = subtleStrokePx)
                    )
                }
            }

            // 3. Subtle grid lines between cells inside boxes
            for (c in 1 until gridSize) {
                if (c % boxCols != 0) {
                    val x = c * cellWidth
                    drawLine(
                        color = colors.borderSubtle,
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
                        color = colors.borderSubtle,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = subtleStrokePx
                    )
                }
            }

            // 4. Box boundary lines: borderStrong at 1.5dp
            for (c in boxCols until gridSize step boxCols) {
                val x = c * cellWidth
                drawLine(
                    color = colors.borderStrong,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = strongStrokePx
                )
            }
            for (r in boxRows until gridSize step boxRows) {
                val y = r * cellHeight
                drawLine(
                    color = colors.borderStrong,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = strongStrokePx
                )
            }

            // Outer frame hairline
            val halfStrong = strongStrokePx / 2f
            drawRect(
                color = colors.borderStrong,
                topLeft = Offset(halfStrong, halfStrong),
                size = Size(width - strongStrokePx, height - strongStrokePx),
                style = Stroke(width = strongStrokePx)
            )

            // 5. Cell digits scaled safely to cell size so large fontScale never clips
            val targetFontSp = with(density) { (min(cellWidth, cellHeight) * 0.54f).toSp() }
            val cellFontSp = if (targetFontSp.value < typo.headlineMedium.fontSize.value) {
                targetFontSp
            } else {
                typo.headlineMedium.fontSize
            }
            val digitStyle = typo.headlineMedium.copy(
                fontSize = cellFontSp,
                lineHeight = cellFontSp,
                textAlign = TextAlign.Center
            )

            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    val value = currentGrid[r, c]
                    if (value != 0) {
                        val isGiven = initialGrid[r, c] != 0
                        val textColor = if (isGiven) colors.textPrimary else colors.honey
                        val layoutResult = textMeasurer.measure(
                            text = value.toString(),
                            style = digitStyle.copy(color = textColor)
                        )
                        val x = c * cellWidth + (cellWidth - layoutResult.size.width) / 2f
                        val y = r * cellHeight + (cellHeight - layoutResult.size.height) / 2f
                        drawText(
                            textLayoutResult = layoutResult,
                            topLeft = Offset(x, y)
                        )
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
 * Full-screen Sudoku gate composable integrating [SudokuGridView] and [SudokuKeypad].
 *
 * Implements the behavioral contracts:
 * - Automatically calls [onGateSolved] once the instant the final correct digit is placed.
 * - Fires [onUserAction] on every cell tap, digit tap, and clear tap (for audio ducking).
 * - On wrong digit: marks only that user cell in conflict and triggers haptic feedback.
 * - Preserves board entries and selection across rotation via rememberSaveable.
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
    val colors = morroColors()
    val typo = morroTypography()
    val haptics = LocalHapticFeedback.current
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
    var selectedCell by rememberSaveable(activePuzzle, stateSaver = NullablePairSaver) {
        mutableStateOf(initialGrid.emptyCells().firstOrNull())
    }
    var isSolved by rememberSaveable(activePuzzle) {
        mutableStateOf(false)
    }

    val conflicts by remember(currentGrid, activePuzzle) {
        derivedStateOf { computeConflicts(activePuzzle, currentGrid) }
    }

    // Auto-dismiss once the instant the final correct digit is placed
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
                    val newGrid = currentGrid.set(r, c, digit)
                    currentGrid = newGrid
                    val isCorrect = (digit == activePuzzle.solution[r, c])
                    if (!isCorrect) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else {
                        selectedCell = nextTargetCellAfterEntry(activePuzzle, newGrid, r, c)
                    }
                }
            }
        }
    }

    val onClearClick: () -> Unit = {
        currentOnUserAction()
        if (!isSolved) {
            selectedCell?.let { (r, c) ->
                if (initialGrid[r, c] == 0) {
                    currentGrid = currentGrid.set(r, c, 0)
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (activePuzzle.mode == SudokuMode.STANDARD) "Sudoku 4×4" else "Sudoku 9×9",
            style = typo.headlineMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Tap an empty cell, then enter a digit",
            style = typo.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Bento card enclosure around the grid with 16dp rounded corners and hairline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .clip(morroShapes().Card)
                .background(colors.surfaceCard)
                .morroHairline(radius = 16.dp, color = colors.borderSubtle)
                .padding(6.dp)
        ) {
            SudokuGridView(
                initialGrid = initialGrid,
                currentGrid = currentGrid,
                selectedCell = selectedCell,
                conflicts = conflicts,
                onCellSelected = { r, c ->
                    currentOnUserAction()
                    if (!isSolved) selectedCell = r to c
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SudokuKeypad(
            mode = activePuzzle.mode,
            onDigitClick = onDigitClick,
            onClearClick = onClearClick,
            enabled = !isSolved,
            modifier = Modifier.fillMaxWidth()
        )
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
