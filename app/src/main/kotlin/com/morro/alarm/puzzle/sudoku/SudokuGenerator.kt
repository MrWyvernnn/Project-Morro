package com.morro.alarm.puzzle.sudoku

import kotlin.random.Random

/**
 * The two difficulty modes for Sudoku puzzles.
 */
enum class SudokuMode {
    /** 4×4 grid, 2×2 boxes, digits 1–4, remove 6–8 cells. */
    STANDARD,
    /** 9×9 grid, 3×3 boxes, digits 1–9, remove 5–7 cells. */
    HARD
}

/**
 * A generated Sudoku puzzle with its solution.
 */
data class SudokuPuzzle(
    val grid: SudokuGrid,
    val solution: SudokuGrid,
    val mode: SudokuMode,
    val seed: Long
)

/**
 * Deterministic Sudoku generator. Same seed + same mode → identical puzzle.
 * Uses seeded [Random] for all randomness; solver is deterministic.
 */
object SudokuGenerator {

    private const val MAX_GRID_ATTEMPTS = 16
    private const val SHUFFLES_PER_GRID = 4

    /**
     * Generate a puzzle for the given [mode] using [seed] for reproducibility.
     *
     * - STANDARD: 4×4, remove 6–8 cells.
     * - HARD: 9×9, remove 5–7 cells.
     *
     * Every removed cell is logically deducible (naked-single or hidden-single)
     * and the puzzle has exactly one solution.
     */
    fun generate(seed: Long, mode: SudokuMode): SudokuPuzzle {
        val rng = Random(seed)
        val params = when (mode) {
            SudokuMode.STANDARD -> GridParams(4, 2, 2, 6, 8)
            SudokuMode.HARD -> GridParams(9, 3, 3, 5, 7)
        }

        var bestPuzzle: SudokuGrid? = null
        var bestSolution: SudokuGrid? = null
        var bestRemoved = -1

        for (attempt in 0 until MAX_GRID_ATTEMPTS) {
            val solution = generateFullGrid(rng, params.size, params.boxRows, params.boxCols)
            for (shuffleTry in 0 until SHUFFLES_PER_GRID) {
                val (candidate, removed) = removeCells(rng, solution, params.maxRemove)
                if (removed >= params.minRemove) {
                    return SudokuPuzzle(
                        grid = candidate,
                        solution = solution,
                        mode = mode,
                        seed = seed
                    )
                }
                if (removed > bestRemoved) {
                    bestRemoved = removed
                    bestPuzzle = candidate
                    bestSolution = solution
                }
            }
        }

        return SudokuPuzzle(
            grid = bestPuzzle!!,
            solution = bestSolution!!,
            mode = mode,
            seed = seed
        )
    }

    // --- Internal helpers ---

    private data class GridParams(
        val size: Int,
        val boxRows: Int,
        val boxCols: Int,
        val minRemove: Int,
        val maxRemove: Int
    )

    /**
     * Build a complete, valid Sudoku grid using randomised backtracking.
     * The [rng] controls the shuffle order of candidate values.
     */
    private fun generateFullGrid(
        rng: Random, size: Int, boxRows: Int, boxCols: Int
    ): SudokuGrid {
        val board = Array(size) { IntArray(size) }
        val rowMask = IntArray(size)
        val colMask = IntArray(size)
        val boxMask = IntArray(size)

        fun fill(pos: Int): Boolean {
            if (pos == size * size) return true
            val r = pos / size
            val c = pos % size
            val b = (r / boxRows) * boxRows + (c / boxCols)
            val used = rowMask[r] or colMask[c] or boxMask[b]

            val values = IntArray(size) { it + 1 }
            // Fisher-Yates shuffle with rng
            for (i in size - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val tmp = values[i]
                values[i] = values[j]
                values[j] = tmp
            }

            for (v in values) {
                val bit = 1 shl (v - 1)
                if ((used and bit) == 0) {
                    board[r][c] = v
                    rowMask[r] = rowMask[r] or bit
                    colMask[c] = colMask[c] or bit
                    boxMask[b] = boxMask[b] or bit
                    if (fill(pos + 1)) return true
                    board[r][c] = 0
                    rowMask[r] = rowMask[r] xor bit
                    colMask[c] = colMask[c] xor bit
                    boxMask[b] = boxMask[b] xor bit
                }
            }
            return false
        }

        fill(0)
        return SudokuGrid(size, boxRows, boxCols, board)
    }

    /**
     * Remove up to [maxRemove] cells from [solution].
     *
     * A removal is kept only if:
     * 1. The puzzle still has exactly one solution.
     * 2. The removed cell is logically deducible (naked/hidden single).
     */
    private fun removeCells(
        rng: Random,
        solution: SudokuGrid,
        maxRemove: Int
    ): Pair<SudokuGrid, Int> {
        val n = solution.size
        val positions = ArrayList<Pair<Int, Int>>(n * n)
        for (r in 0 until n) for (c in 0 until n) positions.add(r to c)
        positions.shuffle(rng)

        var current = solution
        var removed = 0

        for ((r, c) in positions) {
            if (removed >= maxRemove) break
            if (current[r, c] == 0) continue

            val candidate = current.set(r, c, 0)

            // Must remain uniquely solvable
            if (SudokuSolver.countSolutions(candidate, 2) != 1) continue

            // The emptied cell must be deducible without guessing
            if (!SudokuSolver.isLogicallyDeducible(candidate, r, c)) continue

            current = candidate
            removed++
        }

        return current to removed
    }
}
