package com.morro.alarm.puzzle.chess

/**
 * FEN parser and serializer.
 * Uses kotlin.Result to signal errors — never throws.
 */
object FenParser {

    /**
     * Parse a FEN string into a ChessBoard.
     * Returns Result.failure with a descriptive message on malformed input.
     */
    fun parse(fen: String): Result<ChessBoard> = runCatching {
        val parts = fen.trim().split(" ")
        if (parts.size != 6) {
            return Result.failure(IllegalArgumentException("FEN must have 6 space-separated fields, got ${parts.size}"))
        }

        // 1. Piece placement
        val pieces = parsePlacement(parts[0])
            ?: return Result.failure(IllegalArgumentException("Invalid piece placement: ${parts[0]}"))

        // 2. Side to move
        val sideToMove = when (parts[1]) {
            "w" -> Color.WHITE
            "b" -> Color.BLACK
            else -> return Result.failure(IllegalArgumentException("Invalid side to move: ${parts[1]}"))
        }

        // 3. Castling
        val castling = CastlingRights.fromFen(parts[2])
            ?: return Result.failure(IllegalArgumentException("Invalid castling rights: ${parts[2]}"))

        // 4. En passant
        val enPassant = if (parts[3] == "-") {
            null
        } else {
            Square.fromAlgebraic(parts[3])
                ?: return Result.failure(IllegalArgumentException("Invalid en passant square: ${parts[3]}"))
        }

        // 5. Halfmove clock
        val halfmove = parts[4].toIntOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid halfmove clock: ${parts[4]}"))
        if (halfmove < 0) {
            return Result.failure(IllegalArgumentException("Halfmove clock must be non-negative: $halfmove"))
        }

        // 6. Fullmove number
        val fullmove = parts[5].toIntOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid fullmove number: ${parts[5]}"))
        if (fullmove < 1) {
            return Result.failure(IllegalArgumentException("Fullmove number must be >= 1: $fullmove"))
        }

        // 7. Structural chess legality checks
        var whiteKings = 0
        var blackKings = 0
        for (i in 0..63) {
            val piece = pieces[i] ?: continue
            val rank = i / 8
            if (piece.type == PieceType.KING) {
                if (piece.color == Color.WHITE) whiteKings++ else blackKings++
            } else if (piece.type == PieceType.PAWN && (rank == 0 || rank == 7)) {
                return Result.failure(IllegalArgumentException("Pawn cannot stand on rank ${rank + 1}"))
            }
        }
        if (whiteKings != 1 || blackKings != 1) {
            return Result.failure(IllegalArgumentException("FEN must have exactly 1 White king and 1 Black king (got W=$whiteKings, B=$blackKings)"))
        }

        // Validate castling rights against actual king and rook squares
        if (castling.whiteKingside && (pieces[Square(4, 0).index] != Piece(PieceType.KING, Color.WHITE) ||
                pieces[Square(7, 0).index] != Piece(PieceType.ROOK, Color.WHITE))) {
            return Result.failure(IllegalArgumentException("Invalid white kingside castling right"))
        }
        if (castling.whiteQueenside && (pieces[Square(4, 0).index] != Piece(PieceType.KING, Color.WHITE) ||
                pieces[Square(0, 0).index] != Piece(PieceType.ROOK, Color.WHITE))) {
            return Result.failure(IllegalArgumentException("Invalid white queenside castling right"))
        }
        if (castling.blackKingside && (pieces[Square(4, 7).index] != Piece(PieceType.KING, Color.BLACK) ||
                pieces[Square(7, 7).index] != Piece(PieceType.ROOK, Color.BLACK))) {
            return Result.failure(IllegalArgumentException("Invalid black kingside castling right"))
        }
        if (castling.blackQueenside && (pieces[Square(4, 7).index] != Piece(PieceType.KING, Color.BLACK) ||
                pieces[Square(0, 7).index] != Piece(PieceType.ROOK, Color.BLACK))) {
            return Result.failure(IllegalArgumentException("Invalid black queenside castling right"))
        }

        // Validate en passant target square consistency
        if (enPassant != null) {
            val expectedEpRank = if (sideToMove == Color.WHITE) 5 else 2
            val pawnRank = if (sideToMove == Color.WHITE) 4 else 3
            val originRank = if (sideToMove == Color.WHITE) 6 else 1
            val expectedEnemyPawn = Piece(PieceType.PAWN, sideToMove.opposite())
            if (enPassant.rank != expectedEpRank ||
                pieces[enPassant.index] != null ||
                pieces[Square(enPassant.file, originRank).index] != null ||
                pieces[Square(enPassant.file, pawnRank).index] != expectedEnemyPawn
            ) {
                return Result.failure(IllegalArgumentException("Invalid en passant square for board state: ${enPassant.toAlgebraic()}"))
            }
        }

        val board = ChessBoard(
            pieces = pieces,
            sideToMove = sideToMove,
            castling = castling,
            enPassantTarget = enPassant,
            halfmoveClock = halfmove,
            fullmoveNumber = fullmove
        )

        // Side NOT to move cannot already be in check
        if (ChessRules.isCheck(board, sideToMove.opposite())) {
            return Result.failure(IllegalArgumentException("Non-moving king is already in check"))
        }

        board
    }

    /**
     * Serialize a ChessBoard to a FEN string.
     */
    fun toFen(board: ChessBoard): String = buildString {
        // 1. Piece placement (rank 8 down to rank 1)
        for (rank in 7 downTo 0) {
            var emptyCount = 0
            for (file in 0..7) {
                val piece = board[Square(file, rank)]
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        append(emptyCount)
                        emptyCount = 0
                    }
                    append(piece.toFenChar())
                }
            }
            if (emptyCount > 0) append(emptyCount)
            if (rank > 0) append('/')
        }

        // 2. Side to move
        append(' ')
        append(if (board.sideToMove == Color.WHITE) 'w' else 'b')

        // 3. Castling
        append(' ')
        append(board.castling.toFen())

        // 4. En passant
        append(' ')
        append(board.enPassantTarget?.toAlgebraic() ?: "-")

        // 5. Halfmove clock
        append(' ')
        append(board.halfmoveClock)

        // 6. Fullmove number
        append(' ')
        append(board.fullmoveNumber)
    }

    /**
     * Parse the piece placement field (rank8/rank7/.../rank1).
     * Returns a list of 64 nullable Pieces (index = rank*8+file), or null on error.
     */
    private fun parsePlacement(placement: String): List<Piece?>? {
        val ranks = placement.split("/")
        if (ranks.size != 8) return null

        val pieces = MutableList<Piece?>(64) { null }

        for ((rankIdx, rankStr) in ranks.withIndex()) {
            val rank = 7 - rankIdx  // FEN starts with rank 8
            var file = 0
            var prevDigit = false
            for (c in rankStr) {
                if (c.isDigit()) {
                    if (prevDigit) return null // Reject consecutive digits like "44"
                    val skip = c - '0'
                    if (skip < 1 || skip > 8) return null
                    file += skip
                    prevDigit = true
                } else {
                    prevDigit = false
                    if (file > 7) return null
                    val piece = Piece.fromFenChar(c) ?: return null
                    pieces[rank * 8 + file] = piece
                    file++
                }
            }
            if (file != 8) return null  // rank must cover exactly 8 files
        }

        return pieces.toList()
    }
}
