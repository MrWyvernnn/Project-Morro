package com.morro.alarm.puzzle.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessEngineTest {

    @Test
    fun `bestMove finds forced mate in 1`() {
        // White to play: Re1-e8#
        val board = FenParser.parse("6k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1").getOrThrow()
        val result = ChessEngine.bestMove(board, maxDepth = 3, timeBudgetMs = 500L)
        assertEquals("e1e8", result.bestMove?.toUci())
        assertTrue(result.scoreCp > 100_000)
    }

    @Test
    fun `findForcedMate solves mate in 2`() {
        // White to play: Qa4+ Kb8/Kc8 -> Qa8# / Rb1#
        val board = FenParser.parse("1k6/8/1K6/8/8/8/6Q1/8 w - - 0 1").getOrThrow()
        val mateMove = ChessEngine.findForcedMate(board, maxPlies = 3)
        assertNotNull(mateMove)
        val afterFirst = board.apply(mateMove!!)
        val replies = ChessRules.allLegalMoves(afterFirst)
        assertTrue(replies.all { reply -> ChessRules.hasMateInOne(afterFirst.apply(reply)) })
    }

    @Test
    fun `bestMove captures hanging queen`() {
        val board = FenParser.parse("4k3/8/8/3q4/8/8/3R4/4K3 w - - 0 1").getOrThrow()
        val result = ChessEngine.bestMove(board, maxDepth = 3, timeBudgetMs = 500L)
        assertEquals("d2d5", result.bestMove?.toUci())
    }

    @Test
    fun `generateTacticalPuzzle produces valid mate in 1 and mate in 2`() {
        val m1 = ChessEngine.generateTacticalPuzzle(CognitiveDifficulty.STANDARD, seed = 42L)
        assertNotNull(m1)
        val m1Board = FenParser.parse(m1!!.fen).getOrThrow()
        val m1Moves = m1.moves.split(" ").map { ChessMove.fromUci(it)!! }
        assertEquals(2, m1Moves.size)
        val afterM1Setup = m1Board.apply(m1Moves[0])
        assertTrue(ChessRules.isCheckmate(afterM1Setup.apply(m1Moves[1]), afterM1Setup.sideToMove.opposite()))

        val m2 = ChessEngine.generateTacticalPuzzle(CognitiveDifficulty.HARD, seed = 99L)
        assertNotNull(m2)
        val m2Board = FenParser.parse(m2!!.fen).getOrThrow()
        val m2Moves = m2.moves.split(" ").map { ChessMove.fromUci(it)!! }
        assertEquals(4, m2Moves.size)
        val afterM2Setup = m2Board.apply(m2Moves[0])
        val afterUser1 = afterM2Setup.apply(m2Moves[1])
        val afterReply = afterUser1.apply(m2Moves[2])
        assertTrue(ChessRules.isCheckmate(afterReply.apply(m2Moves[3]), afterReply.sideToMove.opposite()))
    }
}
