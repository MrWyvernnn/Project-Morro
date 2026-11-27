package com.morro.alarm.ui.puzzle

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
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
import com.morro.alarm.ui.theme.morroGlassBorder
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

private enum class PuzzleBannerKind {
    NEUTRAL,
    GOOD,
    WRONG,
    SOLVED
}

private val PROMOTION_CHOICES = listOf(
    PieceType.QUEEN to "Queen",
    PieceType.ROOK to "Rook",
    PieceType.BISHOP to "Bishop",
    PieceType.KNIGHT to "Knight"
)

// Exact Chess.com Classic Green + Neo palette
private val ChessComFrameDark = ComposeColor(0xFF262421)
private val ChessComStripSurface = ComposeColor(0xFF302E2B)
private val ChessComLightSquare = ComposeColor(0xFFEEEED2)
private val ChessComDarkSquare = ComposeColor(0xFF769656)
private val ChessComHighlightYellow = ComposeColor(0xFFF6F669)
private val ChessComGreenBanner = ComposeColor(0xFF81B64C)
private val ChessComCrimson = ComposeColor(0xFFCA3431)
private val ChessComCheckGlow = ComposeColor(0xFFE02828)

/**
 * 8×8 Canvas-drawn chess board view matching Chess.com's Classic Green + Neo puzzle experience.
 *
 * - Palette: Exact `#769656` dark green & `#EEEED2` cream squares, `#F6F669` move/select highlights,
 *   crimson radial check glow, and Chess.com `#CA3431` / `#81B64C` feedback banners.
 * - Interaction: Smooth physical drag-and-drop AND tap-to-move with lifted piece preview and
 *   target-square hover ring.
 * - Engine safety: Verifies forced Mate-in-1 / Mate-in-2 accurately (without accepting blunders),
 *   persists across rotation via rememberSaveable, and honors rememberReducedMotion().
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
    val viewConfig = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()

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

    // Initial position's sideToMove plays Moves[0] (the opponent setup move), so player plays the opposite.
    val playerColor = remember(initialBoard) { initialBoard.sideToMove.opposite() }
    val isFlipped = (playerColor == PieceColor.BLACK)

    // Survive rotation/configuration changes
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

    // Drag-and-drop live gesture state
    var draggingFrom by remember(puzzleRecord.puzzleId) { mutableStateOf<Square?>(null) }
    var dragPointerPx by remember(puzzleRecord.puzzleId) { mutableStateOf<Offset?>(null) }
    var dragHoverSquare by remember(puzzleRecord.puzzleId) { mutableStateOf<Square?>(null) }

    // Chess.com incorrect / good move feedback banner state
    var wrongFlashSquare by remember(puzzleRecord.puzzleId) { mutableStateOf<Square?>(null) }
    var feedbackKind by remember(puzzleRecord.puzzleId) { mutableStateOf(PuzzleBannerKind.NEUTRAL) }
    var wrongCounter by remember(puzzleRecord.puzzleId) { mutableIntStateOf(0) }

    val isUserTurnEnabled = setupPlayed &&
        !isSolved &&
        !isAnimating &&
        currentBoard.sideToMove == playerColor

    // Animations
    val shakeOffset = remember(puzzleRecord.puzzleId) { Animatable(0f) }
    val moveProgress = remember(puzzleRecord.puzzleId) { Animatable(0f) }
    var movingPiece by remember(puzzleRecord.puzzleId) { mutableStateOf<MovingPieceState?>(null) }

    val selectedPiecePop by animateFloatAsState(
        targetValue = if (selectedSquare != null && draggingFrom == null && !reducedMotion) 1.07f else 1f,
        animationSpec = if (reducedMotion) tween(0) else MorroSpring.FloatSpringPlayful,
        label = "selectedPiecePop"
    )
    val legalDotsScale by animateFloatAsState(
        targetValue = if (legalDestinations.isNotEmpty()) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else MorroSpring.FloatSpringPlayful,
        label = "legalDotsScale"
    )

    val colors = morroColors()
    val selectedOverlayColor = ChessComHighlightYellow.copy(alpha = 0.56f)
    val lastMoveColor = ChessComHighlightYellow.copy(alpha = 0.45f)
    val legalDotColor = ComposeColor(0xFF000000).copy(alpha = 0.14f)
    val textMeasurer = rememberTextMeasurer()

    // King in check identification
    val inCheckKingSquare = remember(currentBoard) {
        when {
            ChessRules.isCheck(currentBoard, PieceColor.WHITE) -> currentBoard.kingSquare(PieceColor.WHITE)
            ChessRules.isCheck(currentBoard, PieceColor.BLACK) -> currentBoard.kingSquare(PieceColor.BLACK)
            else -> null
        }
    }

    LaunchedEffect(wrongCounter) {
        if (wrongCounter > 0) {
            delay(1_250)
            wrongFlashSquare = null
            if (!isSolved) {
                feedbackKind = if (currentStepIndex > 1) PuzzleBannerKind.GOOD else PuzzleBannerKind.NEUTRAL
            }
        }
    }

    fun triggerWrongMove(targetSquare: Square? = null) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        selectedSquare = null
        legalDestinations = emptyList()
        wrongFlashSquare = targetSquare
        feedbackKind = PuzzleBannerKind.WRONG
        wrongCounter++
        if (!reducedMotion) {
            scope.launch {
                shakeOffset.snapTo(0f)
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
                    durationMillis = 190,
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
            scriptedReply != null && scriptedReply in legalReplies &&
                ChessRules.hasMateInOne(boardBeforeReply.apply(scriptedReply)) -> scriptedReply
            else -> ChessEngine.pickSmartReply(boardBeforeReply)
                ?: ChessRules.pickReplyForForcedMateInTwo(boardBeforeReply)
        }

        if (chosenReply == null) {
            isSolved = true
            isAnimating = false
            feedbackKind = PuzzleBannerKind.SOLVED
            currentOnPuzzleSolved()
            return
        }

        delay(240)
        playAnimatedMove(boardBeforeReply, chosenReply)
        currentStepIndex = stepIdxAfterUserMove + 1
        isAnimating = false
    }

    fun commitValidatedMove(from: Square, to: Square, promotion: PieceType?) {
        if (!isUserTurnEnabled || isSolved) return

        val piece = currentBoard[from] ?: return
        if (piece.color != playerColor) return

        val legalMovesFrom = ChessRules.allLegalMoves(currentBoard).filter { it.from == from && it.to == to }
        if (legalMovesFrom.isEmpty()) {
            triggerWrongMove(to)
            return
        }

        val userMove = if (promotion != null) {
            ChessMove(from, to, promotion)
        } else {
            ChessMove(from, to, null)
        }
        if (userMove !in legalMovesFrom) {
            triggerWrongMove(to)
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
            isCheckmate -> true
            isFinalStep -> expectedUci != null && userMove.toUci() == expectedUci
            else -> (expectedUci != null && userMove.toUci() == expectedUci) ||
                ChessRules.isForcedMateInTwo(afterMove)
        }

        if (!isCorrect) {
            triggerWrongMove(to)
            return
        }

        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        wrongFlashSquare = null

        scope.launch {
            isAnimating = true
            selectedSquare = null
            legalDestinations = emptyList()
            pendingPromotion = null

            val updatedBoard = playAnimatedMove(currentBoard, userMove)
            val solvedNow = isFinalStep || isCheckmate || customResult == MoveResult.Solved
            if (solvedNow) {
                isSolved = true
                isAnimating = false
                feedbackKind = PuzzleBannerKind.SOLVED
                currentOnPuzzleSolved()
            } else {
                feedbackKind = PuzzleBannerKind.GOOD
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
            triggerWrongMove(to)
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

    // Initial setup move (Moves[0]) OR resume pending Mate-in-2 opponent reply after rotation
    LaunchedEffect(puzzleRecord.puzzleId, setupPlayed) {
        if (isSolved) return@LaunchedEffect

        if (!setupPlayed) {
            val setupUci = puzzleMoves.firstOrNull()
            val setupMove = setupUci?.let { ChessMove.fromUci(it) }
            val legalAtStart = ChessRules.allLegalMoves(initialBoard)

            if (setupMove != null && setupMove in legalAtStart) {
                isAnimating = true
                if (!reducedMotion) delay(100)
                playAnimatedMove(initialBoard, setupMove)
                delay(180)
                isAnimating = false
                setupPlayed = true
            } else {
                setupPlayed = true
                if (initialBoard.sideToMove != playerColor) {
                    isSolved = true
                    feedbackKind = PuzzleBannerKind.SOLVED
                    currentOnPuzzleSolved()
                }
            }
        } else if (currentBoard.sideToMove != playerColor && !isAnimating) {
            isAnimating = true
            playOpponentReply(currentBoard, currentStepIndex + 1)
        }
    }

    val totalUserMoves = if (puzzleMoves.size > 2) 2 else 1
    val completedUserMoves = ((currentStepIndex - 1) / 2).coerceIn(0, totalUserMoves)
    val effectiveKind = if (isSolved) PuzzleBannerKind.SOLVED else feedbackKind
    val bannerBgColor by animateColorAsState(
        targetValue = when (effectiveKind) {
            PuzzleBannerKind.SOLVED, PuzzleBannerKind.GOOD -> ChessComGreenBanner
            PuzzleBannerKind.WRONG -> ChessComCrimson
            PuzzleBannerKind.NEUTRAL -> ChessComStripSurface
        },
        animationSpec = tween(180),
        label = "chessBannerBg"
    )
    val bannerText = when {
        isSolved -> "Puzzle solved! Checkmate."
        effectiveKind == PuzzleBannerKind.WRONG -> "Incorrect move — try again"
        !setupPlayed || isAnimating -> "Opponent is playing..."
        effectiveKind == PuzzleBannerKind.GOOD && totalUserMoves == 2 -> "Best move! Finish the mate."
        totalUserMoves == 2 -> "Find Mate in 2 · Move ${(completedUserMoves + 1).coerceAtMost(2)} of 2"
        else -> "Find Checkmate in 1"
    }
    val ratingText = if (puzzleRecord.rating > 0) " · ${puzzleRecord.rating}" else ""

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chess.com Dark Top Player / Puzzle Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(ChessComFrameDark)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    ChessPieces.drawPiece(
                        scope = this,
                        piece = Piece(PieceType.KING, playerColor),
                        bounds = Rect(0f, 0f, size.width, size.height)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (playerColor == PieceColor.WHITE) "White to Move" else "Black to Move",
                    style = morroTypography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ComposeColor(0xFFF3F3F1)
                )
            }

            Text(
                text = (if (totalUserMoves == 2) "Mate in 2" else "Mate in 1") + ratingText,
                style = morroTypography().labelLarge.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                color = ComposeColor(0xFFB5B4B0)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Chess.com Dark Frame + 8x8 Classic Green Board
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = with(density) { shakeOffset.value.dp.toPx() }
                }
                .clip(RoundedCornerShape(10.dp))
                .background(ChessComFrameDark)
                .padding(5.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                val boardWidthPx = with(density) { maxWidth.toPx() }
                val squareSizePx = boardWidthPx / 8f
                val hairlinePx = with(density) { 1.dp.toPx() }
                val snapPadPx = with(density) { 6.dp.toPx() }
                val dragSlopPx = viewConfig.touchSlop

                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(isUserTurnEnabled, isFlipped, squareSizePx, pendingPromotion, dragSlopPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                currentOnUserInteraction()
                                if (!isUserTurnEnabled || isSolved) return@awaitEachGesture
                                if (pendingPromotion != null) {
                                    pendingPromotion = null
                                    return@awaitEachGesture
                                }

                                val startSquare = resolveTappedChessSquare(
                                    tapX = down.position.x,
                                    tapY = down.position.y,
                                    squareSizePx = squareSizePx,
                                    snapPadPx = snapPadPx,
                                    isFlipped = isFlipped
                                )

                                val startPiece = startSquare?.let { currentBoard[it] }
                                val canDragFromStart = startPiece != null && startPiece.color == playerColor
                                val startPos = down.position
                                var isDragging = false

                                if (startSquare != null) {
                                    pressedSquare = startSquare
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                        ?: break

                                    if (!change.pressed) {
                                        val releasePos = change.position
                                        change.consume()
                                        pressedSquare = null

                                        val endSquare = resolveTappedChessSquare(
                                            tapX = releasePos.x.coerceIn(0f, boardWidthPx - 1f),
                                            tapY = releasePos.y.coerceIn(0f, boardWidthPx - 1f),
                                            squareSizePx = squareSizePx,
                                            snapPadPx = snapPadPx,
                                            isFlipped = isFlipped
                                        )

                                        if (isDragging && startSquare != null) {
                                            draggingFrom = null
                                            dragPointerPx = null
                                            dragHoverSquare = null
                                            if (endSquare != null && endSquare != startSquare) {
                                                if (currentBoard[endSquare]?.color == playerColor) {
                                                    selectedSquare = endSquare
                                                    legalDestinations = ChessRules.legalMoves(currentBoard, endSquare)
                                                } else {
                                                    executeUserMove(startSquare, endSquare)
                                                }
                                            }
                                        } else if (endSquare != null) {
                                            onSquareTapped(endSquare)
                                        }
                                        break
                                    }

                                    if (change.positionChanged()) {
                                        val dist = hypot(
                                            change.position.x - startPos.x,
                                            change.position.y - startPos.y
                                        )
                                        if (!isDragging && canDragFromStart && startSquare != null && dist > dragSlopPx) {
                                            isDragging = true
                                            pressedSquare = null
                                            draggingFrom = startSquare
                                            selectedSquare = startSquare
                                            legalDestinations = ChessRules.legalMoves(currentBoard, startSquare)
                                        }
                                        if (isDragging) {
                                            change.consume()
                                            dragPointerPx = change.position
                                            dragHoverSquare = resolveTappedChessSquare(
                                                tapX = change.position.x.coerceIn(0f, boardWidthPx - 1f),
                                                tapY = change.position.y.coerceIn(0f, boardWidthPx - 1f),
                                                squareSizePx = squareSizePx,
                                                snapPadPx = 0f,
                                                isFlipped = isFlipped
                                            )
                                        }
                                    }
                                }

                                pressedSquare = null
                                draggingFrom = null
                                dragPointerPx = null
                                dragHoverSquare = null
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

                            drawRect(
                                color = if (isLight) ChessComLightSquare else ChessComDarkSquare,
                                topLeft = squareTopLeft,
                                size = sqSize
                            )

                            if (lastMove != null && (square == lastMove.from || square == lastMove.to)) {
                                drawRect(
                                    color = lastMoveColor,
                                    topLeft = squareTopLeft,
                                    size = sqSize
                                )
                            }

                            if (square == selectedSquare) {
                                drawRect(
                                    color = selectedOverlayColor,
                                    topLeft = squareTopLeft,
                                    size = sqSize
                                )
                            }

                            if (square == pressedSquare) {
                                drawRect(
                                    color = ComposeColor.Black.copy(alpha = 0.08f),
                                    topLeft = squareTopLeft,
                                    size = sqSize
                                )
                            }

                            if (square == wrongFlashSquare) {
                                drawRect(
                                    color = ChessComCrimson.copy(alpha = 0.55f),
                                    topLeft = squareTopLeft,
                                    size = sqSize
                                )
                            }

                            if (square == inCheckKingSquare) {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            ChessComCheckGlow.copy(alpha = 0.92f),
                                            ChessComCheckGlow.copy(alpha = 0.45f),
                                            ComposeColor.Transparent
                                        ),
                                        center = squareCenter,
                                        radius = squareSizePx * 0.56f
                                    ),
                                    topLeft = squareTopLeft,
                                    size = sqSize
                                )
                            }

                            // Chess.com white border ring on drag-hover destination
                            if (draggingFrom != null && square == dragHoverSquare) {
                                val pad = hairlinePx * 2f
                                drawRect(
                                    color = ComposeColor.White.copy(alpha = 0.65f),
                                    topLeft = Offset(squareTopLeft.x + pad, squareTopLeft.y + pad),
                                    size = Size(squareSizePx - pad * 2f, squareSizePx - pad * 2f),
                                    style = Stroke(width = hairlinePx * 2.5f)
                                )
                            }

                            if (square in legalDestinations && legalDotsScale > 0.02f) {
                                if (currentBoard[square] != null) {
                                    drawCircle(
                                        color = legalDotColor,
                                        radius = squareSizePx * 0.42f * legalDotsScale,
                                        center = squareCenter,
                                        style = Stroke(width = squareSizePx * 0.085f)
                                    )
                                } else {
                                    drawCircle(
                                        color = legalDotColor,
                                        radius = squareSizePx * 0.165f * legalDotsScale,
                                        center = squareCenter
                                    )
                                }
                            }

                            // Classic Chess.com coordinates
                            val coordColor = if (isLight) ChessComDarkSquare else ChessComLightSquare
                            val coordStyle = TextStyle(
                                color = coordColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val coordPad = squareSizePx * 0.06f
                            if (col == 0) {
                                val rankLabel = ('1' + square.rank).toString()
                                val measured = textMeasurer.measure(rankLabel, style = coordStyle)
                                drawText(
                                    textLayoutResult = measured,
                                    topLeft = Offset(
                                        squareTopLeft.x + coordPad,
                                        squareTopLeft.y + coordPad
                                    )
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

                            val piece = currentBoard[square]
                            val isHiddenForMove = (square == movingPiece?.from) || (square == draggingFrom)
                            if (piece != null && !isHiddenForMove) {
                                val scaleBoost = if (square == selectedSquare) selectedPiecePop else 1f
                                val baseInset = squareSizePx * 0.07f
                                val halfDrawn = (squareSizePx / 2f - baseInset) * scaleBoost
                                val pieceBounds = Rect(
                                    squareCenter.x - halfDrawn,
                                    squareCenter.y - halfDrawn,
                                    squareCenter.x + halfDrawn,
                                    squareCenter.y + halfDrawn
                                )
                                ChessPieces.drawPiece(
                                    scope = this,
                                    piece = piece,
                                    bounds = pieceBounds
                                )
                            }
                        }
                    }

                    // Smooth move-slide animation
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
                        val liftArc = sin(t * PI).toFloat()
                        val curX = fromCenter.x + (toCenter.x - fromCenter.x) * t
                        val curY = fromCenter.y + (toCenter.y - fromCenter.y) * t - liftArc * (squareSizePx * 0.06f)

                        if (liftArc > 0.05f) {
                            drawOval(
                                color = ComposeColor.Black.copy(alpha = 0.20f * liftArc),
                                topLeft = Offset(curX - squareSizePx * 0.26f, curY + squareSizePx * 0.28f),
                                size = Size(squareSizePx * 0.52f, squareSizePx * 0.14f)
                            )
                        }

                        val inset = squareSizePx * 0.07f
                        val halfSize = (squareSizePx / 2f - inset) * (1f + 0.10f * liftArc)
                        ChessPieces.drawPiece(
                            scope = this,
                            piece = anim.piece,
                            bounds = Rect(curX - halfSize, curY - halfSize, curX + halfSize, curY + halfSize)
                        )
                    }

                    // Active dragged piece floating directly under finger
                    val activeDragSquare = draggingFrom
                    val activeDragPos = dragPointerPx
                    if (activeDragSquare != null && activeDragPos != null) {
                        val draggedPiece = currentBoard[activeDragSquare]
                        if (draggedPiece != null) {
                            val drawX = activeDragPos.x.coerceIn(0f, boardWidthPx)
                            val drawY = (activeDragPos.y - squareSizePx * 0.16f).coerceIn(0f, boardWidthPx)
                            val halfSize = squareSizePx * 0.54f
                            drawOval(
                                color = ComposeColor.Black.copy(alpha = 0.26f),
                                topLeft = Offset(drawX - squareSizePx * 0.30f, drawY + squareSizePx * 0.34f),
                                size = Size(squareSizePx * 0.60f, squareSizePx * 0.16f)
                            )
                            ChessPieces.drawPiece(
                                scope = this,
                                piece = draggedPiece,
                                bounds = Rect(drawX - halfSize, drawY - halfSize, drawX + halfSize, drawY + halfSize)
                            )
                        }
                    }
                }

                // TalkBack & Switch-Access overlay WITHOUT .clickable so Canvas drag/tap snapping works cleanly
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

                // Pawn promotion picker
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
                                .clip(morroShapes().Card)
                                .background(ChessComFrameDark)
                                .morroGlassBorder(radius = 16.dp, topAlpha = 0.30f, bottomAlpha = 0.08f, tint = ChessComGreenBanner)
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
                                style = morroTypography().titleMedium,
                                color = ComposeColor.White
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
                                            .clip(morroShapes().Button)
                                            .background(ChessComLightSquare)
                                            .morroHairline(radius = 12.dp, color = ChessComDarkSquare)
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

        Spacer(modifier = Modifier.height(6.dp))

        // Chess.com Bottom Puzzle Feedback Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(bannerBgColor)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = bannerText,
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (fadeIn(tween(160)) + slideInVertically { it / 3 })
                            .togetherWith(fadeOut(tween(110)))
                    }
                },
                label = "chessFeedbackBanner"
            ) { msg ->
                Text(
                    text = msg,
                    style = morroTypography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ComposeColor.White
                )
            }
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
