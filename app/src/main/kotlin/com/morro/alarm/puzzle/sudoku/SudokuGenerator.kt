package com.morro.alarm.puzzle.sudoku

import kotlin.random.Random

/**
 * Wake-up calibrated Sudoku tiers.
 *
 * - [EASY]: 4×4 grid, 2×2 boxes, digits 1–4, 6 cells removed (target <= 20s).
 * - [MEDIUM]: 9×9 grid, 3×3 boxes, digits 1–9, 6 cells removed (target <= 45s).
 * - [HARD]: 9×9 grid, 3×3 boxes, digits 1–9, 10–12 cells removed (target <= 90s).
 */
enum class SudokuMode(val label: String, val detail: String) {
    EASY("Easy", "4×4 · 6 blanks"),
    MEDIUM("Medium", "9×9 · 6 blanks"),
    HARD("Hard", "9×9 · 10–12 blanks");

    companion object {
        /** Legacy alias preserved for existing tests targeting 4×4 quick solve. */
        val STANDARD: SudokuMode get() = EASY
    }
}

/**
 * A generated Sudoku puzzle with its unique solution.
 */
data class SudokuPuzzle(
    val grid: SudokuGrid,
    val solution: SudokuGrid,
    val mode: SudokuMode,
    val seed: Long
)

/**
 * Deterministic Sudoku generator. Same seed + same mode -> identical puzzle.
 * Every puzzle has a unique solution and is 100% solvable using naked/hidden singles.
 */
object SudokuGenerator {

    private const val MAX_GRID_ATTEMPTS = 24
    private const val SHUFFLES_PER_GRID = 4

    /**
     * Generates a puzzle for [mode] deterministically from [seed].
     */
    fun generate(seed: Long, mode: SudokuMode): SudokuPuzzle {
        val rng = Random(seed)
        val params = when (mode) {
            SudokuMode.EASY -> GridParams(4, 2, 2, minRemove = 6, maxRemove = 6)
            SudokuMode.MEDIUM -> GridParams(9, 3, 3, minRemove = 6, maxRemove = 6)
            SudokuMode.HARD -> GridParams(9, 3, 3, minRemove = 10, maxRemove = 12)
        }

        var bestPuzzle: SudokuGrid? = null
        var bestSolution: SudokuGrid? = null
        var bestRemoved = -1

        for (attempt in 0 until MAX_GRID_ATTEMPTS) {
            val solution = generateFullGrid(rng, params.size, params.boxRows, params.boxCols)
            for (shuffleTry in 0 until SHUFFLES_PER_GRID) {
                val targetRemove = if (params.minRemove == params.maxRemove) {
                    params.maxRemove
                } else {
                    rng.nextInt(params.minRemove, params.maxRemove + 1)
                }
                val (candidate, removed) = removeCells(rng, solution, targetRemove)
                if (removed >= params.minRemove &&
                    SudokuSolver.countSolutions(candidate, 2) == 1 &&
                    SudokuSolver.isLogicallySolvable(candidate)
                ) {
                    return SudokuPuzzle(
                        grid = candidate,
                        solution = solution,
                        mode = mode,
                        seed = seed
                    )
                }
                if (removed > bestRemoved &&
                    SudokuSolver.countSolutions(candidate, 2) == 1 &&
                    SudokuSolver.isLogicallySolvable(candidate)
                ) {
                    bestRemoved = removed
                    bestPuzzle = candidate
                    bestSolution = solution
                }
            }
        }

        val fallbackSolution = bestSolution ?: generateFullGrid(rng, params.size, params.boxRows, params.boxCols)
        val fallbackGrid = bestPuzzle ?: removeCells(rng, fallbackSolution, params.minRemove).first
        return SudokuPuzzle(
            grid = fallbackGrid,
            solution = fallbackSolution,
            mode = mode,
            seed = seed
        )
    }

    private data class GridParams(
        val size: Int,
        val boxRows: Int,
        val boxCols: Int,
        val minRemove: Int,
        val maxRemove: Int
    )

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

    private fun removeCells(
        rng: Random,
        solution: SudokuGrid,
        targetRemove: Int
    ): Pair<SudokuGrid, Int> {
        val n = solution.size
        val positions = ArrayList<Pair<Int, Int>>(n * n)
        for (r in 0 until n) for (c in 0 until n) positions.add(r to c)
        positions.shuffle(rng)

        var current = solution
        var removed = 0

        for ((r, c) in positions) {
            if (removed >= targetRemove) break
            if (current[r, c] == 0) continue

            val candidate = current.set(r, c, 0)
            if (SudokuSolver.countSolutions(candidate, 2) != 1) continue
            if (!SudokuSolver.isLogicallyDeducible(candidate, r, c)) continue

            current = candidate
            removed++
        }

        return current to removed
    }
}
