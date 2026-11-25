package com.morro.alarm.puzzle.chess

/**
 * Full legal move generation and game-state queries.
 *
 * Strategy: generate pseudo-legal moves, then filter out any move that leaves
 * the mover's own king in check. This guarantees correctness — a false rejection
 * at 6am is unforgivable.
 */
object ChessRules {

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Is [side]'s king currently in check on [board]?
     */
    fun isCheck(board: ChessBoard, side: Color): Boolean {
        val kingSquare = board.kingSquare(side) ?: return false
        return isSquareAttackedBy(board, kingSquare, side.opposite())
    }

    /**
     * Is [side] in checkmate? (In check AND no legal moves.)
     */
    fun isCheckmate(board: ChessBoard, side: Color): Boolean {
        return isCheck(board, side) && !hasAnyLegalMove(board, side)
    }

    /**
     * Is [side] in stalemate? (NOT in check AND no legal moves.)
     */
    fun isStalemate(board: ChessBoard, side: Color): Boolean {
        return !isCheck(board, side) && !hasAnyLegalMove(board, side)
    }

    /**
     * Fast short-circuit check for whether [side] has at least one legal move.
     */
    fun hasAnyLegalMove(board: ChessBoard, side: Color = board.sideToMove): Boolean {
        val pseudoLegal = generatePseudoLegalMoves(board, side)
        for (move in pseudoLegal) {
            val after = board.apply(move)
            val kingSq = after.kingSquare(side) ?: continue
            if (!isSquareAttackedBy(after, kingSq, side.opposite())) {
                return true
            }
        }
        return false
    }

    /**
     * Fast short-circuit check for whether the side to move has an immediate mate-in-1.
     */
    fun hasMateInOne(board: ChessBoard): Boolean {
        val side = board.sideToMove
        val opponent = side.opposite()
        for (move in allLegalMoves(board, side)) {
            val after = board.apply(move)
            if (isCheck(after, opponent) && !hasAnyLegalMove(after, opponent)) {
                return true
            }
        }
        return false
    }

    /**
     * True if [boardAfterUserMove1] (where opponent is to move) is a forced mate-in-2:
     * opponent has >= 1 legal reply (not already checkmate or stalemate) and EVERY legal
     * opponent reply permits at least one immediate mate-in-1.
     */
    fun isForcedMateInTwo(boardAfterUserMove1: ChessBoard): Boolean {
        val opponent = boardAfterUserMove1.sideToMove
        val replies = allLegalMoves(boardAfterUserMove1, opponent)
        if (replies.isEmpty()) return false
        for (reply in replies) {
            val afterReply = boardAfterUserMove1.apply(reply)
            if (!hasMateInOne(afterReply)) return false
        }
        return true
    }

    /**
     * Chooses a deterministic legal reply for a forced mate-in-2 position (used when
     * the player finds a valid alternative mate-in-2 first move).
     */
    fun pickReplyForForcedMateInTwo(boardAfterUserMove1: ChessBoard): ChessMove? {
        val opponent = boardAfterUserMove1.sideToMove
        return allLegalMoves(boardAfterUserMove1, opponent).firstOrNull()
    }

    /**
     * All legal destination squares for the piece at [from].
     * Returns empty if no piece of the side to move is at [from].
     */
    fun legalMoves(board: ChessBoard, from: Square): List<Square> {
        val piece = board[from] ?: return emptyList()
        if (piece.color != board.sideToMove) return emptyList()
        return allLegalMoves(board).filter { it.from == from }.map { it.to }.distinct()
    }

    /**
     * Every legal move for the side to move.
     */
    fun allLegalMoves(board: ChessBoard): List<ChessMove> {
        return allLegalMoves(board, board.sideToMove)
    }

    /**
     * Every legal move for [side].
     */
    fun allLegalMoves(board: ChessBoard, side: Color): List<ChessMove> {
        val pseudoLegal = generatePseudoLegalMoves(board, side)
        return pseudoLegal.filter { move ->
            val after = board.apply(move)
            !isSquareAttackedBy(after, after.kingSquare(side) ?: return@filter false, side.opposite())
        }
    }

    /**
     * Every legal move that delivers immediate checkmate to the opponent.
     */
    fun mateInOneMoves(board: ChessBoard): List<ChessMove> {
        val side = board.sideToMove
        val opponent = side.opposite()
        return allLegalMoves(board, side).filter { move ->
            val after = board.apply(move)
            isCheck(after, opponent) && !hasAnyLegalMove(after, opponent)
        }
    }

    // ── Square-attack query ─────────────────────────────────────────────

    /**
     * Is [square] attacked by any piece of [attacker] color on [board]?
     */
    internal fun isSquareAttackedBy(board: ChessBoard, square: Square, attacker: Color): Boolean {
        // Pawn attacks
        val pawnDir = if (attacker == Color.WHITE) -1 else 1  // direction FROM which a pawn would attack
        for (df in intArrayOf(-1, 1)) {
            val pf = square.file + df
            val pr = square.rank + pawnDir
            if (Square.isValid(pf, pr)) {
                val p = board[Square(pf, pr)]
                if (p != null && p.color == attacker && p.type == PieceType.PAWN) return true
            }
        }

        // Knight attacks
        for ((df, dr) in KNIGHT_OFFSETS) {
            val nf = square.file + df
            val nr = square.rank + dr
            if (Square.isValid(nf, nr)) {
                val p = board[Square(nf, nr)]
                if (p != null && p.color == attacker && p.type == PieceType.KNIGHT) return true
            }
        }

        // King attacks (adjacent squares)
        for ((df, dr) in KING_OFFSETS) {
            val kf = square.file + df
            val kr = square.rank + dr
            if (Square.isValid(kf, kr)) {
                val p = board[Square(kf, kr)]
                if (p != null && p.color == attacker && p.type == PieceType.KING) return true
            }
        }

        // Sliding pieces: rook / queen on ranks and files
        for ((df, dr) in ROOK_DIRS) {
            if (slidingAttack(board, square, df, dr, attacker, PieceType.ROOK, PieceType.QUEEN)) return true
        }

        // Sliding pieces: bishop / queen on diagonals
        for ((df, dr) in BISHOP_DIRS) {
            if (slidingAttack(board, square, df, dr, attacker, PieceType.BISHOP, PieceType.QUEEN)) return true
        }

        return false
    }

    private fun slidingAttack(
        board: ChessBoard, from: Square,
        df: Int, dr: Int,
        attacker: Color,
        type1: PieceType, type2: PieceType
    ): Boolean {
        var f = from.file + df
        var r = from.rank + dr
        while (Square.isValid(f, r)) {
            val p = board[Square(f, r)]
            if (p != null) {
                return p.color == attacker && (p.type == type1 || p.type == type2)
            }
            f += df
            r += dr
        }
        return false
    }

    // ── Pseudo-legal move generation ────────────────────────────────────

    private fun generatePseudoLegalMoves(board: ChessBoard, side: Color): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        for (i in 0..63) {
            val piece = board[i] ?: continue
            if (piece.color != side) continue
            val sq = Square.fromIndex(i)
            when (piece.type) {
                PieceType.PAWN   -> generatePawnMoves(board, sq, side, moves)
                PieceType.KNIGHT -> generateKnightMoves(board, sq, side, moves)
                PieceType.BISHOP -> generateSlidingMoves(board, sq, side, BISHOP_DIRS, moves)
                PieceType.ROOK   -> generateSlidingMoves(board, sq, side, ROOK_DIRS, moves)
                PieceType.QUEEN  -> {
                    generateSlidingMoves(board, sq, side, BISHOP_DIRS, moves)
                    generateSlidingMoves(board, sq, side, ROOK_DIRS, moves)
                }
                PieceType.KING   -> generateKingMoves(board, sq, side, moves)
            }
        }
        return moves
    }

    private fun generatePawnMoves(board: ChessBoard, sq: Square, side: Color, moves: MutableList<ChessMove>) {
        val dir = if (side == Color.WHITE) 1 else -1
        val startRank = if (side == Color.WHITE) 1 else 6
        val promoRank = if (side == Color.WHITE) 7 else 0

        // Single push
        val oneStep = Square(sq.file, sq.rank + dir)
        if (Square.isValid(oneStep.file, oneStep.rank) && board[oneStep] == null) {
            if (oneStep.rank == promoRank) {
                addPromotions(sq, oneStep, moves)
            } else {
                moves.add(ChessMove(sq, oneStep))
            }

            // Double push (only if single push was clear)
            if (sq.rank == startRank) {
                val twoStep = Square(sq.file, sq.rank + 2 * dir)
                if (board[twoStep] == null) {
                    moves.add(ChessMove(sq, twoStep))
                }
            }
        }

        // Captures (including en passant)
        for (df in intArrayOf(-1, 1)) {
            val cf = sq.file + df
            val cr = sq.rank + dir
            if (!Square.isValid(cf, cr)) continue
            val capSq = Square(cf, cr)
            val target = board[capSq]

            val isCapture = target != null && target.color != side
            val isEnPassant = capSq == board.enPassantTarget &&
                target == null &&
                cr != promoRank &&
                board[Square(cf, sq.rank)]?.let { it.type == PieceType.PAWN && it.color != side } == true

            if (isCapture || isEnPassant) {
                if (cr == promoRank) {
                    addPromotions(sq, capSq, moves)
                } else {
                    moves.add(ChessMove(sq, capSq))
                }
            }
        }
    }

    private fun addPromotions(from: Square, to: Square, moves: MutableList<ChessMove>) {
        moves.add(ChessMove(from, to, PieceType.QUEEN))
        moves.add(ChessMove(from, to, PieceType.ROOK))
        moves.add(ChessMove(from, to, PieceType.BISHOP))
        moves.add(ChessMove(from, to, PieceType.KNIGHT))
    }

    private fun generateKnightMoves(board: ChessBoard, sq: Square, side: Color, moves: MutableList<ChessMove>) {
        for ((df, dr) in KNIGHT_OFFSETS) {
            val nf = sq.file + df
            val nr = sq.rank + dr
            if (!Square.isValid(nf, nr)) continue
            val dest = Square(nf, nr)
            val target = board[dest]
            if (target == null || target.color != side) {
                moves.add(ChessMove(sq, dest))
            }
        }
    }

    private fun generateSlidingMoves(
        board: ChessBoard, sq: Square, side: Color,
        dirs: Array<Pair<Int, Int>>, moves: MutableList<ChessMove>
    ) {
        for ((df, dr) in dirs) {
            var f = sq.file + df
            var r = sq.rank + dr
            while (Square.isValid(f, r)) {
                val dest = Square(f, r)
                val target = board[dest]
                if (target == null) {
                    moves.add(ChessMove(sq, dest))
                } else {
                    if (target.color != side) {
                        moves.add(ChessMove(sq, dest)) // capture
                    }
                    break
                }
                f += df
                r += dr
            }
        }
    }

    private fun generateKingMoves(board: ChessBoard, sq: Square, side: Color, moves: MutableList<ChessMove>) {
        // Normal king moves
        for ((df, dr) in KING_OFFSETS) {
            val kf = sq.file + df
            val kr = sq.rank + dr
            if (!Square.isValid(kf, kr)) continue
            val dest = Square(kf, kr)
            val target = board[dest]
            if (target == null || target.color != side) {
                moves.add(ChessMove(sq, dest))
            }
        }

        // Castling
        // The king must be on its starting square (e1/e8)
        val baseRank = if (side == Color.WHITE) 0 else 7
        if (sq.file != 4 || sq.rank != baseRank) return

        val enemy = side.opposite()

        // Cannot castle while in check
        if (isSquareAttackedBy(board, sq, enemy)) return

        // Kingside
        val hasKingside = if (side == Color.WHITE) board.castling.whiteKingside else board.castling.blackKingside
        if (hasKingside && board[Square(7, baseRank)] == Piece(PieceType.ROOK, side)) {
            val f1 = Square(5, baseRank)
            val g1 = Square(6, baseRank)
            if (board[f1] == null && board[g1] == null) {
                // squares the king passes through (f1) and lands on (g1) must not be attacked
                if (!isSquareAttackedBy(board, f1, enemy) && !isSquareAttackedBy(board, g1, enemy)) {
                    moves.add(ChessMove(sq, g1))
                }
            }
        }

        // Queenside
        val hasQueenside = if (side == Color.WHITE) board.castling.whiteQueenside else board.castling.blackQueenside
        if (hasQueenside && board[Square(0, baseRank)] == Piece(PieceType.ROOK, side)) {
            val d1 = Square(3, baseRank)
            val c1 = Square(2, baseRank)
            val b1 = Square(1, baseRank)
            if (board[d1] == null && board[c1] == null && board[b1] == null) {
                // King passes through d1 and lands on c1 — both must be safe
                if (!isSquareAttackedBy(board, d1, enemy) && !isSquareAttackedBy(board, c1, enemy)) {
                    moves.add(ChessMove(sq, c1))
                }
            }
        }
    }

    // ── Direction tables ────────────────────────────────────────────────

    private val KNIGHT_OFFSETS = arrayOf(
        Pair(-2, -1), Pair(-2, 1), Pair(-1, -2), Pair(-1, 2),
        Pair(1, -2), Pair(1, 2), Pair(2, -1), Pair(2, 1)
    )

    private val KING_OFFSETS = arrayOf(
        Pair(-1, -1), Pair(-1, 0), Pair(-1, 1),
        Pair(0, -1), Pair(0, 1),
        Pair(1, -1), Pair(1, 0), Pair(1, 1)
    )

    private val BISHOP_DIRS = arrayOf(
        Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1)
    )

    private val ROOK_DIRS = arrayOf(
        Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
    )
}
