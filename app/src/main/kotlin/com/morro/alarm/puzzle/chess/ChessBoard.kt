package com.morro.alarm.puzzle.chess

/**
 * Chess piece color.
 */
enum class Color {
    WHITE, BLACK;

    fun opposite(): Color = if (this == WHITE) BLACK else WHITE
}

/**
 * Chess piece type.
 */
enum class PieceType {
    PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING
}

/**
 * A piece on the board: type + color.
 */
data class Piece(val type: PieceType, val color: Color) {
    /** FEN char: uppercase = white, lowercase = black. */
    fun toFenChar(): Char {
        val c = when (type) {
            PieceType.PAWN   -> 'p'
            PieceType.KNIGHT -> 'n'
            PieceType.BISHOP -> 'b'
            PieceType.ROOK   -> 'r'
            PieceType.QUEEN  -> 'q'
            PieceType.KING   -> 'k'
        }
        return if (color == Color.WHITE) c.uppercaseChar() else c
    }

    companion object {
        fun fromFenChar(c: Char): Piece? {
            val type = when (c.lowercaseChar()) {
                'p' -> PieceType.PAWN
                'n' -> PieceType.KNIGHT
                'b' -> PieceType.BISHOP
                'r' -> PieceType.ROOK
                'q' -> PieceType.QUEEN
                'k' -> PieceType.KING
                else -> return null
            }
            val color = if (c.isUpperCase()) Color.WHITE else Color.BLACK
            return Piece(type, color)
        }
    }
}

/**
 * A square on the board identified by file (0=a..7=h) and rank (0=rank1..7=rank8).
 * Index = rank * 8 + file (0..63, a1=0, h8=63).
 */
data class Square(val file: Int, val rank: Int) {

    /** 0-based index a1=0, b1=1, ..., h8=63. */
    val index: Int get() = rank * 8 + file

    fun toAlgebraic(): String = "${'a' + file}${'1' + rank}"

    override fun toString(): String = toAlgebraic()

    companion object {
        fun fromIndex(index: Int): Square = Square(index % 8, index / 8)

        fun fromAlgebraic(s: String): Square? {
            if (s.length != 2) return null
            val file = s[0] - 'a'
            val rank = s[1] - '1'
            if (file !in 0..7 || rank !in 0..7) return null
            return Square(file, rank)
        }

        /** Check if file/rank are on the board. */
        fun isValid(file: Int, rank: Int): Boolean = file in 0..7 && rank in 0..7
    }
}

/**
 * Castling rights: each flag independently tracks whether that castling is still potentially legal.
 */
data class CastlingRights(
    val whiteKingside: Boolean = false,
    val whiteQueenside: Boolean = false,
    val blackKingside: Boolean = false,
    val blackQueenside: Boolean = false
) {
    fun toFen(): String {
        val sb = StringBuilder()
        if (whiteKingside)  sb.append('K')
        if (whiteQueenside) sb.append('Q')
        if (blackKingside)  sb.append('k')
        if (blackQueenside) sb.append('q')
        return if (sb.isEmpty()) "-" else sb.toString()
    }

    companion object {
        fun fromFen(s: String): CastlingRights? {
            if (s == "-") return CastlingRights()
            // validate: only K, Q, k, q allowed, no duplicates, order must be KQkq
            val allowed = "KQkq"
            var lastIndex = -1
            for (c in s) {
                val idx = allowed.indexOf(c)
                if (idx == -1) return null
                if (idx <= lastIndex) return null  // out of order or duplicate
                lastIndex = idx
            }
            return CastlingRights(
                whiteKingside  = 'K' in s,
                whiteQueenside = 'Q' in s,
                blackKingside  = 'k' in s,
                blackQueenside = 'q' in s
            )
        }
    }
}

/**
 * Immutable chess board state.
 *
 * pieces: Array of 64 nullable Pieces (index = rank*8 + file, a1=0).
 * sideToMove, castling, enPassant target square, halfmove clock, fullmove number.
 */
data class ChessBoard(
    private val pieces: List<Piece?>,
    val sideToMove: Color,
    val castling: CastlingRights,
    val enPassantTarget: Square?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int
) {
    init {
        require(pieces.size == 64) { "Board must have exactly 64 squares" }
    }

    /** Get the piece at a square, or null if empty. */
    operator fun get(square: Square): Piece? = pieces[square.index]

    /** Get the piece at an index (0..63). */
    operator fun get(index: Int): Piece? = pieces[index]

    /** Return a copy of the internal pieces list. */
    fun piecesAsList(): List<Piece?> = pieces.toList()

    /** Find the square of the king for the given color, or null if missing (shouldn't happen in valid boards). */
    fun kingSquare(color: Color): Square? {
        val kingPiece = Piece(PieceType.KING, color)
        for (i in 0..63) {
            if (pieces[i] == kingPiece) return Square.fromIndex(i)
        }
        return null
    }

    /**
     * Apply a move and return a new board. This does NOT validate legality — the caller
     * (ChessRules) must ensure the move is legal before calling this.
     *
     * Handles: normal moves, captures, castling (rook movement), en passant capture,
     * pawn double push (setting en passant square), promotion.
     */
    fun apply(move: ChessMove): ChessBoard {
        val mutablePieces = pieces.toMutableList()
        val movingPiece = mutablePieces[move.from.index]
            ?: throw IllegalArgumentException("No piece on origin square ${move.from.toAlgebraic()}")
        val capturedPiece = mutablePieces[move.to.index]

        // Move the piece
        mutablePieces[move.from.index] = null
        mutablePieces[move.to.index] = if (move.promotion != null) {
            Piece(move.promotion, movingPiece.color)
        } else {
            movingPiece
        }

        // --- En passant capture ---
        var newEnPassant: Square? = null
        if (movingPiece.type == PieceType.PAWN && move.to == enPassantTarget) {
            // Remove the captured pawn (it's on the same file as destination, same rank as origin)
            val capturedPawnIndex = Square(move.to.file, move.from.rank).index
            mutablePieces[capturedPawnIndex] = null
        }

        // --- Pawn double push → set en passant target ---
        if (movingPiece.type == PieceType.PAWN) {
            val rankDiff = move.to.rank - move.from.rank
            if (rankDiff == 2 || rankDiff == -2) {
                val epRank = (move.from.rank + move.to.rank) / 2
                newEnPassant = Square(move.from.file, epRank)
            }
        }

        // --- Castling: move the rook ---
        if (movingPiece.type == PieceType.KING) {
            val fileDiff = move.to.file - move.from.file
            if (fileDiff == 2) {
                // Kingside castle
                val rookFrom = Square(7, move.from.rank).index
                val rookTo = Square(5, move.from.rank).index
                mutablePieces[rookTo] = mutablePieces[rookFrom]
                mutablePieces[rookFrom] = null
            } else if (fileDiff == -2) {
                // Queenside castle
                val rookFrom = Square(0, move.from.rank).index
                val rookTo = Square(3, move.from.rank).index
                mutablePieces[rookTo] = mutablePieces[rookFrom]
                mutablePieces[rookFrom] = null
            }
        }

        // --- Update castling rights ---
        var newCastling = castling
        // King moved → lose both sides
        if (movingPiece.type == PieceType.KING) {
            newCastling = if (movingPiece.color == Color.WHITE) {
                newCastling.copy(whiteKingside = false, whiteQueenside = false)
            } else {
                newCastling.copy(blackKingside = false, blackQueenside = false)
            }
        }
        // Rook moved or captured → lose that side
        // White rooks
        if (move.from == Square(0, 0) || move.to == Square(0, 0)) {
            newCastling = newCastling.copy(whiteQueenside = false)
        }
        if (move.from == Square(7, 0) || move.to == Square(7, 0)) {
            newCastling = newCastling.copy(whiteKingside = false)
        }
        // Black rooks
        if (move.from == Square(0, 7) || move.to == Square(0, 7)) {
            newCastling = newCastling.copy(blackQueenside = false)
        }
        if (move.from == Square(7, 7) || move.to == Square(7, 7)) {
            newCastling = newCastling.copy(blackKingside = false)
        }

        // --- Halfmove clock ---
        val newHalfmove = if (movingPiece.type == PieceType.PAWN || capturedPiece != null) {
            0
        } else {
            halfmoveClock + 1
        }

        // --- Fullmove number ---
        val newFullmove = if (sideToMove == Color.BLACK) fullmoveNumber + 1 else fullmoveNumber

        return ChessBoard(
            pieces = mutablePieces.toList(),
            sideToMove = sideToMove.opposite(),
            castling = newCastling,
            enPassantTarget = newEnPassant,
            halfmoveClock = newHalfmove,
            fullmoveNumber = newFullmove
        )
    }

    companion object {
        /** An empty board. */
        fun empty(): ChessBoard = ChessBoard(
            pieces = List(64) { null },
            sideToMove = Color.WHITE,
            castling = CastlingRights(),
            enPassantTarget = null,
            halfmoveClock = 0,
            fullmoveNumber = 1
        )

        /** Standard starting position. */
        fun startingPosition(): ChessBoard =
            FenParser.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
                .getOrThrow()
    }
}
