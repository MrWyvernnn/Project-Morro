package com.morro.alarm.puzzle.chess

import org.junit.Assert.*
import org.junit.Test

class ChessEngineTest {

    // ── FenParser Round-trip Tests ─────────────────────────────────────

    @Test
    fun testFenRoundTripStartingPosition() {
        val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val board = FenParser.parse(startFen).getOrThrow()
        assertEquals(startFen, FenParser.toFen(board))
    }

    @Test
    fun testFenRoundTripMidGamePositions() {
        val fens = listOf(
            "r1bqk2r/pp2bppp/2n1pn2/2pp4/2PP4/2N1PN2/PP2BPPP/R1BQK2R w KQkq - 4 7",
            "r1b1k2r/ppppnppp/2n2q2/2b5/3NP3/2P1B3/PP3PPP/RN1QKB1R w KQkq - 1 7",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        )
        for (fen in fens) {
            val board = FenParser.parse(fen).getOrThrow()
            assertEquals(fen, FenParser.toFen(board))
        }
    }

    @Test
    fun testFenParserRejectsMalformedFenWithoutThrowing() {
        val invalidFens = listOf(
            "",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -", // only 5 parts
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1", // invalid side
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w X - 0 1", // invalid castling
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e9 0 1", // invalid ep
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - -1 1", // negative halfmove
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0" // fullmove < 1
        )
        for (bad in invalidFens) {
            val result = FenParser.parse(bad)
            assertTrue("Expected failure for '$bad'", result.isFailure)
        }
    }

    // ── Castling Tests ─────────────────────────────────────────────────

    @Test
    fun testCastlingBlockedWhenInCheck() {
        // White king at e1, rooks at a1, h1. Black king at b8 (safe from a1/h1), Black rook at e8 giving check.
        val fen = "1k2r3/8/8/8/8/8/8/R3K2R w KQ - 0 1"
        val board = FenParser.parse(fen).getOrThrow()
        assertTrue(ChessRules.isCheck(board, Color.WHITE))

        val moves = ChessRules.allLegalMoves(board)
        val castleMoves = moves.filter {
            it.from == Square(4, 0) && (it.to == Square(6, 0) || it.to == Square(2, 0))
        }
        assertTrue("Cannot castle while in check", castleMoves.isEmpty())
    }

    @Test
    fun testCastlingBlockedThroughAttackedSquare() {
        // White king at e1, rooks at a1, h1. Black bishop attacks f1 (passes through check for kingside)
        // Bishop on c4 covers f1.
        val fen = "r3k2r/8/8/8/2b5/8/8/R3K2R w KQkq - 0 1"
        val board = FenParser.parse(fen).getOrThrow()
        assertFalse(ChessRules.isCheck(board, Color.WHITE))

        val moves = ChessRules.allLegalMoves(board)
        val kingsideCastle = moves.any { it.from == Square(4, 0) && it.to == Square(6, 0) }
        val queensideCastle = moves.any { it.from == Square(4, 0) && it.to == Square(2, 0) }

        assertFalse("Kingside castling blocked through attacked square f1", kingsideCastle)
        assertTrue("Queenside castling should be legal", queensideCastle)
    }

    @Test
    fun testCastlingBlockedWhenLandingSquareAttacked() {
        // White king e1, black king b8, black rook attacks g1 (landing square for kingside). Rook on g8.
        val fen = "1k4r1/8/8/8/8/8/8/R3K2R w KQ - 0 1"
        val board = FenParser.parse(fen).getOrThrow()

        val moves = ChessRules.allLegalMoves(board)
        val kingsideCastle = moves.any { it.from == Square(4, 0) && it.to == Square(6, 0) }
        assertFalse("Cannot castle into check (g1 attacked)", kingsideCastle)
    }

    // ── En Passant Tests ───────────────────────────────────────────────

    @Test
    fun testEnPassantLegalOnlyImmediatelyAfterDoublePush() {
        // White pawn on e5, Black pawn pushes d7 -> d5. Ep target is d6.
        val fenWithEp = "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2"
        val board = FenParser.parse(fenWithEp).getOrThrow()

        val legal = ChessRules.allLegalMoves(board)
        val epMove = ChessMove(Square.fromAlgebraic("e5")!!, Square.fromAlgebraic("d6")!!)
        assertTrue("En passant capture should be legal", legal.contains(epMove))

        // After applying ep capture, the black pawn on d5 must be removed
        val afterBoard = board.apply(epMove)
        assertNull(afterBoard[Square.fromAlgebraic("d5")!!])
        assertEquals(Piece(PieceType.PAWN, Color.WHITE), afterBoard[Square.fromAlgebraic("d6")!!])

        // If another move is played instead of ep capture, the next turn has no ep target
        val fenNoEp = "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"
        val boardNoEp = FenParser.parse(fenNoEp).getOrThrow()
        val legalNoEp = ChessRules.allLegalMoves(boardNoEp)
        assertFalse("En passant should not be legal if ep square is not set", legalNoEp.contains(epMove))
    }

    // ── Pinned Piece Tests ─────────────────────────────────────────────

    @Test
    fun testPinnedPieceCannotMoveOffPinLine() {
        // White king on e1, White knight on e2, Black king on a8, Black rook on e8. Knight is pinned to e1.
        val fen = "k3r3/8/8/8/8/8/4N3/4K3 w - - 0 1"
        val board = FenParser.parse(fen).getOrThrow()

        val knightSq = Square.fromAlgebraic("e2")!!
        val knightMoves = ChessRules.legalMoves(board, knightSq)
        assertTrue("Pinned knight cannot move anywhere off the pin line", knightMoves.isEmpty())
    }

    // ── Checkmate vs Stalemate Tests ───────────────────────────────────

    @Test
    fun testBackRankMateRecognizedAndStalemateIsNotCheckmate() {
        // Back rank mate: Black king on h8 with pawns on f7, g7, h7; White rook on d8 delivering checkmate.
        val mateFen = "3R2k1/5ppp/8/8/8/8/8/4K3 b - - 0 1"
        val mateBoard = FenParser.parse(mateFen).getOrThrow()

        assertTrue("Should be checkmate", ChessRules.isCheckmate(mateBoard, Color.BLACK))
        assertFalse("Checkmate is not stalemate", ChessRules.isStalemate(mateBoard, Color.BLACK))

        // Stalemate: Black king on a8, White king on c7, White queen on b6. Black to move.
        // Wait, Queen on b6 attacks a8? b6 to a8: file diff 1, rank diff 2 -> not attacked!
        // Let's verify: a8 to b6 is rank 7 to rank 5, file 0 to file 1 (dx=1, dy=2). Queen on b6 does NOT attack a8!
        // Squares adjacent to a8: a7 (attacked by b6 queen rank/diag, and c7 king), b8 (attacked by b6 queen and c7 king), b7 (attacked by queen and king).
        // Black has no legal moves and is NOT in check.
        val stalemateFen = "k7/8/1Q1K4/8/8/8/8/8 b - - 0 1"
        val staleBoard = FenParser.parse(stalemateFen).getOrThrow()

        assertFalse("Should not be check", ChessRules.isCheck(staleBoard, Color.BLACK))
        assertTrue("Should be stalemate", ChessRules.isStalemate(staleBoard, Color.BLACK))
        assertFalse("Stalemate is NOT checkmate", ChessRules.isCheckmate(staleBoard, Color.BLACK))
    }

    // ── Mate in One Tests ──────────────────────────────────────────────

    @Test
    fun testMateInOneReturnsAllMatingMoves() {
        // White has two distinct checkmates:
        // King on h1, rooks on a7 and b7. Black king on h8, pawns blocking or isolated.
        // Consider: Black king on e8. White rooks on a7 and b6.
        // Or position: Black king on h8, White Queen on f7, White Rook on a1.
        // Queen f7-g7# (mate) or Queen f7-f8# (mate if defended or king trapped)?
        // Let's set up an explicit position with 2 distinct mates in 1:
        // White: King on g1, Queen on g6, Rook on f1.
        // Black: King on h8, pawns on g7, h7? If pawn on g7, Q cannot go to g7.
        // Let's construct:
        // Black king on a8, black pawns on a7, b7. White has Rook on d1, Rook on e1.
        // White Rd1-d8# is mate, and Re1-e8# is also mate!
        val twoMatesFen = "k7/pp6/8/8/8/8/8/3RR1K1 w - - 0 1"
        val board = FenParser.parse(twoMatesFen).getOrThrow()

        val mateMoves = ChessRules.mateInOneMoves(board)
        val uciMates = mateMoves.map { it.toUci() }.toSet()

        assertEquals(2, mateMoves.size)
        assertTrue(uciMates.contains("d1d8"))
        assertTrue(uciMates.contains("e1e8"))
    }

    // ── Promotion delivers mate where knight does not ──────────────────

    @Test
    fun testPromotionToQueenDeliversMateWhereKnightDoesNot() {
        // White pawn on e7, White king on e6, Black king on e8.
        // If pawn promotes e7e8q#, it delivers checkmate.
        // If pawn promotes e7e8n, it delivers check but does it deliver checkmate?
        // Wait, with Ke6, e7 pawn promotes:
        // On e8, White pawn becomes Queen -> e8 is king's square so that would be capturing king!
        // A pawn promotes when moving to rank 8: from e7 to d8 or f8 (capture) or e8 (if empty).
        // Let's put Black king on d8!
        // White pawn on e7. White King on f7.
        // Black king on d7?
        // Let's make it simpler:
        // Black king on h8. White king on f7. White pawn on g7... wait g7 is check already.
        // White pawn on e7. Black king on a8. White queen/etc...
        // Consider back rank:
        // Black king on g8, black pawns on f7, g7, h7... Wait if pawns are there king can't be on g8 with pawns on f7, g7, h7.
        // Black king on h8, Black pawns on g7, h7.
        // White King on f8?
        // What about:
        // Black king on h8, pawns on f7, g6, h7. (King trapped at h8).
        // White pawn on c7. White rook on a1.
        // If c7-c8=Q or R: back rank mate!
        // Wait, if c7-c8=Q/R, c8 to h8 is 8th rank. King is on h8!
        // The 8th rank is: c8 -> d8, e8, f8, g8, h8!
        // On c8, a Queen attacks h8 along rank 8 (c8-d8-e8-f8-g8-h8). All squares empty!
        // King at h8 has no moves (g8 attacked by Queen on c8, g7 occupied by black pawn, h7 occupied by black pawn).
        // A Rook on c8 ALSO attacks h8 along rank 8.
        // BUT a Knight on c8 does NOT attack h8!
        // Let's construct position:
        // White: King on g1, Pawn on a7.
        // Black: King on h8, Pawn on g7, Pawn on h7.
        // White plays a7-a8:
        // If a7-a8=Q: Queen attacks along rank 8 directly to h8! Mate! (King has no moves: g8 attacked by Q, g7/h7 blocked).
        // If a7-a8=N: Knight is on a8, does not give check at all! Not mate!
        val fen = "7k/P5pp/8/8/8/8/8/6K1 w - - 0 1"
        val board = FenParser.parse(fen).getOrThrow()

        val mateMoves = ChessRules.mateInOneMoves(board)
        val mateUcis = mateMoves.map { it.toUci() }

        // Queen (a7a8q) and Rook (a7a8r) deliver mate!
        assertTrue("a7a8q should be mate", mateUcis.contains("a7a8q"))
        assertTrue("a7a8r should be mate", mateUcis.contains("a7a8r"))
        assertFalse("a7a8n should NOT be mate", mateUcis.contains("a7a8n"))
        assertFalse("a7a8b should NOT be mate", mateUcis.contains("a7a8b"))
    }

    // ── UCI parsing & formatting ───────────────────────────────────────

    @Test
    fun testChessMoveUci() {
        val m1 = ChessMove.fromUci("e2e4")
        assertNotNull(m1)
        assertEquals("e2e4", m1!!.toUci())
        assertEquals(Square(4, 1), m1.from)
        assertEquals(Square(4, 3), m1.to)
        assertNull(m1.promotion)

        val m2 = ChessMove.fromUci("e7e8q")
        assertNotNull(m2)
        assertEquals("e7e8q", m2!!.toUci())
        assertEquals(PieceType.QUEEN, m2.promotion)

        assertNull(ChessMove.fromUci("e2e"))
        assertNull(ChessMove.fromUci("invalid"))
        assertNull(ChessMove.fromUci("e7e8x"))
    }

    // ── Strict Legality Hardening Tests ────────────────────────────────

    @Test
    fun testFenRejectsInvalidKingsPawnsDigitsCastlingAndEp() {
        val illegalFens = listOf(
            "4r3/8/8/8/8/8/8/4K3 w - - 0 1",          // missing black king
            "k6k/8/8/8/8/8/8/4K3 w - - 0 1",          // two black kings
            "k7/8/8/8/8/8/8/4K2P w - - 0 1",          // white pawn on rank 1
            "k2p4/8/8/8/8/8/8/4K3 w - - 0 1",         // black pawn on rank 8
            "k7/44/8/8/8/8/8/4K3 w - - 0 1",          // consecutive digits
            "k7/8/8/8/8/8/8/4K3 w K - 0 1",           // kingside castling without h1 rook
            "k7/8/8/8/8/8/4P3/4K3 w - d6 0 1",        // invalid en passant without black pawn on d5
            "k3R3/8/8/8/8/8/8/4K3 w - - 0 1"          // non-moving black king already in check
        )
        for (bad in illegalFens) {
            assertTrue("Should reject illegal FEN: $bad", FenParser.parse(bad).isFailure)
        }
    }

    @Test
    fun testEnPassantDiscoveredHorizontalCheckBlocked() {
        // White king on a5, White pawn on d5, Black pawn on e5 (just double-pushed -> ep e6), Black rook on h5.
        // Black king on a8. Taking d5xe6 leaves a5 king exposed to h5 rook!
        val board = FenParser.parse("k7/8/8/K2Pp2r/8/8/8/8 w - e6 0 1").getOrThrow()
        val epMove = ChessMove.fromUci("d5e6")!!
        val legal = ChessRules.allLegalMoves(board)
        assertFalse("EP exposing king horizontally to rook must be illegal", epMove in legal)
    }

    @Test
    fun testForcedMateInTwoDetection() {
        // After Black blunder f8f5: White Q on d5 plays Qa8+ forcing Rf8, then Qxf8#
        val start = FenParser.parse("5r1k/6pp/4N3/3Q4/8/8/6PP/6K1 b - - 0 1").getOrThrow()
        val afterBlunder = start.apply(ChessMove.fromUci("f8f5")!!)
        val m1 = ChessMove.fromUci("d5a8")!!
        val afterM1 = afterBlunder.apply(m1)
        assertFalse(ChessRules.isCheckmate(afterM1, Color.BLACK))
        assertTrue(ChessRules.isForcedMateInTwo(afterM1))
    }
}
