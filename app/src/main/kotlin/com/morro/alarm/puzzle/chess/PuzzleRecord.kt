package com.morro.alarm.puzzle.chess

/**
 * One Lichess puzzle, parsed from the bundled gzipped CSV.
 *
 * @property puzzleId  Lichess puzzle ID (e.g. "00sHx").
 * @property fen       FEN of the position BEFORE the opponent's setup move.
 * @property moves     Space-separated UCI moves. moves[0] is the opponent's
 *                     setup move; the solver's first answer is moves[1].
 * @property rating    Lichess puzzle rating (Glicko-2).
 * @property themes    Space-separated Lichess theme tags (e.g. "mateIn1 short").
 */
data class PuzzleRecord(
    val puzzleId: String,
    val fen: String,
    val moves: String,
    val rating: Int,
    val themes: String
)
