package com.morro.alarm.puzzle.chess

/**
 * A chess move: origin square, destination square, and optional promotion piece type.
 * UCI format: "e2e4" (normal) or "e7e8q" (promotion).
 */
data class ChessMove(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null
) {
    /** Serialize to UCI string, e.g. "e2e4" or "e7e8q". */
    fun toUci(): String = buildString {
        append(from.toAlgebraic())
        append(to.toAlgebraic())
        if (promotion != null) {
            append(
                when (promotion) {
                    PieceType.QUEEN  -> 'q'
                    PieceType.ROOK   -> 'r'
                    PieceType.BISHOP -> 'b'
                    PieceType.KNIGHT -> 'n'
                    else -> 'q'
                }
            )
        }
    }

    override fun toString(): String = toUci()

    companion object {
        /**
         * Parse a UCI move string (4 or 5 chars). Returns null on malformed input.
         * Examples: "e2e4", "e7e8q"
         */
        fun fromUci(uci: String): ChessMove? {
            if (uci.length !in 4..5) return null
            val from = Square.fromAlgebraic(uci.substring(0, 2)) ?: return null
            val to = Square.fromAlgebraic(uci.substring(2, 4)) ?: return null
            val promotion = if (uci.length == 5) {
                when (uci[4].lowercaseChar()) {
                    'q' -> PieceType.QUEEN
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    else -> return null
                }
            } else null
            return ChessMove(from, to, promotion)
        }
    }
}
