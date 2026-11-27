package com.morro.alarm.puzzle.chess

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Pure-Kotlin tactical chess engine with:
 * - Material + PeSTO piece-square table evaluation + bishop pair
 * - Alpha-beta negamax search with iterative deepening
 * - Quiescence search (captures & promotions)
 * - MVV-LVA + promotion + killer move ordering
 * - Fixed-size Zobrist transposition table
 * - Exact forced-mate solver and on-device tactical puzzle generator
 */
object ChessEngine {

    private const val INF = 1_000_000
    private const val MATE_SCORE = 900_000
    private const val TT_SIZE = 1 shl 16 // 65,536 entries
    private const val TT_MASK = TT_SIZE - 1

    private const val FLAG_EXACT = 0
    private const val FLAG_LOWER = 1
    private const val FLAG_UPPER = 2

    data class SearchResult(
        val bestMove: ChessMove?,
        val scoreCp: Int,
        val mateInMoves: Int?,
        val nodesSearched: Int
    )

    private class TTEntry {
        var key: Long = 0L
        var depth: Int = -1
        var score: Int = 0
        var flag: Int = FLAG_EXACT
        var bestMove: ChessMove? = null
    }

    // Zobrist tables (deterministic seed so hashes are reproducible)
    private val zobristPiece = Array(12) { LongArray(64) }
    private var zobristSide: Long = 0L
    private val zobristCastle = LongArray(16)
    private val zobristEp = LongArray(8)

    init {
        val rng = Random(0x5EED_C4E55L)
        for (p in 0 until 12) {
            for (sq in 0 until 64) {
                zobristPiece[p][sq] = rng.nextLong()
            }
        }
        zobristSide = rng.nextLong()
        for (i in 0 until 16) zobristCastle[i] = rng.nextLong()
        for (i in 0 until 8) zobristEp[i] = rng.nextLong()
    }

    private val PIECE_VALUES = intArrayOf(
        100,  // PAWN
        320,  // KNIGHT
        330,  // BISHOP
        500,  // ROOK
        900,  // QUEEN
        20000 // KING
    )

    // Piece-square tables from White's perspective (rank 8 at index 0..7 down to rank 1 at index 56..63)
    private val PAWN_PST = intArrayOf(
        0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5,  5, 10, 25, 25, 10,  5,  5,
        0,  0,  0, 20, 20,  0,  0,  0,
        5, -5,-10,  0,  0,-10, -5,  5,
        5, 10, 10,-20,-20, 10, 10,  5,
        0,  0,  0,  0,  0,  0,  0,  0
    )

    private val KNIGHT_PST = intArrayOf(
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    )

    private val BISHOP_PST = intArrayOf(
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    )

    private val ROOK_PST = intArrayOf(
        0,  0,  0,  0,  0,  0,  0,  0,
        5, 10, 10, 10, 10, 10, 10,  5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        0,  0,  0,  5,  5,  0,  0,  0
    )

    private val QUEEN_PST = intArrayOf(
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
        -5,  0,  5,  5,  5,  5,  0, -5,
        0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    )

    private val KING_PST = intArrayOf(
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
        20, 20,  0,  0,  0,  0, 20, 20,
        20, 30, 10,  0,  0, 10, 30, 20
    )

    /**
     * Static evaluation from [board.sideToMove]'s perspective in centipawns.
     */
    fun evaluate(board: ChessBoard): Int {
        var score = 0
        var whiteBishops = 0
        var blackBishops = 0

        for (rank in 0..7) {
            for (file in 0..7) {
                val piece = board[Square(file, rank)] ?: continue
                val typeIdx = piece.type.ordinal
                val baseVal = PIECE_VALUES[typeIdx]
                // PST index 0 is rank 8 (rank=7), index 56 is rank 1 (rank=0)
                val pstIdx = if (piece.color == Color.WHITE) {
                    (7 - rank) * 8 + file
                } else {
                    rank * 8 + file
                }
                val pstVal = when (piece.type) {
                    PieceType.PAWN -> PAWN_PST[pstIdx]
                    PieceType.KNIGHT -> KNIGHT_PST[pstIdx]
                    PieceType.BISHOP -> BISHOP_PST[pstIdx]
                    PieceType.ROOK -> ROOK_PST[pstIdx]
                    PieceType.QUEEN -> QUEEN_PST[pstIdx]
                    PieceType.KING -> KING_PST[pstIdx]
                }
                if (piece.color == Color.WHITE) {
                    score += baseVal + pstVal
                    if (piece.type == PieceType.BISHOP) whiteBishops++
                } else {
                    score -= baseVal + pstVal
                    if (piece.type == PieceType.BISHOP) blackBishops++
                }
            }
        }

        if (whiteBishops >= 2) score += 30
        if (blackBishops >= 2) score -= 30

        return if (board.sideToMove == Color.WHITE) score else -score
    }

    /**
     * Searches [board] using iterative deepening alpha-beta + quiescence + transposition table.
     */
    fun bestMove(
        board: ChessBoard,
        maxDepth: Int = 4,
        timeBudgetMs: Long = 250L
    ): SearchResult {
        val tt = Array(TT_SIZE) { TTEntry() }
        val killers = Array(64) { arrayOfNulls<ChessMove>(2) }
        val deadline = System.currentTimeMillis() + timeBudgetMs.coerceAtLeast(10L)
        var nodes = 0
        var bestMoveSoFar: ChessMove? = null
        var bestScoreSoFar = 0
        var aborted = false

        fun shouldStop(): Boolean {
            if ((nodes and 1023) == 0 && System.currentTimeMillis() >= deadline) {
                aborted = true
            }
            return aborted
        }

        fun scoreMove(b: ChessBoard, move: ChessMove, ttMove: ChessMove?, ply: Int): Int {
            if (move == ttMove) return 10_000_000
            val victim = b[move.to]
            val attacker = b[move.from]
            if (victim != null && attacker != null) {
                return 1_000_000 + PIECE_VALUES[victim.type.ordinal] * 10 - PIECE_VALUES[attacker.type.ordinal]
            }
            if (move.promotion != null) {
                return 900_000 + PIECE_VALUES[move.promotion.ordinal]
            }
            if (ply < 64) {
                if (move == killers[ply][0]) return 500_000
                if (move == killers[ply][1]) return 400_000
            }
            return 0
        }

        fun quiescence(b: ChessBoard, alphaIn: Int, beta: Int, ply: Int): Int {
            nodes++
            if (shouldStop()) return 0

            val standPat = evaluate(b)
            if (standPat >= beta) return beta
            var alpha = max(alphaIn, standPat)

            val tacticalMoves = ChessRules.allLegalMoves(b).filter { move ->
                b[move.to] != null || move.promotion != null
            }.sortedByDescending { scoreMove(b, it, null, ply) }

            for (move in tacticalMoves) {
                val score = -quiescence(b.apply(move), -beta, -alpha, ply + 1)
                if (aborted) return 0
                if (score >= beta) return beta
                if (score > alpha) alpha = score
            }
            return alpha
        }

        fun negamax(b: ChessBoard, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
            nodes++
            if (shouldStop()) return 0

            val key = zobristHash(b)
            val ttEntry = tt[(key.toInt() and TT_MASK)]
            var ttMove: ChessMove? = null
            if (ttEntry.key == key && ttEntry.depth >= depth) {
                ttMove = ttEntry.bestMove
                when (ttEntry.flag) {
                    FLAG_EXACT -> return ttEntry.score
                    FLAG_LOWER -> if (ttEntry.score >= beta) return ttEntry.score
                    FLAG_UPPER -> if (ttEntry.score <= alphaIn) return ttEntry.score
                }
            } else if (ttEntry.key == key) {
                ttMove = ttEntry.bestMove
            }

            val legal = ChessRules.allLegalMoves(b)
            if (legal.isEmpty()) {
                return if (ChessRules.isCheck(b, b.sideToMove)) {
                    -MATE_SCORE + ply
                } else {
                    0 // Stalemate
                }
            }

            if (depth <= 0) {
                return quiescence(b, alphaIn, beta, ply)
            }

            val ordered = legal.sortedByDescending { scoreMove(b, it, ttMove, ply) }
            var alpha = alphaIn
            var bestScore = -INF
            var bestMove: ChessMove? = ordered.firstOrNull()

            for (move in ordered) {
                val score = -negamax(b.apply(move), depth - 1, -beta, -alpha, ply + 1)
                if (aborted) return 0
                if (score > bestScore) {
                    bestScore = score
                    bestMove = move
                }
                if (score > alpha) {
                    alpha = score
                }
                if (alpha >= beta) {
                    if (b[move.to] == null && ply < 64) {
                        killers[ply][1] = killers[ply][0]
                        killers[ply][0] = move
                    }
                    break
                }
            }

            val flag = when {
                bestScore <= alphaIn -> FLAG_UPPER
                bestScore >= beta -> FLAG_LOWER
                else -> FLAG_EXACT
            }
            ttEntry.key = key
            ttEntry.depth = depth
            ttEntry.score = bestScore
            ttEntry.flag = flag
            ttEntry.bestMove = bestMove

            return bestScore
        }

        val rootLegal = ChessRules.allLegalMoves(board)
        if (rootLegal.isEmpty()) {
            val inCheck = ChessRules.isCheck(board, board.sideToMove)
            return SearchResult(
                bestMove = null,
                scoreCp = if (inCheck) -MATE_SCORE else 0,
                mateInMoves = if (inCheck) 0 else null,
                nodesSearched = 1
            )
        }
        bestMoveSoFar = rootLegal.first()

        for (depth in 1..maxDepth.coerceIn(1, 8)) {
            aborted = false
            var alpha = -INF
            val beta = INF
            var iterBestMove: ChessMove? = null
            var iterBestScore = -INF

            val orderedRoot = rootLegal.sortedByDescending { scoreMove(board, it, bestMoveSoFar, 0) }
            for (move in orderedRoot) {
                val score = -negamax(board.apply(move), depth - 1, -beta, -alpha, 1)
                if (aborted) break
                if (score > iterBestScore) {
                    iterBestScore = score
                    iterBestMove = move
                }
                if (score > alpha) alpha = score
            }

            if (!aborted && iterBestMove != null) {
                bestMoveSoFar = iterBestMove
                bestScoreSoFar = iterBestScore
                if (abs(bestScoreSoFar) >= MATE_SCORE - 100) break
            } else {
                break
            }
        }

        val matePlies = if (abs(bestScoreSoFar) >= MATE_SCORE - 100) {
            (MATE_SCORE - abs(bestScoreSoFar) + 1) / 2
        } else {
            null
        }

        return SearchResult(
            bestMove = bestMoveSoFar,
            scoreCp = bestScoreSoFar,
            mateInMoves = matePlies,
            nodesSearched = nodes
        )
    }

    /**
     * Exact forced-mate finder up to [maxPlies] (e.g. 1 = mate in 1, 3 = mate in 2).
     * Returns a winning move if one exists, or null.
     */
    fun findForcedMate(board: ChessBoard, maxPlies: Int = 3): ChessMove? {
        val legal = ChessRules.allLegalMoves(board)
        for (move in legal) {
            val next = board.apply(move)
            if (ChessRules.isCheckmate(next, next.sideToMove)) return move
        }
        if (maxPlies <= 1) return null

        for (move in legal) {
            val next = board.apply(move)
            val replies = ChessRules.allLegalMoves(next)
            if (replies.isEmpty()) continue
            val allRepliesLose = replies.all { reply ->
                findForcedMate(next.apply(reply), maxPlies - 2) != null
            }
            if (allRepliesLose) return move
        }
        return null
    }

    /**
     * Picks the smartest defensive reply for the opponent in a puzzle.
     * Prefers moves that avoid mate-in-1; otherwise uses alpha-beta search.
     */
    fun pickSmartReply(board: ChessBoard): ChessMove? {
        val legal = ChessRules.allLegalMoves(board)
        if (legal.isEmpty()) return null
        val avoidingImmediateMate = legal.filter { reply ->
            !ChessRules.hasMateInOne(board.apply(reply))
        }
        if (avoidingImmediateMate.isNotEmpty()) {
            val candidate = bestMove(board, maxDepth = 3, timeBudgetMs = 120L).bestMove
            if (candidate != null && candidate in avoidingImmediateMate) {
                return candidate
            }
            return avoidingImmediateMate.maxByOrNull { reply ->
                -evaluate(board.apply(reply))
            } ?: avoidingImmediateMate.first()
        }
        return bestMove(board, maxDepth = 2, timeBudgetMs = 80L).bestMove ?: legal.first()
    }

    /**
     * On-device tactical puzzle generator using [ChessEngine] verification.
     * Builds randomized endgames/middlegame tactical patterns and verifies a forced mate.
     */
    fun generateTacticalPuzzle(difficulty: CognitiveDifficulty, seed: Long): PuzzleRecord? {
        val rng = Random(seed)
        val wantMateIn2 = difficulty == CognitiveDifficulty.HARD

        for (attempt in 0 until 60) {
            val bkFile = rng.nextInt(8)
            val bkRank = if (rng.nextBoolean()) 7 else 0
            val wkRank = if (bkRank == 7) 5 else 2
            val wkFile = (bkFile + rng.nextInt(-1, 2)).coerceIn(0, 7)

            val pieces = MutableList<Piece?>(64) { null }
            pieces[Square(bkFile, bkRank).index] = Piece(PieceType.KING, Color.BLACK)
            pieces[Square(wkFile, wkRank).index] = Piece(PieceType.KING, Color.WHITE)

            // Add attacking White Queen or Rook + helper piece
            val attackerSquare = Square(rng.nextInt(8), rng.nextInt(2, 6))
            if (pieces[attackerSquare.index] != null) continue
            val attackerType = if (rng.nextBoolean()) PieceType.QUEEN else PieceType.ROOK
            pieces[attackerSquare.index] = Piece(attackerType, Color.WHITE)

            if (wantMateIn2 || rng.nextBoolean()) {
                val helperSquare = Square(rng.nextInt(8), rng.nextInt(1, 7))
                if (pieces[helperSquare.index] == null) {
                    val helperType = if (rng.nextBoolean()) PieceType.ROOK else PieceType.BISHOP
                    pieces[helperSquare.index] = Piece(helperType, Color.WHITE)
                }
            }

            val board = ChessBoard(
                pieces = pieces,
                sideToMove = Color.BLACK,
                castling = CastlingRights(),
                enPassantTarget = null,
                halfmoveClock = 0,
                fullmoveNumber = 1
            )

            // Ensure White isn't already giving illegal check on Black's turn
            if (ChessRules.isCheck(board, Color.WHITE)) continue
            val setupMoves = ChessRules.allLegalMoves(board)
            if (setupMoves.isEmpty()) continue
            val setup = setupMoves[rng.nextInt(setupMoves.size)]
            val afterSetup = board.apply(setup)

            if (wantMateIn2) {
                if (ChessRules.hasMateInOne(afterSetup)) continue
                val mateMove = findForcedMate(afterSetup, maxPlies = 3) ?: continue
                val afterM1 = afterSetup.apply(mateMove)
                val reply = pickSmartReply(afterM1) ?: continue
                val afterReply = afterM1.apply(reply)
                val finalMate = findForcedMate(afterReply, maxPlies = 1) ?: continue
                return PuzzleRecord(
                    puzzleId = "gen_m2_${seed}_$attempt",
                    fen = FenParser.toFen(board),
                    moves = "${setup.toUci()} ${mateMove.toUci()} ${reply.toUci()} ${finalMate.toUci()}",
                    rating = 1200,
                    themes = "mateIn2 generated"
                )
            } else {
                val mateMove = findForcedMate(afterSetup, maxPlies = 1) ?: continue
                return PuzzleRecord(
                    puzzleId = "gen_m1_${seed}_$attempt",
                    fen = FenParser.toFen(board),
                    moves = "${setup.toUci()} ${mateMove.toUci()}",
                    rating = 850,
                    themes = "mateIn1 generated"
                )
            }
        }
        return null
    }

    private fun zobristHash(board: ChessBoard): Long {
        var h = if (board.sideToMove == Color.BLACK) zobristSide else 0L
        for (rank in 0..7) {
            for (file in 0..7) {
                val sq = Square(file, rank)
                val piece = board[sq] ?: continue
                val pIdx = piece.type.ordinal + if (piece.color == Color.BLACK) 6 else 0
                h = h xor zobristPiece[pIdx][sq.index]
            }
        }
        val castleMask =
            (if (board.castling.whiteKingside) 1 else 0) or
                (if (board.castling.whiteQueenside) 2 else 0) or
                (if (board.castling.blackKingside) 4 else 0) or
                (if (board.castling.blackQueenside) 8 else 0)
        h = h xor zobristCastle[castleMask]
        board.enPassantTarget?.let { ep ->
            h = h xor zobristEp[ep.file]
        }
        return h
    }
}
