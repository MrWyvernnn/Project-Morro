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
    fun `same seed produces identical puzzle for STANDARD`() {
        val a = SudokuGenerator.generate(42L, SudokuMode.STANDARD)
        val b = SudokuGenerator.generate(42L, SudokuMode.STANDARD)
        assertEquals(a.grid, b.grid)
        assertEquals(a.solution, b.solution)
        assertEquals(a.mode, b.mode)
        assertEquals(a.seed, b.seed)
    }

    @Test
    fun `same seed produces identical puzzle for HARD`() {
        val a = SudokuGenerator.generate(42L, SudokuMode.HARD)
        val b = SudokuGenerator.generate(42L, SudokuMode.HARD)
        assertEquals(a.grid, b.grid)
        assertEquals(a.solution, b.solution)
    }

    // --- Removal count ---

    @Test
    fun `STANDARD removes 6 to 8 cells`() {
        for (seed in 1L..100L) {
            val puzzle = SudokuGenerator.generate(seed, SudokuMode.STANDARD)
            val empties = puzzle.grid.emptyCells().size
            assertTrue(
                "Seed $seed: STANDARD should remove 6-8 cells, got $empties",
                empties in 6..8
            )
        }
    }

    @Test
    fun `HARD removes 5 to 7 cells`() {
        for (seed in 1L..100L) {
            val puzzle = SudokuGenerator.generate(seed, SudokuMode.HARD)
            val empties = puzzle.grid.emptyCells().size
            assertTrue(
                "Seed $seed: HARD should remove 5-7 cells, got $empties",
                empties in 5..7
            )
        }
    }

    // --- Uniqueness: 500 seeds per mode ---

    @Test
    fun `500 seeds STANDARD all have exactly one solution`() {
        for (seed in 1L..500L) {
            val puzzle = SudokuGenerator.generate(seed, SudokuMode.STANDARD)
            val count = SudokuSolver.countSolutions(puzzle.grid, 2)
            assertEquals("Seed $seed: STANDARD should have exactly 1 solution", 1, count)
        }
    }

    @Test
    fun `500 seeds HARD all have exactly one solution`() {
        for (seed in 1L..500L) {
            val puzzle = SudokuGenerator.generate(seed, SudokuMode.HARD)
            val count = SudokuSolver.countSolutions(puzzle.grid, 2)
            assertEquals("Seed $seed: HARD should have exactly 1 solution", 1, count)
        }
    }

    // --- Logical deducibility ---

    @Test
    fun `every removed cell in STANDARD is logically deducible without guessing`() {
        for (seed in 1L..500L) {
            val puzzle = SudokuGenerator.generate(seed, SudokuMode.STANDARD)
            var current = puzzle.grid
            val remaining = current.emptyCells().toMutableSet()
            while (remaining.isNotEmpty()) {
                val deducible = remaining.firstOrNull { (r, c) ->
                    SudokuSolver.isLogicallyDeducible(current, r, c)
                }
                assertNotNull(
                    "Seed $seed: stuck with no logically deducible cells! Remaining: $remaining",
                    deducible
                )
                val (r, c) = deducible!!
                current = current.set(r, c, puzzle.solution[r, c])
                remaining.remove(deducible)
            }
            assertEquals(puzzle.solution, current)
        }
    }

    @Test
    fun `every removed cell in HARD is logically deducible without guessing`() {
        for (seed in 1L..500L) {
            val puzzle = SudokuGenerator.generate(seed, SudokuMode.HARD)
            var current = puzzle.grid
            val remaining = current.emptyCells().toMutableSet()
            while (remaining.isNotEmpty()) {
                val deducible = remaining.firstOrNull { (r, c) ->
                    SudokuSolver.isLogicallyDeducible(current, r, c)
                }
                assertNotNull(
                    "Seed $seed: stuck with no logically deducible cells! Remaining: $remaining",
                    deducible
                )
                val (r, c) = deducible!!
                current = current.set(r, c, puzzle.solution[r, c])
                remaining.remove(deducible)
            }
            assertEquals(puzzle.solution, current)
        }
    }

    // --- Solution validity ---

    @Test
    fun `generated solutions are valid`() {
        for (seed in 1L..50L) {
            val std = SudokuGenerator.generate(seed, SudokuMode.STANDARD)
            assertTrue("STANDARD seed $seed solution is solved", SudokuGrid.isSolved(std.solution))

            val hard = SudokuGenerator.generate(seed, SudokuMode.HARD)
            assertTrue("HARD seed $seed solution is solved", SudokuGrid.isSolved(hard.solution))
        }
    }

    // --- Performance ---

    @Test
    fun `STANDARD generation under 50ms average`() {
        // Warm up
        repeat(10) { SudokuGenerator.generate(it.toLong(), SudokuMode.STANDARD) }

        val start = System.nanoTime()
        val iterations = 100
        repeat(iterations) { SudokuGenerator.generate(it.toLong() + 1000, SudokuMode.STANDARD) }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        val avg = elapsed / iterations

        assertTrue(
            "STANDARD avg generation ${avg}ms should be under 50ms",
            avg < 50.0
        )
    }

    @Test
    fun `HARD generation under 50ms average`() {
        // Warm up
        repeat(10) { SudokuGenerator.generate(it.toLong(), SudokuMode.HARD) }

        val start = System.nanoTime()
        val iterations = 100
        repeat(iterations) { SudokuGenerator.generate(it.toLong() + 1000, SudokuMode.HARD) }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        val avg = elapsed / iterations

        assertTrue(
            "HARD avg generation ${avg}ms should be under 50ms",
            avg < 50.0
        )
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
            val std = SudokuGenerator.generate(seed, SudokuMode.STANDARD)
            assertEquals(std, SudokuGenerator.generate(seed, SudokuMode.STANDARD))
            assertTrue("Seed $seed STANDARD empties=${std.grid.emptyCells().size}", std.grid.emptyCells().size in 6..8)
            assertEquals(1, SudokuSolver.countSolutions(std.grid, 2))

            val hard = SudokuGenerator.generate(seed, SudokuMode.HARD)
            assertEquals(hard, SudokuGenerator.generate(seed, SudokuMode.HARD))
            assertTrue("Seed $seed HARD empties=${hard.grid.emptyCells().size}", hard.grid.emptyCells().size in 5..7)
            assertEquals(1, SudokuSolver.countSolutions(hard.grid, 2))
        }
    }
}
