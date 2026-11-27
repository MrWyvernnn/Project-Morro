package com.morro.alarm.ui.puzzle

import com.morro.alarm.puzzle.sudoku.SudokuGenerator
import com.morro.alarm.puzzle.sudoku.SudokuGrid
import com.morro.alarm.puzzle.sudoku.SudokuMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuUiTest {

    @Test
    fun `givens are inert and empty cells are resolved`() {
        // Create 4x4 grid where (0, 0) is given and (0, 1) is empty
        val initialGrid = SudokuGrid(
            size = 4,
            boxRows = 2,
            boxCols = 2,
            cells = arrayOf(
                intArrayOf(1, 0, 3, 4),
                intArrayOf(3, 4, 1, 2),
                intArrayOf(2, 3, 4, 1),
                intArrayOf(4, 1, 2, 3)
            )
        )

        val gridSizePx = 400f
        val cellWidth = 100f // 400 / 4
        val expandPx = 8f

        // Tap dead center of empty cell (0, 1): x = 150, y = 50
        val centerHit = resolveNearestEmptyCell(
            tapX = 150f,
            tapY = 50f,
            gridSizePx = gridSizePx,
            initialGrid = initialGrid,
            expandPx = expandPx
        )
        assertEquals(Pair(0, 1), centerHit)

        // Tap inside given cell (0, 0) far from (0, 1): x = 20, y = 50
        val farGivenHit = resolveNearestEmptyCell(
            tapX = 20f,
            tapY = 50f,
            gridSizePx = gridSizePx,
            initialGrid = initialGrid,
            expandPx = expandPx
        )
        // Givens are inert and (20, 50) is >8px away from (0, 1) bounds (left edge of 0,1 is 100)
        assertNull(farGivenHit)
    }

    @Test
    fun `expanded hit region snaps to nearest empty cell`() {
        val initialGrid = SudokuGrid(
            size = 4,
            boxRows = 2,
            boxCols = 2,
            cells = arrayOf(
                intArrayOf(1, 0, 3, 4),
                intArrayOf(3, 4, 1, 2),
                intArrayOf(2, 3, 4, 1),
                intArrayOf(4, 1, 2, 3)
            )
        )

        val gridSizePx = 400f
        val expandPx = 10f

        // Cell (0, 1) is from x=100 to x=200, y=0 to y=100.
        // Tap at x=95, y=50 (inside given cell (0, 0), but within 10px of empty cell (0, 1))
        val snappedHit = resolveNearestEmptyCell(
            tapX = 95f,
            tapY = 50f,
            gridSizePx = gridSizePx,
            initialGrid = initialGrid,
            expandPx = expandPx
        )
        assertEquals(Pair(0, 1), snappedHit)
    }

    @Test
    fun `tap between two empty cells selects closer one`() {
        val initialGrid = SudokuGrid(
            size = 4,
            boxRows = 2,
            boxCols = 2,
            cells = arrayOf(
                intArrayOf(1, 0, 0, 4),
                intArrayOf(3, 4, 1, 2),
                intArrayOf(2, 3, 4, 1),
                intArrayOf(4, 1, 2, 3)
            )
        )

        val gridSizePx = 400f
        val expandPx = 10f

        // Cell (0, 1): x in 100..200 (center 150)
        // Cell (0, 2): x in 200..300 (center 250)
        // Tap at x=190, y=50 -> closer to center 150 than 250
        val closerTo1 = resolveNearestEmptyCell(
            tapX = 190f,
            tapY = 50f,
            gridSizePx = gridSizePx,
            initialGrid = initialGrid,
            expandPx = expandPx
        )
        assertEquals(Pair(0, 1), closerTo1)

        // Tap at x=210, y=50 -> closer to center 250 than 150
        val closerTo2 = resolveNearestEmptyCell(
            tapX = 210f,
            tapY = 50f,
            gridSizePx = gridSizePx,
            initialGrid = initialGrid,
            expandPx = expandPx
        )
        assertEquals(Pair(0, 2), closerTo2)
    }

    @Test
    fun `computeConflicts flags wrong digits and duplicate entries`() {
        val puzzle = SudokuGenerator.generate(seed = 12345L, mode = SudokuMode.STANDARD)
        val emptyCells = puzzle.grid.emptyCells()
        assertTrue(emptyCells.isNotEmpty())

        val (targetR, targetC) = emptyCells.first()
        val correctVal = puzzle.solution[targetR, targetC]
        val wrongVal = if (correctVal == 1) 2 else 1

        // 1. Initial grid has 0 conflicts
        val initialConflicts = computeConflicts(puzzle, puzzle.grid)
        assertTrue(initialConflicts.isEmpty())

        // 2. Setting correct digit has 0 conflicts
        val gridWithCorrect = puzzle.grid.set(targetR, targetC, correctVal)
        val conflictsWithCorrect = computeConflicts(puzzle, gridWithCorrect)
        assertTrue(conflictsWithCorrect.isEmpty())

        // 3. Setting wrong digit triggers conflict on that cell
        val gridWithWrong = puzzle.grid.set(targetR, targetC, wrongVal)
        val conflictsWithWrong = computeConflicts(puzzle, gridWithWrong)
        assertTrue(conflictsWithWrong.contains(Pair(targetR, targetC)))

        // 4. Clearing the cell removes conflict
        val gridCleared = gridWithWrong.set(targetR, targetC, 0)
        val conflictsCleared = computeConflicts(puzzle, gridCleared)
        assertFalse(conflictsCleared.contains(Pair(targetR, targetC)))
    }

    @Test
    fun `solving puzzle reaches valid solved state`() {
        val puzzle = SudokuGenerator.generate(seed = 99999L, mode = SudokuMode.STANDARD)
        var workingGrid = puzzle.grid

        for ((r, c) in puzzle.grid.emptyCells()) {
            val correctVal = puzzle.solution[r, c]
            workingGrid = workingGrid.set(r, c, correctVal)
        }

        assertTrue(SudokuGrid.isSolved(workingGrid))
        assertEquals(puzzle.solution, workingGrid)
        val conflicts = computeConflicts(puzzle, workingGrid)
        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `hard mode 9x9 generates with correct dimensions and deducible holes`() {
        val puzzle = SudokuGenerator.generate(seed = 42L, mode = SudokuMode.HARD)
        assertEquals(9, puzzle.grid.size)
        assertEquals(3, puzzle.grid.boxRows)
        assertEquals(3, puzzle.grid.boxCols)
        val emptyCells = puzzle.grid.emptyCells()
        assertTrue(emptyCells.size in 5..7)
    }

    @Test
    fun `computeConflicts never shades inert given cells`() {
        val puzzle = SudokuGenerator.generate(seed = 42L, mode = SudokuMode.STANDARD)
        val empties = puzzle.grid.emptyCells()
        val (r, c) = empties.first()
        // Pick a digit that equals a given in the same row (guaranteed wrong for (r, c))
        val givenInRow = (0 until 4).first { col -> puzzle.grid[r, col] != 0 }
        val dupDigit = puzzle.grid[r, givenInRow]

        val gridWithDup = puzzle.grid.set(r, c, dupDigit)
        val conflicts = computeConflicts(puzzle, gridWithDup)
        assertTrue("Wrong user cell must be flagged", (r to c) in conflicts)
        assertFalse("Inert given cell must NOT be shaded", (r to givenInRow) in conflicts)
    }

    @Test
    fun `nextTargetCellAfterEntry advances sequentially and falls back to conflicts`() {
        val puzzle = SudokuGenerator.generate(seed = 77L, mode = SudokuMode.STANDARD)
        val empties = puzzle.grid.emptyCells()
        assertTrue(empties.size >= 2)

        // Put wrong digit in empties[0], then fill remaining cells correctly except empties[1]
        val (r0, c0) = empties[0]
        val (r1, c1) = empties[1]
        val wrongVal = if (puzzle.solution[r0, c0] == 1) 2 else 1
        var g = puzzle.grid.set(r0, c0, wrongVal)
        for ((r, c) in empties.drop(2)) {
            g = g.set(r, c, puzzle.solution[r, c])
        }
        // Now fill empties[1] correctly; emptyCells is empty, so target should jump to conflict (r0, c0)
        g = g.set(r1, c1, puzzle.solution[r1, c1])
        assertEquals(r0 to c0, nextTargetCellAfterEntry(puzzle, g, r1, c1))
    }

    @Test
    fun `clearPeerNotesForEntry removes digit from row, column, box, and entered cell`() {
        val initialNotes = mapOf(
            (0 to 0) to setOf(3, 4), // target cell
            (0 to 3) to setOf(2, 3), // same row
            (3 to 0) to setOf(1, 3), // same col
            (1 to 1) to setOf(3),    // same 2x2 box
            (2 to 2) to setOf(3, 4)  // unrelated cell
        )
        val cleaned = clearPeerNotesForEntry(
            notes = initialNotes,
            row = 0,
            col = 0,
            digit = 3,
            boxRows = 2,
            boxCols = 2
        )
        assertNull(cleaned[0 to 0])
        assertEquals(setOf(2), cleaned[0 to 3])
        assertEquals(setOf(1), cleaned[3 to 0])
        assertNull(cleaned[1 to 1])
        assertEquals(setOf(3, 4), cleaned[2 to 2])
    }
}
