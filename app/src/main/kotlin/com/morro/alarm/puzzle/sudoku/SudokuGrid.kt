package com.morro.alarm.puzzle.sudoku

/**
 * Immutable 2D Sudoku grid. Supports 4×4 (2×2 boxes) and 9×9 (3×3 boxes).
 * Cell value 0 means empty.
 */
data class SudokuGrid(
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
    val cells: Array<IntArray>
) {
    init {
        require(size > 0) { "size must be positive" }
        require(boxRows > 0) { "boxRows must be positive" }
        require(boxCols > 0) { "boxCols must be positive" }
        require(size == boxRows * boxCols) { "size must equal boxRows * boxCols" }
        require(cells.size == size) { "cells must have $size rows" }
        cells.forEach { row ->
            require(row.size == size) { "each row must have $size columns" }
            for (v in row) {
                require(v in 0..size) { "cell value $v out of range 0..$size" }
            }
        }
    }

    /** Get the value at [row],[col]. 0 = empty. */
    operator fun get(row: Int, col: Int): Int {
        require(row in 0 until size && col in 0 until size) { "index ($row,$col) out of bounds for size $size" }
        return cells[row][col]
    }

    /** Returns a new grid with [value] placed at [row],[col]. */
    fun set(row: Int, col: Int, value: Int): SudokuGrid {
        require(row in 0 until size && col in 0 until size) { "index ($row,$col) out of bounds for size $size" }
        require(value in 0..size) { "value $value out of range 0..$size" }
        val newCells = Array(size) { r -> cells[r].copyOf() }
        newCells[row][col] = value
        return copy(cells = newCells)
    }

    /** All positions where the cell is empty (value == 0). */
    fun emptyCells(): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (cells[r][c] == 0) result.add(r to c)
            }
        }
        return result
    }

    /** Deep-copy equality because Array doesn't do structural equals by default. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SudokuGrid) return false
        if (size != other.size || boxRows != other.boxRows || boxCols != other.boxCols) return false
        for (r in 0 until size) {
            if (!cells[r].contentEquals(other.cells[r])) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var h = size * 31 + boxRows * 17 + boxCols
        for (r in 0 until size) h = h * 31 + cells[r].contentHashCode()
        return h
    }

    override fun toString(): String = buildString {
        for (r in 0 until size) {
            appendLine(cells[r].joinToString(" ") { if (it == 0) "." else it.toString() })
        }
    }

    companion object {
        /** Create an empty grid of the given dimensions. */
        fun empty(size: Int, boxRows: Int, boxCols: Int): SudokuGrid {
            require(size == boxRows * boxCols)
            return SudokuGrid(size, boxRows, boxCols, Array(size) { IntArray(size) })
        }

        /** Set of cells that have row, column, or box duplicates among filled cells. */
        fun conflicts(grid: SudokuGrid): Set<Pair<Int, Int>> {
            val bad = mutableSetOf<Pair<Int, Int>>()
            val n = grid.size

            // Row duplicates
            for (r in 0 until n) {
                val seen = mutableMapOf<Int, MutableList<Int>>()
                for (c in 0 until n) {
                    val v = grid[r, c]
                    if (v != 0) seen.getOrPut(v) { mutableListOf() }.add(c)
                }
                for ((_, cols) in seen) {
                    if (cols.size > 1) cols.forEach { c -> bad.add(r to c) }
                }
            }

            // Column duplicates
            for (c in 0 until n) {
                val seen = mutableMapOf<Int, MutableList<Int>>()
                for (r in 0 until n) {
                    val v = grid[r, c]
                    if (v != 0) seen.getOrPut(v) { mutableListOf() }.add(r)
                }
                for ((_, rows) in seen) {
                    if (rows.size > 1) rows.forEach { r -> bad.add(r to c) }
                }
            }

            // Box duplicates
            for (br in 0 until n step grid.boxRows) {
                for (bc in 0 until n step grid.boxCols) {
                    val seen = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()
                    for (r in br until br + grid.boxRows) {
                        for (c in bc until bc + grid.boxCols) {
                            val v = grid[r, c]
                            if (v != 0) seen.getOrPut(v) { mutableListOf() }.add(r to c)
                        }
                    }
                    for ((_, positions) in seen) {
                        if (positions.size > 1) positions.forEach { bad.add(it) }
                    }
                }
            }

            return bad
        }

        /** True if every cell is filled (no zeros). */
        fun isComplete(grid: SudokuGrid): Boolean {
            for (r in 0 until grid.size) {
                for (c in 0 until grid.size) {
                    if (grid[r, c] == 0) return false
                }
            }
            return true
        }

        /** True if complete AND no conflicts. */
        fun isSolved(grid: SudokuGrid): Boolean = isComplete(grid) && conflicts(grid).isEmpty()
    }
}
