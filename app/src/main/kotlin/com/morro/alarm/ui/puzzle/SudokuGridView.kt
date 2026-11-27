package com.morro.alarm.ui.puzzle

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography
import kotlinx.coroutines.launch
import kotlin.math.min

// Restrained brick red scoped strictly to conflict cells inside the dark Sudoku grid.
private val GridConflictBrick = Color(0xFFC4553B)
private val GridDarkSurface = Color(0xFF121110)

private const val MAX_UNDO_HISTORY = 20

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
 * Canvas-rendered Classic Sudoku grid in Morro's monochrome surface palette:
 *  - Grid background: `#121110` (`surfaceCard`)
 *  - Inner cell lines: `borderSubtle`; box boundaries: `borderStrong` at `1.5.dp`
 *  - Givens: `textPrimary` (`#EFE7D6`), strictly inert (only EMPTY cells are tappable)
 *  - User digits: `honey` (`#E8C58F`)
 *  - Conflicts: `#C4553B` digit on `#C4553B` at `0.14` alpha fill (scoped only to conflict cells)
 *  - Selection: `borderActive` hairline + `honey` at `0.12` fill
 *  - Peers (row, col, box): `honey` at `0.05`; Same number: `honey` at `0.10`
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
) {
    val colors = morroColors()
    val typo = morroTypography()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 48)
    val currentOnCellSelected by rememberUpdatedState(onCellSelected)

    val subtleStrokePx = with(density) { 1.dp.toPx() }
    val strongStrokePx = with(density) { 1.5.dp.toPx() }
    val activeStrokePx = with(density) { 1.2.dp.toPx() }
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
            .background(GridDarkSurface)
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
                        emptyTarget?.let { (r, c) ->
                            currentOnCellSelected(r, c)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val cellWidth = width / gridSize
            val cellHeight = height / gridSize

            // 1. Peer (row, col, box) @ honey 0.05 & same-number highlight @ honey 0.10
            selectedCell?.let { (sr, sc) ->
                if (sr in 0 until gridSize && sc in 0 until gridSize) {
                    val sBoxR = sr / boxRows
                    val sBoxC = sc / boxCols
                    for (r in 0 until gridSize) {
                        for (c in 0 until gridSize) {
                            val inPeer = (r == sr || c == sc || (r / boxRows == sBoxR && c / boxCols == sBoxC))
                            val sameDigit = selectedDigit != 0 && currentGrid[r, c] == selectedDigit && (r to c) != selectedCell
                            if (sameDigit) {
                                drawRect(
                                    color = colors.honey.copy(alpha = 0.10f),
                                    topLeft = Offset(c * cellWidth, r * cellHeight),
                                    size = Size(cellWidth, cellHeight)
                                )
                            } else if (inPeer && (r to c) != selectedCell) {
                                drawRect(
                                    color = colors.honey.copy(alpha = 0.05f),
                                    topLeft = Offset(c * cellWidth, r * cellHeight),
                                    size = Size(cellWidth, cellHeight)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Conflict cell background (#C4553B @ 0.14 alpha)
            for ((r, c) in conflicts) {
                if (r in 0 until gridSize && c in 0 until gridSize) {
                    drawRect(
                        color = GridConflictBrick.copy(alpha = 0.14f),
                        topLeft = Offset(c * cellWidth, r * cellHeight),
                        size = Size(cellWidth, cellHeight)
                    )
                }
            }

            // 3. Selected cell: borderActive hairline + honey at 0.12 fill
            selectedCell?.let { (r, c) ->
                if (r in 0 until gridSize && c in 0 until gridSize) {
                    if ((r to c) !in conflicts) {
                        drawRect(
                            color = colors.honey.copy(alpha = 0.12f),
                            topLeft = Offset(c * cellWidth, r * cellHeight),
                            size = Size(cellWidth, cellHeight)
                        )
                    }
                    val half = activeStrokePx / 2f
                    drawRect(
                        color = colors.borderActive,
                        topLeft = Offset(c * cellWidth + half, r * cellHeight + half),
                        size = Size(cellWidth - activeStrokePx, cellHeight - activeStrokePx),
                        style = Stroke(width = activeStrokePx)
                    )
                }
            }

            // 4. Inner cell lines: borderSubtle
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

            // 5. Box boundaries & outer perimeter: borderStrong at 1.5dp
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
            val halfStrong = strongStrokePx / 2f
            drawRect(
                color = colors.borderStrong,
                topLeft = Offset(halfStrong, halfStrong),
                size = Size(width - strongStrokePx, height - strongStrokePx),
                style = Stroke(width = strongStrokePx)
            )

            // 6. Cell digits & corner pencil marks
            val targetFontSp = with(density) { (min(cellWidth, cellHeight) * 0.54f).toSp() }
            val cellFontSp = minOf(targetFontSp.value, typo.headlineMedium.fontSize.value).sp
            val digitStyle = typo.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = cellFontSp,
                lineHeight = cellFontSp,
                textAlign = TextAlign.Center
            )
            val noteFontSp = with(density) { (min(cellWidth / boxCols, cellHeight / boxRows) * 0.62f).toSp() }
            val noteStyle = typo.labelLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = noteFontSp.value.coerceIn(7f, 11f).sp,
                lineHeight = noteFontSp.value.coerceIn(7f, 11f).sp,
                textAlign = TextAlign.Center
            )

            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    val value = currentGrid[r, c]
                    if (value != 0) {
                        val isGiven = initialGrid[r, c] != 0
                        val textColor = when {
                            isGiven -> colors.textPrimary
                            (r to c) in conflicts -> GridConflictBrick
                            else -> colors.honey
                        }
                        val layoutResult = textMeasurer.measure(
                            text = value.toString(),
                            style = digitStyle.copy(color = textColor)
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
                                val noteLayout = textMeasurer.measure(
                                    text = digit.toString(),
                                    style = noteStyle.copy(color = colors.textTertiary)
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
 * Classic Sudoku dismissal gate adhering strictly to Morro monochrome chrome around the `#121110` grid:
 *  - Three wake-up calibrated tiers (`Easy`, `Medium`, `Hard`).
 *  - Compact Morro header (`tier` + remaining blanks).
 *  - Dark grid canvas (`#121110`) with `honey` peers/selection & scoped `#C4553B` conflict indicator.
 *  - Morro action row (`Undo · Erase · Notes` — Notes defaults OFF; Undo bounded to 20 entries).
 *  - Morro number pad (`1–9` or `1–4` + `Clear`) sized >= 56dp, fitting `360×800` without scroll.
 *  - Auto-dismisses the instant the final correct digit is placed; wrong digits remain visible.
 */
@Composable
fun SudokuGateScreen(
    onGateSolved: () -> Unit,
    modifier: Modifier = Modifier,
    puzzle: SudokuPuzzle? = null,
    mode: SudokuMode = SudokuMode.EASY,
    seed: Long = rememberSaveable { System.currentTimeMillis() },
    onUserAction: () -> Unit = {},
    onGenerationFailed: () -> Unit = {}
) {
    val colors = morroColors()
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
    // Notes toggle MUST default OFF (pencil marks help Hard, add friction on Easy).
    var notesMode by rememberSaveable(activePuzzle) { mutableStateOf(false) }
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

    val remainingDigitCounts by remember(currentGrid) {
        derivedStateOf {
            val n = currentGrid.size
            val placedCounts = IntArray(n + 1)
            for (r in 0 until n) {
                for (c in 0 until n) {
                    val v = currentGrid[r, c]
                    if (v in 1..n) placedCounts[v]++
                }
            }
            (1..n).associateWith { d -> (n - placedCounts[d]).coerceAtLeast(0) }
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
                    undoStack = (undoStack + SudokuSnapshot(currentGrid, notesMap)).takeLast(MAX_UNDO_HISTORY)
                    if (notesMode) {
                        val existing = notesMap[r to c].orEmpty()
                        val updated = if (digit in existing) existing - digit else existing + digit
                        val nextNotes = notesMap.toMutableMap()
                        if (updated.isEmpty()) nextNotes.remove(r to c) else nextNotes[r to c] = updated
                        if (currentGrid[r, c] != 0) {
                            currentGrid = currentGrid.set(r, c, 0)
                        }
                        notesMap = nextNotes
                    } else {
                        val newGrid = currentGrid.set(r, c, digit)
                        currentGrid = newGrid
                        // Entering a value always clears that cell's marks (plus peer conflicts).
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
                                entryPop.snapTo(0.76f)
                                entryPop.animateTo(1f, MorroSpring.FloatSpringPlayful)
                            }
                        }
                        if (!isCorrect) {
                            // Keep wrong digit visible with conflict state — never auto-clear
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
                    undoStack = (undoStack + SudokuSnapshot(currentGrid, notesMap)).takeLast(MAX_UNDO_HISTORY)
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
        // Compact Morro monochrome status header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sudoku · ${activePuzzle.mode.label}",
                style = typo.titleMedium,
                color = colors.textSecondary
            )
            AnimatedContent(
                targetState = remainingEmptyCount,
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        fadeIn(tween(MorroMotion.EnterMillis, easing = MorroMotion.EnterEasing))
                            .togetherWith(fadeOut(tween(MorroMotion.exitMillis(150))))
                    }
                },
                label = "sudokuRemaining"
            ) { rem ->
                Text(
                    text = if (rem == 0) "Solved" else "$rem left",
                    style = typo.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    color = colors.honey
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Classic grid (#121110) with Morro border hairlines
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = with(density) { gridShakeOffset.value.dp.toPx() }
                }
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
                modifier = Modifier.fillMaxWidth(),
                lastEnteredCell = lastEnteredCell,
                entryPopScale = entryPop.value
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Compact Morro monochrome action bar above the pad: Undo · Erase · Notes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SudokuActionBarChip(
                label = "Undo",
                enabled = !isSolved && undoStack.isNotEmpty(),
                active = false,
                onClick = onUndoClick,
                modifier = Modifier.weight(1f)
            )
            SudokuActionBarChip(
                label = "Erase",
                enabled = !isSolved && selectedCell != null,
                active = false,
                onClick = onEraseClick,
                modifier = Modifier.weight(1f)
            )
            SudokuActionBarChip(
                label = if (notesMode) "Notes on" else "Notes",
                enabled = !isSolved,
                active = notesMode,
                onClick = {
                    currentOnUserAction()
                    notesMode = !notesMode
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        SudokuKeypad(
            mode = activePuzzle.mode,
            onDigitClick = onDigitClick,
            onClearClick = onEraseClick,
            enabled = !isSolved,
            remainingCounts = remainingDigitCounts,
            notesMode = notesMode,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SudokuActionBarChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val colors = morroColors()
    val shapes = morroShapes()
    val typo = morroTypography()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .height(40.dp)
            .pressScale(
                interactionSource = interaction,
                enabled = enabled,
                pressedScale = MorroMotion.PressedScale,
                releaseSpec = MorroSpring.FloatSpringPlayful
            )
            .clip(shapes.Button)
            .background(if (active) colors.surfaceElevated else colors.surfaceCard)
            .morroHairline(
                radius = 12.dp,
                color = if (active) colors.borderActive else colors.borderSubtle
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typo.labelLarge,
            color = when {
                active -> colors.honey
                enabled -> colors.textPrimary
                else -> colors.textDisabled
            },
            maxLines = 1
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
            if (r == row && c == col) return@forEach
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
