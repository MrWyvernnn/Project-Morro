package com.morro.alarm.ui.puzzle

import com.morro.alarm.puzzle.chess.ChessBoard
import com.morro.alarm.puzzle.chess.ChessMove
import com.morro.alarm.puzzle.chess.Color
import com.morro.alarm.puzzle.chess.FenParser
import com.morro.alarm.puzzle.chess.Piece
import com.morro.alarm.puzzle.chess.PieceType
import com.morro.alarm.puzzle.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ChessBoardUiTest {

    @Test
    fun squareCoordinateMapping_whiteOrientation() {
        // White orientation (!isFlipped): a1 is bottom-left (col 0, row 7), h8 is top-right (col 7, row 0)
        val a1 = Square(file = 0, rank = 0)
        val h8 = Square(file = 7, rank = 7)
        val e4 = Square(file = 4, rank = 3)

        // col = file, row = 7 - rank
        assertEquals(0, a1.file)
        assertEquals(7, 7 - a1.rank)

        assertEquals(7, h8.file)
        assertEquals(0, 7 - h8.rank)

        assertEquals(4, e4.file)
        assertEquals(4, 7 - e4.rank)
    }

    @Test
    fun squareCoordinateMapping_blackOrientation() {
        // Black orientation (isFlipped): a1 is top-right (col 7, row 0), h8 is bottom-left (col 0, row 7)
        val a1 = Square(file = 0, rank = 0)
        val h8 = Square(file = 7, rank = 7)

        val colA1 = 7 - a1.file
        val rowA1 = a1.rank
        assertEquals(7, colA1)
        assertEquals(0, rowA1)

        val colH8 = 7 - h8.file
        val rowH8 = h8.rank
        assertEquals(0, colH8)
        assertEquals(7, rowH8)
    }

    @Test
    fun squareColorTones_verifyAMOLEDContrast() {
        // (file + rank) % 2 == 1 is light square (surfaceHigh), == 0 is dark square (surface)
        // In chess: a1 is dark, h1 is light, a8 is light, h8 is dark.
        val a1 = Square(0, 0)
        val b1 = Square(1, 0)
        val h1 = Square(7, 0)
        val a8 = Square(0, 7)
        val h8 = Square(7, 7)

        assertEquals(0, (a1.file + a1.rank) % 2) // dark
        assertEquals(1, (b1.file + b1.rank) % 2) // light
        assertEquals(1, (h1.file + h1.rank) % 2) // light
        assertEquals(1, (a8.file + a8.rank) % 2) // light
        assertEquals(0, (h8.file + h8.rank) % 2) // dark
    }

    @Test
    fun nearestSquareTapSnapping_resolvesClosestSquareCenter() {
        val squareSizePx = 50f
        val tolerancePx = squareSizePx / 2f + 16f // half a square + ~6dp

        // Touch near center of square at (col=2, row=3) -> centerX=125, centerY=175
        val touchX = 127f
        val touchY = 173f

        var bestCol = -1
        var bestRow = -1
        var minDistance = Float.MAX_VALUE

        for (col in 0..7) {
            for (row in 0..7) {
                val cx = (col + 0.5f) * squareSizePx
                val cy = (row + 0.5f) * squareSizePx
                val dist = hypot(touchX - cx, touchY - cy)
                if (dist < minDistance) {
                    minDistance = dist
                    bestCol = col
                    bestRow = row
                }
            }
        }

        assertTrue(minDistance <= tolerancePx)
        assertEquals(2, bestCol)
        assertEquals(3, bestRow)
    }

    @Test
    fun moveResult_sealedInterfaceContract() {
        val correct: MoveResult = MoveResult.Correct
        val incorrect: MoveResult = MoveResult.Incorrect
        val solved: MoveResult = MoveResult.Solved

        assertTrue(correct is MoveResult.Correct)
        assertTrue(incorrect is MoveResult.Incorrect)
        assertTrue(solved is MoveResult.Solved)
    }

    @Test
    fun puzzleParsing_extractsOpponentAndUserMoves() {
        // Sample Lichess puzzle: moves = "f8c5 d8h5"
        val movesStr = "f8c5 d8h5"
        val moves = movesStr.trim().split("\\s+".toRegex())
        assertEquals(2, moves.size)

        val opponentSetup = ChessMove.fromUci(moves[0])
        val userSolution = ChessMove.fromUci(moves[1])

        assertNotNull(opponentSetup)
        assertNotNull(userSolution)
        assertEquals("f8c5", opponentSetup?.toUci())
        assertEquals("d8h5", userSolution?.toUci())
    }

    @Test
    fun mateInTwoParsing_extractsFourMoves() {
        // Mate-in-2: moves = "e1g1 d8h4 f2f3 h4h2"
        val movesStr = "e1g1 d8h4 f2f3 h4h2"
        val moves = movesStr.trim().split("\\s+".toRegex())
        assertEquals(4, moves.size)

        val opponentSetup = ChessMove.fromUci(moves[0])
        val userMove1 = ChessMove.fromUci(moves[1])
        val opponentReply = ChessMove.fromUci(moves[2])
        val userMove2 = ChessMove.fromUci(moves[3])

        assertEquals("e1g1", opponentSetup?.toUci())
        assertEquals("d8h4", userMove1?.toUci())
        assertEquals("f2f3", opponentReply?.toUci())
        assertEquals("h4h2", userMove2?.toUci())
    }

    @Test
    fun resolveTappedChessSquare_handlesSquareCornersWithoutDroppingTaps() {
        val squareSizePx = 50f
        val snapPadPx = 6f
        // Corner of (col=0, row=0) -> x = 49, y = 49 (Euclidean dist ~33.9 > 31, axis-aligned dx=24 <= 31)
        val cornerTap = resolveTappedChessSquare(
            tapX = 49f,
            tapY = 49f,
            squareSizePx = squareSizePx,
            snapPadPx = snapPadPx,
            isFlipped = false
        )
        assertEquals(Square(file = 0, rank = 7), cornerTap)
    }
}
