package com.morro.alarm.ui.puzzle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.morro.alarm.puzzle.chess.ChessBoard
import com.morro.alarm.puzzle.chess.ChessEngine
import com.morro.alarm.puzzle.chess.ChessMove
import com.morro.alarm.puzzle.chess.ChessRules
import com.morro.alarm.puzzle.chess.Color as PieceColor
import com.morro.alarm.puzzle.chess.FenParser
import com.morro.alarm.puzzle.chess.Piece
import com.morro.alarm.puzzle.chess.PieceType
import com.morro.alarm.puzzle.chess.PuzzleRecord
import com.morro.alarm.puzzle.chess.Square
import com.morro.alarm.ui.motion.MorroMotion
import com.morro.alarm.ui.motion.pressScale
import com.morro.alarm.ui.motion.rememberReducedMotion
import com.morro.alarm.ui.theme.MorroSpring
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Result of a user move attempt.
 */
sealed interface MoveResult {
    data object Correct : MoveResult
    data object Incorrect : MoveResult
    data object Solved : MoveResult
}

private data class MovingPieceState(
    val piece: Piece,
    val from: Square,
    val to: Square
)

private val PROMOTION_CHOICES = listOf(
    PieceType.QUEEN to "Queen",
    PieceType.ROOK to "Rook",
    PieceType.BISHOP to "Bishop",
    PieceType.KNIGHT to "Knight"
)

// Scoped wooden board palette (wood lives ONLY on the 8x8 surface; chrome stays Morro monochrome)
private val WoodLightSquare = ComposeColor(0xFFE8D5B0)
private val WoodDarkSquare = ComposeColor(0xFF8B6242)
private val WoodBoardFrame = ComposeColor(0xFF5C4030)
private val LegalDotEspresso = ComposeColor(0xFF3A2A1C)
private val CheckRingBrick = ComposeColor(0xFFC4553B)

/**
 * 8×8 Canvas-drawn wooden chessboard for wake-up cognitive gates.
 *
 * - Board palette is scoped strictly to the 8×8 surface (warm maple `#E8D5B0` & walnut `#8B6242`,
 *   procedural grain streaks at `alpha <= 0.05`, and a `2.dp` `#5C4030` inset frame).
 * - Everything outside the board stays Morro monochrome (`morroColors()`, `morroTypography()`).
 * - Selection & last-move highlights use Morro's honey (`#E8C58F`); legal targets use `#3A2A1C`;
 *   king in check uses `#C4553B`; wrong moves trigger haptic + 200ms horizontal spring shake only.
 * - Pure tap-to-select, tap-to-destination interaction (no drag-and-drop), with half-square + 6dp
 *   snapping and pressed-square feedback.
 */
@Composable
fun ChessBoardView(
    board: ChessBoard,
    puzzleRecord: PuzzleRecord,
    onMoveAttempt: ((from: Square, to: Square) -> MoveResult?)? = null,
    onUserInteraction: () -> Unit = {},
    onPuzzleSolved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val colors = morroColors()
    val typo = morroTypography()
    val shapes = morroShapes()

    val currentOnMoveAttempt by rememberUpdatedState(onMoveAttempt)
    val currentOnUserInteraction by rememberUpdatedState(onUserInteraction)
    val currentOnPuzzleSolved by rememberUpdatedState(onPuzzleSolved)

    val puzzleMoves = remember(puzzleRecord.moves) {
        puzzleRecord.moves.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    val initialBoard = remember(puzzleRecord.fen, board) {
        FenParser.parse(puzzleRecord.fen).getOrNull() ?: board
    }
    val initialFen = remember(initialBoard) { FenParser.toFen(initialBoard) }

    // Initial FEN's sideToMove plays Moves[0] (opponent setup move); user plays the opposite color.
    val playerColor = remember(initialBoard) { initialBoard.sideToMove.opposite() }
    val isFlipped = (playerColor == PieceColor.BLACK)

    var savedFen by rememberSaveable(puzzleRecord.puzzleId) { mutableStateOf(initialFen) }
    var setupPlayed by rememberSaveable(puzzleRecord.puzzleId) { mutableStateOf(false) }
    var lastMoveUci by rememberSaveable(puzzleRecord.puzzleId) { mutableStateOf<String?>(null) }
    var currentStepIndex by rememberSaveable(puzzleRecord.puzzleId) { mutableIntStateOf(1) }
    var isSolved by rememberSaveable(puzzleRecord.puzzleId) { mutableStateOf(false) }

    val currentBoard = remember(savedFen, initialBoard) {
        FenParser.parse(savedFen).getOrNull() ?: initialBoard
    }
    val lastMove = remember(lastMoveUci) { lastMoveUci?.let { ChessMove.fromUci(it) } }

    var selectedSquare by remember(puzzleRecord.puzzleId) { mutableStateOf<Square?>(null) }
    var legalDestinations by remember(puzzleRecord.puzzleId) { mutableStateOf<List<Square>>(emptyList()) }
    var pressedSquare by remember(puzzleRecord.puzzleId) { mutableStateOf<Square?>(null) }
    var pendingPromotion by remember(puzzleRecord.puzzleId) { mutableStateOf<Pair<Square, Square>?>(null) }
    var isAnimating by remember(puzzleRecord.puzzleId) { mutableStateOf(false) }

    val isUserTurnEnabled = setupPlayed &&
        !isSolved &&
        !isAnimating &&
        currentBoard.sideToMove == playerColor

    val shakeOffset = remember(puzzleRecord.puzzleId) { Animatable(0f) }
    val moveProgress = remember(puzzleRecord.puzzleId) { Animatable(0f) }
    var movingPiece by remember(puzzleRecord.puzzleId) { mutableStateOf<MovingPieceState?>(null) }
    val grainPath = remember { Path() }

    val legalDotsScale by animateFloatAsState(
        targetValue = if (legalDestinations.isNotEmpty()) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else MorroSpring.FloatSpringPlayful,
        label = "legalDotsScale"
    )

    val honey = colors.honey
    val selectedOverlayColor = honey.copy(alpha = 0.35f)
    val selectedRingColor = honey
    val lastMoveColor = honey.copy(alpha = 0.18f)
    val legalDotColor = LegalDotEspresso.copy(alpha = 0.35f)
    val checkRingColor = CheckRingBrick.copy(alpha = 0.80f)
    val textMeasurer = rememberTextMeasurer()

    val inCheckKingSquare = remember(currentBoard) {
        when {
            ChessRules.isCheck(currentBoard, PieceColor.WHITE) -> currentBoard.kingSquare(PieceColor.WHITE)
            ChessRules.isCheck(currentBoard, PieceColor.BLACK) -> currentBoard.kingSquare(PieceColor.BLACK)
            else -> null
        }
    }

    fun triggerWrongMove() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        selectedSquare = null
        legalDestinations = emptyList()
        if (!reducedMotion) {
            scope.launch {
                shakeOffset.snapTo(0f)
                // 5 steps * 40ms = 200ms horizontal spring-damped shake; no error text or flash
                for (dpOffset in MorroMotion.WrongShakeOffsetsDp) {
                    shakeOffset.animateTo(
                        targetValue = dpOffset,
                        animationSpec = tween(durationMillis = MorroMotion.ShakeStepMillis)
                    )
                }
            }
        }
    }

    suspend fun playAnimatedMove(boardBefore: ChessBoard, move: ChessMove): ChessBoard {
        val piece = boardBefore[move.from]
        if (piece != null && !reducedMotion) {
            movingPiece = MovingPieceState(piece, move.from, move.to)
            moveProgress.snapTo(0f)
            moveProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = MorroMotion.MoveMillis,
                    easing = MorroMotion.MoveEasing
                )
            )
            movingPiece = null
        }
        val nextBoard = boardBefore.apply(move)
        savedFen = FenParser.toFen(nextBoard)
        lastMoveUci = move.toUci()
        return nextBoard
    }

    suspend fun playOpponentReply(boardBeforeReply: ChessBoard, stepIdxAfterUserMove: Int) {
        val scriptedReplyUci = puzzleMoves.getOrNull(stepIdxAfterUserMove)
        val scriptedReply = scriptedReplyUci?.let { ChessMove.fromUci(it) }
        val legalReplies = ChessRules.allLegalMoves(boardBeforeReply)

        val chosenReply = when {
            scriptedReply != null && scriptedReply in legalReplies -> scriptedReply
            else -> ChessEngine.pickSmartReply(boardBeforeReply)
                ?: ChessRules.pickReplyForForcedMateInTwo(boardBeforeReply)
        }

        if (chosenReply == null) {
            isSolved = true
            isAnimating = false
            currentOnPuzzleSolved()
            return
        }

        delay(400)
        playAnimatedMove(boardBeforeReply, chosenReply)
        currentStepIndex = stepIdxAfterUserMove + 1
        isAnimating = false
    }

    val totalUserMoves = if (puzzleMoves.size > 2) 2 else 1

    fun commitValidatedMove(from: Square, to: Square, promotion: PieceType?) {
        if (!isUserTurnEnabled || isSolved) return

        val piece = currentBoard[from] ?: return
        if (piece.color != playerColor) return

        val legalMovesFrom = ChessRules.allLegalMoves(currentBoard).filter { it.from == from && it.to == to }
        if (legalMovesFrom.isEmpty()) {
            triggerWrongMove()
            return
        }

        val userMove = ChessMove(from, to, promotion)
        if (userMove !in legalMovesFrom) {
            triggerWrongMove()
            return
        }

        val afterMove = currentBoard.apply(userMove)
        val isCheckmate = ChessRules.isCheckmate(afterMove, playerColor.opposite())
        val expectedUci = puzzleMoves.getOrNull(currentStepIndex)
        val isFinalStep = currentStepIndex >= puzzleMoves.lastIndex || currentStepIndex + 2 > puzzleMoves.lastIndex
        val customResult = currentOnMoveAttempt?.invoke(from, to)

        val isCorrect = when {
            customResult == MoveResult.Incorrect -> false
            customResult == MoveResult.Correct || customResult == MoveResult.Solved -> true
            totalUserMoves == 1 -> isCheckmate
            isFinalStep -> (expectedUci != null && userMove.toUci() == expectedUci) || (expectedUci == null && isCheckmate)
            else -> expectedUci != null && userMove.toUci() == expectedUci
        }

        if (!isCorrect) {
            triggerWrongMove()
            return
        }

        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

        scope.launch {
            isAnimating = true
            selectedSquare = null
            legalDestinations = emptyList()
            pendingPromotion = null

            val updatedBoard = playAnimatedMove(currentBoard, userMove)
            val solvedNow = if (totalUserMoves == 1) {
                isCheckmate || customResult == MoveResult.Solved
            } else {
                isFinalStep || isCheckmate || customResult == MoveResult.Solved
            }

            if (solvedNow) {
                isSolved = true
                isAnimating = false
                currentOnPuzzleSolved()
            } else {
                playOpponentReply(updatedBoard, currentStepIndex + 1)
            }
        }
    }

    fun executeUserMove(from: Square, to: Square) {
        if (!isUserTurnEnabled || isSolved) return
        val piece = currentBoard[from] ?: return
        if (piece.color != playerColor) return

        val legal = ChessRules.legalMoves(currentBoard, from)
        if (to !in legal) {
            triggerWrongMove()
            return
        }

        val isPromotion = piece.type == PieceType.PAWN && (to.rank == 7 || to.rank == 0)
        if (isPromotion) {
            pendingPromotion = from to to
            return
        }

        commitValidatedMove(from, to, promotion = null)
    }

    fun onSquareTapped(square: Square) {
        currentOnUserInteraction()
        if (!isUserTurnEnabled || isSolved) return
        if (pendingPromotion != null) {
            pendingPromotion = null
            return
        }

        val selected = selectedSquare
        if (selected == null) {
            val piece = currentBoard[square]
            if (piece != null && piece.color == playerColor) {
                selectedSquare = square
                legalDestinations = ChessRules.legalMoves(currentBoard, square)
            }
        } else {
            if (square == selected) {
                selectedSquare = null
                legalDestinations = emptyList()
            } else if (currentBoard[square]?.color == playerColor) {
                selectedSquare = square
                legalDestinations = ChessRules.legalMoves(currentBoard, square)
            } else {
                executeUserMove(selected, square)
            }
        }
    }

    // Auto-play Moves[0] (opponent setup) on entry with both squares highlighted for 600ms
    LaunchedEffect(puzzleRecord.puzzleId, setupPlayed) {
        if (isSolved) return@LaunchedEffect

        if (!setupPlayed) {
            val setupUci = puzzleMoves.firstOrNull()
            val setupMove = setupUci?.let { ChessMove.fromUci(it) }
            val legalAtStart = ChessRules.allLegalMoves(initialBoard)

            if (setupMove != null && setupMove in legalAtStart) {
                isAnimating = true
                playAnimatedMove(initialBoard, setupMove)
                delay(600)
                isAnimating = false
                setupPlayed = true
            } else {
                setupPlayed = true
                if (initialBoard.sideToMove != playerColor) {
                    isSolved = true
                    currentOnPuzzleSolved()
                }
            }
        } else if (currentBoard.sideToMove != playerColor && !isAnimating) {
            isAnimating = true
            playOpponentReply(currentBoard, currentStepIndex + 1)
        }
    }

    val sideLine = if (playerColor == PieceColor.WHITE) "White to move" else "Black to move"
    val helperLine = if (totalUserMoves == 2) {
        "Mate in 2 · Find the exact winning line"
    } else {
        "Mate in 1 · Any delivering checkmate passes"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Morro monochrome header chrome outside the wooden board
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = sideLine,
                style = typo.titleMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = helperLine,
                style = typo.bodyMedium,
                color = colors.textTertiary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Wooden 8x8 board (only a 2dp #5C4030 inset frame on the board itself, nothing outside)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    translationX = with(density) { shakeOffset.value.dp.toPx() }
                }
        ) {
            val boardWidthPx = with(density) { maxWidth.toPx() }
            val squareSizePx = boardWidthPx / 8f
            val frameBorderPx = with(density) { 2.dp.toPx() }
            val selectRingPx = with(density) { 2.dp.toPx() }
            val checkRingPx = with(density) { 2.dp.toPx() }
            val snapPadPx = with(density) { 6.dp.toPx() }
            val dotRadiusPx = minOf(with(density) { 4.dp.toPx() }, squareSizePx * 0.16f)

            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(isUserTurnEnabled, isFlipped, squareSizePx, pendingPromotion) {
                        detectTapGestures { offset ->
                            currentOnUserInteraction()
                            if (!isUserTurnEnabled || isSolved) return@detectTapGestures
                            if (pendingPromotion != null) {
                                pendingPromotion = null
                                return@detectTapGestures
                            }

                            val resolved = resolveTappedChessSquare(
                                tapX = offset.x,
                                tapY = offset.y,
                                squareSizePx = squareSizePx,
                                snapPadPx = snapPadPx,
                                isFlipped = isFlipped
                            )
                            if (resolved != null) {
                                scope.launch {
                                    pressedSquare = resolved
                                    delay(MorroMotion.PressMillis.toLong())
                                    if (pressedSquare == resolved) pressedSquare = null
                                }
                                onSquareTapped(resolved)
                            }
                        }
                    }
            ) {
                val sqSize = Size(squareSizePx, squareSizePx)

                for (col in 0..7) {
                    for (row in 0..7) {
                        val square = coordsToSquare(col, row, isFlipped)
                        val isLight = (square.file + square.rank) % 2 == 1
                        val squareTopLeft = Offset(col * squareSizePx, row * squareSizePx)
                        val squareCenter = Offset(
                            squareTopLeft.x + squareSizePx / 2f,
                            squareTopLeft.y + squareSizePx / 2f
                        )

                        // 1. Base wood square tone
                        drawRect(
                            color = if (isLight) WoodLightSquare else WoodDarkSquare,
                            topLeft = squareTopLeft,
                            size = sqSize
                        )

                        // 2. Deterministic procedural wood grain streaks (alpha <= 0.05)
                        drawWoodGrain(
                            grainPath = grainPath,
                            topLeft = squareTopLeft,
                            squareSize = squareSizePx,
                            seed = square.index,
                            isLight = isLight
                        )

                        // 3. Last move honey highlight (0.18)
                        if (lastMove != null && (square == lastMove.from || square == lastMove.to)) {
                            drawRect(
                                color = lastMoveColor,
                                topLeft = squareTopLeft,
                                size = sqSize
                            )
                        }

                        // 4. Selected square honey overlay (0.35) + 2dp honey inset ring
                        if (square == selectedSquare) {
                            drawRect(
                                color = selectedOverlayColor,
                                topLeft = squareTopLeft,
                                size = sqSize
                            )
                            val halfSel = selectRingPx / 2f
                            drawRect(
                                color = selectedRingColor,
                                topLeft = Offset(squareTopLeft.x + halfSel, squareTopLeft.y + halfSel),
                                size = Size(squareSizePx - selectRingPx, squareSizePx - selectRingPx),
                                style = Stroke(width = selectRingPx)
                            )
                        }

                        // 5. Brief pressed state for groggy-finger touch feedback
                        if (square == pressedSquare) {
                            drawRect(
                                color = ComposeColor.Black.copy(alpha = 0.14f),
                                topLeft = squareTopLeft,
                                size = sqSize
                            )
                        }

                        // 6. King in check ring (#C4553B at 0.8 alpha)
                        if (square == inCheckKingSquare) {
                            val halfChk = checkRingPx / 2f
                            drawRect(
                                color = checkRingColor,
                                topLeft = Offset(squareTopLeft.x + halfChk, squareTopLeft.y + halfChk),
                                size = Size(squareSizePx - checkRingPx, squareSizePx - checkRingPx),
                                style = Stroke(width = checkRingPx)
                            )
                        }

                        // 7. Legal destination indicators (#3A2A1C at 0.35: dot on empty, ring on occupied)
                        if (square in legalDestinations && legalDotsScale > 0.02f) {
                            if (currentBoard[square] != null) {
                                val ringStroke = (squareSizePx * 0.08f).coerceAtLeast(2f)
                                drawCircle(
                                    color = legalDotColor,
                                    radius = squareSizePx * 0.42f * legalDotsScale,
                                    center = squareCenter,
                                    style = Stroke(width = ringStroke)
                                )
                            } else {
                                drawCircle(
                                    color = legalDotColor,
                                    radius = dotRadiusPx * legalDotsScale,
                                    center = squareCenter
                                )
                            }
                        }

                        // 8. Coordinates tucked inside edge corners (9sp, opposite square tone at 0.7 alpha)
                        val coordColor = (if (isLight) WoodDarkSquare else WoodLightSquare).copy(alpha = 0.70f)
                        val coordStyle = TextStyle(
                            color = coordColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val coordPad = squareSizePx * 0.055f
                        if (col == 0) {
                            val rankLabel = ('1' + square.rank).toString()
                            val measured = textMeasurer.measure(rankLabel, style = coordStyle)
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(squareTopLeft.x + coordPad, squareTopLeft.y + coordPad)
                            )
                        }
                        if (row == 7) {
                            val fileLabel = ('a' + square.file).toString()
                            val measured = textMeasurer.measure(fileLabel, style = coordStyle)
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(
                                    squareTopLeft.x + squareSizePx - measured.size.width - coordPad,
                                    squareTopLeft.y + squareSizePx - measured.size.height - coordPad
                                )
                            )
                        }

                        // 9. Static piece
                        val piece = currentBoard[square]
                        if (piece != null && square != movingPiece?.from) {
                            val inset = squareSizePx * 0.08f
                            val halfDrawn = squareSizePx / 2f - inset
                            ChessPieces.drawPiece(
                                scope = this,
                                piece = piece,
                                bounds = Rect(
                                    squareCenter.x - halfDrawn,
                                    squareCenter.y - halfDrawn,
                                    squareCenter.x + halfDrawn,
                                    squareCenter.y + halfDrawn
                                )
                            )
                        }
                    }
                }

                // 2dp inset wooden board frame (#5C4030), nothing outside the 8x8
                val halfFrame = frameBorderPx / 2f
                drawRect(
                    color = WoodBoardFrame,
                    topLeft = Offset(halfFrame, halfFrame),
                    size = Size(size.width - frameBorderPx, size.height - frameBorderPx),
                    style = Stroke(width = frameBorderPx)
                )

                // Moving piece slide animation (MorroMotion.MoveMillis / MoveEasing)
                movingPiece?.let { anim ->
                    val fromCoord = squareToCoords(anim.from, isFlipped)
                    val toCoord = squareToCoords(anim.to, isFlipped)
                    val fromCenter = Offset(
                        (fromCoord.first + 0.5f) * squareSizePx,
                        (fromCoord.second + 0.5f) * squareSizePx
                    )
                    val toCenter = Offset(
                        (toCoord.first + 0.5f) * squareSizePx,
                        (toCoord.second + 0.5f) * squareSizePx
                    )

                    val t = moveProgress.value
                    val curX = fromCenter.x + (toCenter.x - fromCenter.x) * t
                    val curY = fromCenter.y + (toCenter.y - fromCenter.y) * t
                    val halfSize = squareSizePx / 2f - squareSizePx * 0.08f
                    ChessPieces.drawPiece(
                        scope = this,
                        piece = anim.piece,
                        bounds = Rect(curX - halfSize, curY - halfSize, curX + halfSize, curY + halfSize)
                    )
                }
            }

            // TalkBack & Switch-Access overlay WITHOUT .clickable so tap snapping remains intact
            Column(modifier = Modifier.matchParentSize()) {
                for (row in 0..7) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        for (col in 0..7) {
                            val square = coordsToSquare(col, row, isFlipped)
                            val piece = currentBoard[square]
                            val isSelected = (square == selectedSquare)
                            val isLegal = (square in legalDestinations)
                            val isLastMove = (lastMove != null && (square == lastMove.from || square == lastMove.to))
                            val isCheck = (square == inCheckKingSquare)

                            val contentDesc = buildString {
                                if (piece != null) {
                                    append(if (piece.color == PieceColor.WHITE) "White " else "Black ")
                                    append(piece.type.name.lowercase().replaceFirstChar { it.uppercase() })
                                    append(" on ")
                                }
                                append(square.toAlgebraic())
                                if (isSelected) append(", selected")
                                if (isLegal) append(", legal move")
                                if (isLastMove) append(", last move")
                                if (isCheck) append(", king in check")
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .focusable(enabled = isUserTurnEnabled && pendingPromotion == null)
                                    .clearAndSetSemantics {
                                        contentDescription = contentDesc
                                        selected = isSelected
                                        if (isUserTurnEnabled && pendingPromotion == null) {
                                            role = Role.Button
                                            onClick(label = "Select or move to ${square.toAlgebraic()}") {
                                                onSquareTapped(square)
                                                true
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // Pawn promotion picker in Morro monochrome chrome
            pendingPromotion?.let { (promoFrom, promoTo) ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.canvas.copy(alpha = 0.72f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            currentOnUserInteraction()
                            pendingPromotion = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(shapes.Card)
                            .background(colors.surface)
                            .morroHairline(radius = 16.dp, color = colors.borderStrong)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Promote pawn",
                            style = typo.titleMedium,
                            color = colors.textPrimary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PROMOTION_CHOICES.forEach { (promoType, label) ->
                                val btnInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .pressScale(btnInteraction, pressedScale = MorroMotion.PressedScale)
                                        .clip(shapes.Button)
                                        .background(colors.surfaceCard)
                                        .morroHairline(radius = 12.dp, color = colors.borderStrong)
                                        .clickable(
                                            interactionSource = btnInteraction,
                                            indication = null,
                                            role = Role.Button,
                                            onClick = {
                                                currentOnUserInteraction()
                                                commitValidatedMove(promoFrom, promoTo, promoType)
                                            }
                                        )
                                        .clearAndSetSemantics {
                                            contentDescription = "Promote to $label"
                                            role = Role.Button
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.size(38.dp)) {
                                        ChessPieces.drawPiece(
                                            scope = this,
                                            piece = Piece(promoType, playerColor),
                                            bounds = Rect(0f, 0f, size.width, size.height)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Deterministic, clipped sinusoidal wood grain streaks along the horizontal axis of a square.
 * Kept strictly subtle (alpha <= 0.05) and seeded by [seed] for recomposition stability.
 */
private fun DrawScope.drawWoodGrain(
    grainPath: Path,
    topLeft: Offset,
    squareSize: Float,
    seed: Int,
    isLight: Boolean,
) {
    val grainColor = if (isLight) {
        WoodDarkSquare.copy(alpha = 0.045f)
    } else {
        WoodLightSquare.copy(alpha = 0.040f)
    }
    val strokeW = (squareSize * 0.022f).coerceAtLeast(1f)
    val phase = ((seed * 37) % 13) * 0.45f
    val amp = squareSize * 0.032f

    clipRect(
        left = topLeft.x,
        top = topLeft.y,
        right = topLeft.x + squareSize,
        bottom = topLeft.y + squareSize
    ) {
        for (band in 0..2) {
            val baseY = topLeft.y + squareSize * (0.22f + band * 0.28f) + ((seed + band) % 3 - 1) * squareSize * 0.02f
            grainPath.rewind()
            grainPath.moveTo(topLeft.x, baseY + sin(phase + band) * amp)
            val steps = 4
            val dx = squareSize / steps
            for (s in 1..steps) {
                val x1 = topLeft.x + (s - 0.5f) * dx
                val x2 = topLeft.x + s * dx
                val yCtrl = baseY + sin(phase + band + s * 1.1f) * amp
                val yEnd = baseY + sin(phase + band + s * 1.7f) * amp * 0.7f
                grainPath.quadraticTo(x1, yCtrl, x2, yEnd)
            }
            drawPath(
                path = grainPath,
                color = grainColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
    }
}

/** Overload for callers omitting optional callbacks. */
@Composable
fun ChessBoardView(
    board: ChessBoard,
    puzzleRecord: PuzzleRecord,
    onPuzzleSolved: () -> Unit,
    modifier: Modifier = Modifier
) = ChessBoardView(
    board = board,
    puzzleRecord = puzzleRecord,
    onMoveAttempt = null,
    onUserInteraction = {},
    onPuzzleSolved = onPuzzleSolved,
    modifier = modifier
)

/**
 * Resolves a tap coordinate to the nearest chess square within an axis-aligned
 * half-square + [snapPadPx] band, ensuring square corners are never dropped.
 */
internal fun resolveTappedChessSquare(
    tapX: Float,
    tapY: Float,
    squareSizePx: Float,
    snapPadPx: Float,
    isFlipped: Boolean
): Square? {
    if (squareSizePx <= 0f) return null
    val halfSq = squareSizePx / 2f
    val maxReach = halfSq + snapPadPx

    var closestSquare: Square? = null
    var minDistance = Float.MAX_VALUE

    for (col in 0..7) {
        for (row in 0..7) {
            val centerX = (col + 0.5f) * squareSizePx
            val centerY = (row + 0.5f) * squareSizePx
            val dx = abs(tapX - centerX)
            val dy = abs(tapY - centerY)
            if (dx <= maxReach && dy <= maxReach) {
                val dist = hypot(dx, dy)
                if (dist < minDistance) {
                    minDistance = dist
                    closestSquare = coordsToSquare(col, row, isFlipped)
                }
            }
        }
    }
    return closestSquare
}

private fun coordsToSquare(col: Int, row: Int, isFlipped: Boolean): Square {
    val file = if (isFlipped) 7 - col else col
    val rank = if (isFlipped) row else 7 - row
    return Square(file, rank)
}

private fun squareToCoords(square: Square, isFlipped: Boolean): Pair<Int, Int> {
    val col = if (isFlipped) 7 - square.file else square.file
    val row = if (isFlipped) square.rank else 7 - square.rank
    return Pair(col, row)
}
