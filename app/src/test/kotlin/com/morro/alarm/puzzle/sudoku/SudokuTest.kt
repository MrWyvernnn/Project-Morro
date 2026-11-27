package com.morro.alarm.puzzle.sudoku

import org.junit.Assert.*
import org.junit.Test

class SudokuGridTest {

    // --- conflicts() ---

    @Test
    fun `conflicts catches row duplicate`() {
        val grid = SudokuGrid.empty(4, 2, 2)
            .set(0, 0, 1)
            .set(0, 1, 1) // duplicate in row 0
        val bad = SudokuGrid.conflicts(grid)
        assertTrue("Row duplicate should flag (0,0)", (0 to 0) in bad)
        assertTrue("Row duplicate should flag (0,1)", (0 to 1) in bad)
    }

    @Test
    fun `conflicts catches column duplicate`() {
        val grid = SudokuGrid.empty(4, 2, 2)
            .set(0, 0, 2)
            .set(1, 0, 2) // duplicate in col 0
        val bad = SudokuGrid.conflicts(grid)
        assertTrue("Col duplicate should flag (0,0)", (0 to 0) in bad)
        assertTrue("Col duplicate should flag (1,0)", (1 to 0) in bad)
    }

    @Test
    fun `conflicts catches box duplicate`() {
        val grid = SudokuGrid.empty(4, 2, 2)
            .set(0, 0, 3)
            .set(1, 1, 3) // same 2x2 box
        val bad = SudokuGrid.conflicts(grid)
        assertTrue("Box duplicate should flag (0,0)", (0 to 0) in bad)
        assertTrue("Box duplicate should flag (1,1)", (1 to 1) in bad)
    }

    @Test
    fun `no conflicts on a valid partial grid`() {
        val grid = SudokuGrid.empty(4, 2, 2)
            .set(0, 0, 1)
            .set(0, 1, 2)
            .set(1, 0, 3)
            .set(1, 1, 4)
        assertTrue(SudokuGrid.conflicts(grid).isEmpty())
    }

    // --- isComplete / isSolved ---

    @Test
    fun `isComplete false for empty grid`() {
        assertFalse(SudokuGrid.isComplete(SudokuGrid.empty(4, 2, 2)))
    }

    @Test
    fun `isSolved true for valid complete 4x4`() {
        // A valid 4x4 solution
        val grid = SudokuGrid(4, 2, 2, arrayOf(
            intArrayOf(1, 2, 3, 4),
            intArrayOf(3, 4, 1, 2),
            intArrayOf(2, 1, 4, 3),
            intArrayOf(4, 3, 2, 1)
        ))
        assertTrue(SudokuGrid.isSolved(grid))
    }

    // --- emptyCells ---

    @Test
    fun `emptyCells returns all cells for empty grid`() {
        val grid = SudokuGrid.empty(4, 2, 2)
        assertEquals(16, grid.emptyCells().size)
    }

    @Test
    fun `emptyCells excludes filled cells`() {
        val grid = SudokuGrid.empty(4, 2, 2).set(0, 0, 1)
        assertEquals(15, grid.emptyCells().size)
        assertFalse((0 to 0) in grid.emptyCells())
    }

    // --- immutability ---

    @Test
    fun `set returns new grid without mutating original`() {
        val original = SudokuGrid.empty(4, 2, 2)
        val modified = original.set(0, 0, 1)
        assertEquals(0, original[0, 0])
        assertEquals(1, modified[0, 0])
    }
}

class SudokuSolverTest {

    @Test
    fun `countSolutions returns 1 for valid unique 4x4 puzzle`() {
        // Remove one cell from a known solution
        val solution = SudokuGrid(4, 2, 2, arrayOf(
            intArrayOf(1, 2, 3, 4),
            intArrayOf(3, 4, 1, 2),
            intArrayOf(2, 1, 4, 3),
            intArrayOf(4, 3, 2, 1)
        ))
        val puzzle = solution.set(0, 0, 0)
        assertEquals(1, SudokuSolver.countSolutions(puzzle, 2))
    }

    @Test
    fun `solve returns solution for valid puzzle`() {
        val solution = SudokuGrid(4, 2, 2, arrayOf(
            intArrayOf(1, 2, 3, 4),
            intArrayOf(3, 4, 1, 2),
            intArrayOf(2, 1, 4, 3),
            intArrayOf(4, 3, 2, 1)
        ))
        val puzzle = solution.set(0, 0, 0)
        val solved = SudokuSolver.solve(puzzle)
        assertNotNull(solved)
        assertEquals(solution, solved)
    }

    @Test
    fun `isLogicallyDeducible naked single`() {
        // Fill row 0 except (0,0), and ensure column and box also constrain it
        val grid = SudokuGrid(4, 2, 2, arrayOf(
            intArrayOf(0, 2, 3, 4),
            intArrayOf(3, 4, 1, 2),
            intArrayOf(2, 1, 4, 3),
            intArrayOf(4, 3, 2, 1)
        ))
        // Only 1 fits at (0,0) → naked single
        assertTrue(SudokuSolver.isLogicallyDeducible(grid, 0, 0))
    }

    @Test
    fun `candidatesFor returns correct set`() {
        val grid = SudokuGrid(4, 2, 2, arrayOf(
            intArrayOf(0, 2, 3, 4),
            intArrayOf(3, 4, 1, 2),
            intArrayOf(2, 1, 4, 3),
            intArrayOf(4, 3, 2, 1)
        ))
        assertEquals(setOf(1), SudokuSolver.candidatesFor(grid, 0, 0))
    }
}

class SudokuGeneratorTest {

    // --- Determinism ---

    @Test
    fun `same seed produces identical puzzle across all three tiers`() {
        for (mode in SudokuMode.values()) {
            val a = SudokuGenerator.generate(42L, mode)
            val b = SudokuGenerator.generate(42L, mode)
            assertEquals("grid mismatch for $mode", a.grid, b.grid)
            assertEquals("solution mismatch for $mode", a.solution, b.solution)
            assertEquals(mode, a.mode)
            assertEquals(42L, a.seed)
        }
    }

    // --- Wake-up calibrated removal counts ---

    @Test
    fun `EASY removes 6 cells on 4x4`() {
        for (seed in 1L..100L) {
            val puzzle = SudokuGenerator.generate(seed, SudokuMode.EASY)
            assertEquals(4, puzzle.grid.size)
            assertEquals(2, puzzle.grid.boxRows)
            assertEquals(2, puzzle.grid.boxCols)
            assertEquals("Seed $seed EASY empty count", 6, puzzle.grid.emptyCells().size)
        }
    }

    @Test
    fun `MEDIUM removes 6 cells on 9x9`() {
        for (seed in 1L..100L) {
            val puzzle = SudokuGenerator.generate(seed, SudokuMode.MEDIUM)
            assertEquals(9, puzzle.grid.size)
            assertEquals(3, puzzle.grid.boxRows)
            assertEquals(3, puzzle.grid.boxCols)
            assertEquals("Seed $seed MEDIUM empty count", 6, puzzle.grid.emptyCells().size)
        }
    }

    @Test
    fun `HARD removes 10 to 12 cells on 9x9`() {
        for (seed in 1L..100L) {
            val puzzle = SudokuGenerator.generate(seed, SudokuMode.HARD)
            assertEquals(9, puzzle.grid.size)
            val empties = puzzle.grid.emptyCells().size
            assertTrue(
                "Seed $seed: HARD should remove 10-12 cells, got $empties",
                empties in 10..12
            )
        }
    }

    // --- Uniqueness: 500 seeds per tier ---

    @Test
    fun `500 seeds per tier all have exactly one solution`() {
        for (mode in SudokuMode.values()) {
            for (seed in 1L..500L) {
                val puzzle = SudokuGenerator.generate(seed, mode)
                val count = SudokuSolver.countSolutions(puzzle.grid, 2)
                assertEquals("Seed $seed $mode should have exactly 1 solution", 1, count)
            }
        }
    }

    // --- Logical deducibility: 500 seeds per tier ---

    @Test
    fun `500 seeds per tier are logically deducible without guessing`() {
        for (mode in SudokuMode.values()) {
            for (seed in 1L..500L) {
                val puzzle = SudokuGenerator.generate(seed, mode)
                assertTrue(
                    "Seed $seed $mode must pass logical-solve verification",
                    SudokuSolver.isLogicallySolvable(puzzle.grid)
                )
            }
        }
    }

    // --- Solution validity ---

    @Test
    fun `generated solutions are valid across all tiers`() {
        for (mode in SudokuMode.values()) {
            for (seed in 1L..50L) {
                val puzzle = SudokuGenerator.generate(seed, mode)
                assertTrue("$mode seed $seed solution is solved", SudokuGrid.isSolved(puzzle.solution))
            }
        }
    }

    // --- Performance ---

    @Test
    fun `all tiers generate in under 50ms average`() {
        for (mode in SudokuMode.values()) {
            repeat(10) { SudokuGenerator.generate(it.toLong(), mode) }
            val start = System.nanoTime()
            val iterations = 100
            repeat(iterations) { SudokuGenerator.generate(it.toLong() + 1000, mode) }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
            val avg = elapsedMs / iterations
            assertTrue("$mode avg generation ${avg}ms should be under 50ms", avg < 50.0)
        }
    }

    // --- Solver & Grid Hardening ---

    @Test
    fun `countSolutions and solve reject conflicting pre-filled givens`() {
        val conflicting = SudokuGrid(4, 2, 2, arrayOf(
            intArrayOf(1, 1, 3, 4),
            intArrayOf(3, 4, 2, 1),
            intArrayOf(2, 3, 4, 2),
            intArrayOf(4, 2, 1, 3)
        ))
        assertEquals(0, SudokuSolver.countSolutions(conflicting, 2))
        assertNull(SudokuSolver.solve(conflicting))
    }

    @Test
    fun `countSolutions returns 0 when limit is zero or negative`() {
        val valid = SudokuGrid(4, 2, 2, arrayOf(
            intArrayOf(1, 2, 3, 4),
            intArrayOf(3, 4, 1, 2),
            intArrayOf(2, 1, 4, 3),
            intArrayOf(4, 3, 2, 1)
        ))
        assertEquals(0, SudokuSolver.countSolutions(valid, 0))
        assertEquals(0, SudokuSolver.countSolutions(valid, -1))
    }

    @Test
    fun `SudokuGrid rejects invalid dimensions and out of range cell values`() {
        assertThrows(IllegalArgumentException::class.java) {
            SudokuGrid(0, 0, 0, emptyArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            SudokuGrid.empty(4, 2, 2).set(0, 0, 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SudokuGrid.empty(4, 2, 2).set(0, 0, -1)
        }
    }

    @Test
    fun `edge case seeds generate valid deterministic puzzles with target hole counts`() {
        val edgeSeeds = listOf(0L, -1L, -42L, Long.MIN_VALUE, Long.MAX_VALUE)
        for (seed in edgeSeeds) {
            val std = SudokuGenerator.generate(seed, SudokuMode.EASY)
            assertEquals(std, SudokuGenerator.generate(seed, SudokuMode.EASY))
            assertEquals(6, std.grid.emptyCells().size)
            assertEquals(1, SudokuSolver.countSolutions(std.grid, 2))

            val med = SudokuGenerator.generate(seed, SudokuMode.MEDIUM)
            assertEquals(med, SudokuGenerator.generate(seed, SudokuMode.MEDIUM))
            assertEquals(6, med.grid.emptyCells().size)
            assertEquals(1, SudokuSolver.countSolutions(med.grid, 2))

            val hard = SudokuGenerator.generate(seed, SudokuMode.HARD)
            assertEquals(hard, SudokuGenerator.generate(seed, SudokuMode.HARD))
            assertTrue("Seed $seed HARD empties=${hard.grid.emptyCells().size}", hard.grid.emptyCells().size in 10..12)
            assertEquals(1, SudokuSolver.countSolutions(hard.grid, 2))
        }
    }
}
