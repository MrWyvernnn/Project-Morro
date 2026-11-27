package com.morro.alarm.puzzle.sudoku

/**
 * Deterministic Sudoku solver using bitmask constraint tracking and MRV ordering.
 * Also provides logical-deduction checks (naked-single, hidden-single).
 */
object SudokuSolver {

    /**
     * Count solutions up to [limit], with early exit.
     * Validates pre-filled givens upfront: invalid or conflicting givens return 0.
     * Returns 0 if [limit] <= 0.
     */
    fun countSolutions(grid: SudokuGrid, limit: Int = 2): Int {
        if (limit <= 0) return 0
        return search(grid, limit = limit, captureFirst = null)
    }

    /**
     * Solve the grid. Returns the unique solution, or null if zero, conflicting, or multiple solutions.
     */
    fun solve(grid: SudokuGrid): SudokuGrid? {
        val n = grid.size
        val captured = Array(n) { IntArray(n) }
        val count = search(grid, limit = 2, captureFirst = captured)
        return if (count == 1) SudokuGrid(n, grid.boxRows, grid.boxCols, captured) else null
    }

    /**
     * True if the cell at [row],[col] can be deduced by either:
     * - Naked single: only one candidate value is possible for the cell.
     * - Hidden single: the cell is the only place in its row, column, or box
     *   where a particular value can go.
     *
     * No guessing, no backtracking — purely logical deduction a human can do.
     */
    fun isLogicallyDeducible(grid: SudokuGrid, row: Int, col: Int): Boolean {
        require(grid[row, col] == 0) { "Cell ($row,$col) is not empty" }
        val n = grid.size
        val boxRows = grid.boxRows
        val boxCols = grid.boxCols
        val fullMask = (1 shl n) - 1

        val rowUsed = IntArray(n)
        val colUsed = IntArray(n)
        val boxUsed = IntArray(n)

        for (r in 0 until n) {
            val brBase = (r / boxRows) * boxRows
            for (c in 0 until n) {
                val v = grid[r, c]
                if (v == 0) continue
                if (v !in 1..n) return false
                val bit = 1 shl (v - 1)
                val b = brBase + (c / boxCols)
                if ((rowUsed[r] and bit) != 0 || (colUsed[c] and bit) != 0 || (boxUsed[b] and bit) != 0) {
                    return false
                }
                rowUsed[r] = rowUsed[r] or bit
                colUsed[c] = colUsed[c] or bit
                boxUsed[b] = boxUsed[b] or bit
            }
        }

        fun cellCandidatesMask(r: Int, c: Int): Int {
            val b = (r / boxRows) * boxRows + (c / boxCols)
            return fullMask and (rowUsed[r] or colUsed[c] or boxUsed[b]).inv()
        }

        val candidates = cellCandidatesMask(row, col)
        if (candidates == 0) return false

        // Naked single: exactly one bit set
        if ((candidates and (candidates - 1)) == 0) return true

        // Hidden single in row: bits present in candidates that appear in NO empty peer in row
        var rowPeerUnion = 0
        for (c in 0 until n) {
            if (c != col && grid[row, c] == 0) {
                rowPeerUnion = rowPeerUnion or cellCandidatesMask(row, c)
            }
        }
        if ((candidates and rowPeerUnion.inv()) != 0) return true

        // Hidden single in column
        var colPeerUnion = 0
        for (r in 0 until n) {
            if (r != row && grid[r, col] == 0) {
                colPeerUnion = colPeerUnion or cellCandidatesMask(r, col)
            }
        }
        if ((candidates and colPeerUnion.inv()) != 0) return true

        // Hidden single in box
        var boxPeerUnion = 0
        val br = (row / boxRows) * boxRows
        val bc = (col / boxCols) * boxCols
        for (r in br until br + boxRows) {
            for (c in bc until bc + boxCols) {
                if ((r != row || c != col) && grid[r, c] == 0) {
                    boxPeerUnion = boxPeerUnion or cellCandidatesMask(r, c)
                }
            }
        }
        return (candidates and boxPeerUnion.inv()) != 0
    }

    /**
     * Simulates a human solver using only naked-single and hidden-single rules (no guessing or
     * backtracking). Returns `true` only if every empty cell in [grid] can be filled in sequence
     * without ambiguity.
     */
    fun isLogicallySolvable(grid: SudokuGrid): Boolean {
        var current = grid
        val remaining = current.emptyCells().toMutableSet()
        while (remaining.isNotEmpty()) {
            var progressed = false
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val (r, c) = iterator.next()
                if (isLogicallyDeducible(current, r, c)) {
                    val candidates = candidatesFor(current, r, c)
                    val value = when {
                        candidates.size == 1 -> candidates.first()
                        else -> uniqueHiddenValue(current, r, c, candidates) ?: continue
                    }
                    current = current.set(r, c, value)
                    iterator.remove()
                    progressed = true
                }
            }
            if (!progressed) return false
        }
        return SudokuGrid.isSolved(current)
    }

    private fun uniqueHiddenValue(
        grid: SudokuGrid,
        row: Int,
        col: Int,
        candidates: Set<Int>
    ): Int? {
        val n = grid.size
        // Check row hidden single
        for (v in candidates) {
            var rowUnique = true
            for (c in 0 until n) {
                if (c != col && grid[row, c] == 0 && v in candidatesFor(grid, row, c)) {
                    rowUnique = false
                    break
                }
            }
            if (rowUnique) return v

            var colUnique = true
            for (r in 0 until n) {
                if (r != row && grid[r, col] == 0 && v in candidatesFor(grid, r, col)) {
                    colUnique = false
                    break
                }
            }
            if (colUnique) return v

            var boxUnique = true
            val br = (row / grid.boxRows) * grid.boxRows
            val bc = (col / grid.boxCols) * grid.boxCols
            for (r in br until br + grid.boxRows) {
                for (c in bc until bc + grid.boxCols) {
                    if ((r != row || c != col) && grid[r, c] == 0 && v in candidatesFor(grid, r, c)) {
                        boxUnique = false
                        break
                    }
                }
                if (!boxUnique) break
            }
            if (boxUnique) return v
        }
        return null
    }

    /** All candidate values for an empty cell. */
    internal fun candidatesFor(grid: SudokuGrid, row: Int, col: Int): Set<Int> {
        val n = grid.size
        var usedMask = 0

        for (c in 0 until n) {
            val v = grid[row, c]
            if (v in 1..n) usedMask = usedMask or (1 shl (v - 1))
        }
        for (r in 0 until n) {
            val v = grid[r, col]
            if (v in 1..n) usedMask = usedMask or (1 shl (v - 1))
        }
        val br = (row / grid.boxRows) * grid.boxRows
        val bc = (col / grid.boxCols) * grid.boxCols
        for (r in br until br + grid.boxRows) {
            for (c in bc until bc + grid.boxCols) {
                val v = grid[r, c]
                if (v in 1..n) usedMask = usedMask or (1 shl (v - 1))
            }
        }

        val result = mutableSetOf<Int>()
        for (v in 1..n) {
            if ((usedMask and (1 shl (v - 1))) == 0) result.add(v)
        }
        return result
    }

    // --- Unified bitmask + MRV backtracker ---

    private fun search(
        grid: SudokuGrid,
        limit: Int,
        captureFirst: Array<IntArray>?
    ): Int {
        val n = grid.size
        if (n <= 0 || n > 30) return 0
        val boxRows = grid.boxRows
        val boxCols = grid.boxCols
        val fullMask = (1 shl n) - 1

        val board = Array(n) { IntArray(n) }
        val rowMask = IntArray(n)
        val colMask = IntArray(n)
        val boxMask = IntArray(n)
        var emptyCount = 0

        // Validate all pre-filled givens and populate bitmasks
        for (r in 0 until n) {
            val brBase = (r / boxRows) * boxRows
            for (c in 0 until n) {
                val v = grid[r, c]
                board[r][c] = v
                if (v == 0) {
                    emptyCount++
                    continue
                }
                if (v !in 1..n) return 0
                val bit = 1 shl (v - 1)
                val b = brBase + (c / boxCols)
                if ((rowMask[r] and bit) != 0 || (colMask[c] and bit) != 0 || (boxMask[b] and bit) != 0) {
                    return 0
                }
                rowMask[r] = rowMask[r] or bit
                colMask[c] = colMask[c] or bit
                boxMask[b] = boxMask[b] or bit
            }
        }

        var solutions = 0

        fun backtrack(remaining: Int): Boolean {
            if (remaining == 0) {
                if (solutions == 0 && captureFirst != null) {
                    for (r in 0 until n) {
                        board[r].copyInto(captureFirst[r])
                    }
                }
                solutions++
                return solutions >= limit
            }

            // Pick empty cell with Minimum Remaining Values (MRV)
            var bestR = -1
            var bestC = -1
            var bestBox = -1
            var bestCandMask = 0
            var bestCount = Int.MAX_VALUE

            for (r in 0 until n) {
                val brBase = (r / boxRows) * boxRows
                for (c in 0 until n) {
                    if (board[r][c] != 0) continue
                    val b = brBase + (c / boxCols)
                    val candMask = fullMask and (rowMask[r] or colMask[c] or boxMask[b]).inv()
                    val count = Integer.bitCount(candMask)
                    if (count == 0) return false
                    if (count < bestCount) {
                        bestCount = count
                        bestR = r
                        bestC = c
                        bestBox = b
                        bestCandMask = candMask
                        if (count == 1) break
                    }
                }
                if (bestCount == 1) break
            }

            var mask = bestCandMask
            while (mask != 0) {
                val bit = mask and -mask
                mask = mask xor bit
                val digit = Integer.numberOfTrailingZeros(bit) + 1

                board[bestR][bestC] = digit
                rowMask[bestR] = rowMask[bestR] or bit
                colMask[bestC] = colMask[bestC] or bit
                boxMask[bestBox] = boxMask[bestBox] or bit

                val stop = backtrack(remaining - 1)

                board[bestR][bestC] = 0
                rowMask[bestR] = rowMask[bestR] xor bit
                colMask[bestC] = colMask[bestC] xor bit
                boxMask[bestBox] = boxMask[bestBox] xor bit

                if (stop) return true
            }
            return false
        }

        backtrack(emptyCount)
        return solutions
    }
}
