package com.morro.alarm.ui.puzzle

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import com.morro.alarm.puzzle.chess.ChessBoard
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
import com.morro.alarm.ui.theme.morroColors
import com.morro.alarm.ui.theme.morroHairline
import com.morro.alarm.ui.theme.morroShapes
import com.morro.alarm.ui.theme.morroTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

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

/**
 * 8×8 Canvas-drawn chess board view designed for morning cognitive gate puzzles.
 *
 * Rules:
 * - Square tones: light = surfaceHigh (#231F19), dark = surface (#0B0A08) (readable on AMOLED).
 * - Sizing: fits available width, 41–43dp per square on a 360dp screen, never scrolls.
 * - Interaction: tap-to-select, tap-to-destination (no drag and drop).
 * - Selected square: honey at 20% overlay + borderActive hairline.
 * - Legal destinations: textPrimary at 18% dots, 8dp radius circle.
 * - Last move: honey at 10% on both squares (from & to).
 * - King in check: error hairline ring on that square.
 * - Axis-aligned nearest-square tap snapping with +6dp tolerance band (covers corners cleanly).
 * - Opponent setup move (Moves[0]) auto-plays on entry with both squares highlighted for 600ms before user may act.
 * - Mate-in-2 handling: accepts immediate mate-in-1, Moves[1], OR any forced mate-in-2, then auto-plays opponent reply and lets user deliver checkmate.
 * - Pawn promotion: presents a Morro-styled Q/R/B/N picker without spoiling underpromotions.
 * - Survival: board state persists across configuration changes via rememberSaveable.
 * - Reduced motion: honors rememberReducedMotion().
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

    val isUserTurnEnabled = setupPlayed &&
        !isSolved &&
        !isAnimating &&
        currentBoard.sideToMove == playerColor

    // Animations
    val shakeOffset = remember(puzzleRecord.puzzleId) { Animatable(0f) }
    val moveProgress = remember(puzzleRecord.puzzleId) { Animatable(0f) }
    var movingPiece by remember(puzzleRecord.puzzleId) { mutableStateOf<MovingPieceState?>(null) }

    // Colors
    val colors = morroColors()
    val lightSquareColor = colors.surfaceHigh
    val darkSquareColor = colors.surface
    val selectedOverlayColor = colors.honey.copy(alpha = 0.20f)
    val selectedBorderColor = colors.borderActive
    val lastMoveColor = colors.honey.copy(alpha = 0.10f)
    val legalDotColor = colors.textPrimary.copy(alpha = 0.18f)
    val kingCheckColor = colors.error
    val pieceColor = colors.textPrimary
    val pressedOverlayColor = colors.textPrimary.copy(alpha = 0.08f)

    // King in check identification
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
            scriptedReply != null && scriptedReply in legalReplies &&
                ChessRules.hasMateInOne(boardBeforeReply.apply(scriptedReply)) -> scriptedReply
            else -> ChessRules.pickReplyForForcedMateInTwo(boardBeforeReply)
        }

        if (chosenReply == null) {
            // No legal reply (checkmate/stalemate) -> if checkmate, solved!
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

    fun commitValidatedMove(from: Square, to: Square, promotion: PieceType?) {
        if (!isUserTurnEnabled || isSolved) return

        val piece = currentBoard[from] ?: return
        if (piece.color != playerColor) return

        val legalMovesFrom = ChessRules.allLegalMoves(currentBoard).filter { it.from == from && it.to == to }
        if (legalMovesFrom.isEmpty()) {
            triggerWrongMove()
            return
        }

        val userMove = if (promotion != null) {
            ChessMove(from, to, promotion)
        } else {
            ChessMove(from, to, null)
        }
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
            isCheckmate -> true
            isFinalStep -> expectedUci != null && userMove.toUci() == expectedUci
            else -> (expectedUci != null && userMove.toUci() == expectedUci) ||
                ChessRules.isForcedMateInTwo(afterMove)
        }

        if (!isCorrect) {
            triggerWrongMove()
            return
        }

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

    // Initial setup move (Moves[0]) OR resume pending Mate-in-2 opponent reply after rotation
    LaunchedEffect(puzzleRecord.puzzleId, setupPlayed) {
        if (isSolved) return@LaunchedEffect

        if (!setupPlayed) {
            val setupUci = puzzleMoves.firstOrNull()
            val setupMove = setupUci?.let { ChessMove.fromUci(it) }
            val legalAtStart = ChessRules.allLegalMoves(initialBoard)

            if (setupMove != null && setupMove in legalAtStart) {
                isAnimating = true
                if (!reducedMotion) delay(120)
                playAnimatedMove(initialBoard, setupMove)
                delay(600)
                isAnimating = false
                setupPlayed = true
            } else {
                // Corrupt/empty setup move fallback: allow player if already their turn, else fail safely
                setupPlayed = true
                if (initialBoard.sideToMove != playerColor) {
                    isSolved = true
                    currentOnPuzzleSolved()
                }
            }
        } else if (currentBoard.sideToMove != playerColor && !isAnimating) {
            // Resumed mid-delay after rotation on opponent's turn
            isAnimating = true
            playOpponentReply(currentBoard, currentStepIndex + 1)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (playerColor == PieceColor.WHITE) "White to play" else "Black to play",
            style = morroTypography().headlineMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = if (puzzleMoves.size > 2) "Find the mate in two moves" else "Find the checkmate in one move",
            style = morroTypography().bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Shake sits BEFORE clip/background so the entire framed board well moves cleanly
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = with(density) { shakeOffset.value.dp.toPx() }
                }
                .clip(morroShapes().Card)
                .background(colors.surfaceCard)
                .morroHairline(radius = 16.dp, color = colors.borderSubtle)
                .padding(6.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                val boardWidthPx = with(density) { maxWidth.toPx() }
                val squareSizePx = boardWidthPx / 8f
                val dotRadiusPx = with(density) { 8.dp.toPx() }
                val hairlinePx = with(density) { 1.dp.toPx() }
                val snapPadPx = with(density) { 6.dp.toPx() }

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

                                val closestSquare = resolveTappedChessSquare(
                                    tapX = offset.x,
                                    tapY = offset.y,
                                    squareSizePx = squareSizePx,
                                    snapPadPx = snapPadPx,
                                    isFlipped = isFlipped
                                )

                                if (closestSquare != null) {
                                    scope.launch {
                                        pressedSquare = closestSquare
                                        delay(MorroMotion.PressMillis.toLong())
                                        if (pressedSquare == closestSquare) {
                                            pressedSquare = null
                                        }
                                    }
                                    onSquareTapped(closestSquare)
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

                            drawRect(
                                color = if (isLight) lightSquareColor else darkSquareColor,
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
                                drawRect(
                                    color = selectedBorderColor,
                                    topLeft = Offset(squareTopLeft.x + hairlinePx / 2f, squareTopLeft.y + hairlinePx / 2f),
                                    size = Size(squareSizePx - hairlinePx, squareSizePx - hairlinePx),
                                    style = Stroke(width = hairlinePx)
                                )
                            }

                            if (square == pressedSquare) {
                                drawRect(
                                    color = pressedOverlayColor,
                                    topLeft = squareTopLeft,
                                    size = sqSize
                                )
                            }

                            if (square == inCheckKingSquare) {
                                val pad = hairlinePx * 2f
                                drawRoundRect(
                                    color = kingCheckColor,
                                    topLeft = Offset(squareTopLeft.x + pad, squareTopLeft.y + pad),
                                    size = Size(squareSizePx - pad * 2f, squareSizePx - pad * 2f),
                                    cornerRadius = CornerRadius(hairlinePx * 4f, hairlinePx * 4f),
                                    style = Stroke(width = hairlinePx * 2f)
                                )
                            }

                            if (square in legalDestinations) {
                                val center = Offset(
                                    squareTopLeft.x + squareSizePx / 2f,
                                    squareTopLeft.y + squareSizePx / 2f
                                )
                                drawCircle(
                                    color = legalDotColor,
                                    radius = dotRadiusPx,
                                    center = center
                                )
                            }

                            val piece = currentBoard[square]
                            if (piece != null && square != movingPiece?.from) {
                                val inset = squareSizePx * 0.10f
                                val pieceBounds = Rect(
                                    squareTopLeft.x + inset,
                                    squareTopLeft.y + inset,
                                    squareTopLeft.x + squareSizePx - inset,
                                    squareTopLeft.y + squareSizePx - inset
                                )
                                ChessPieces.drawPiece(
                                    scope = this,
                                    piece = piece,
                                    bounds = pieceBounds,
                                    color = pieceColor
                                )
                            }
                        }
                    }

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

                        val inset = squareSizePx * 0.10f
                        val halfSize = squareSizePx / 2f - inset
                        val movingBounds = Rect(
                            curX - halfSize,
                            curY - halfSize,
                            curX + halfSize,
                            curY + halfSize
                        )
                        ChessPieces.drawPiece(
                            scope = this,
                            piece = anim.piece,
                            bounds = movingBounds,
                            color = pieceColor
                        )
                    }
                }

                // TalkBack & Switch-Access overlay WITHOUT .clickable so pointer taps hit Canvas snapping!
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

                // Pawn promotion picker (never peeks at expectedUci, never blocks underpromotion mates)
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
                                .background(colors.surfaceElevated)
                                .morroHairline(radius = 16.dp, color = colors.borderActive)
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
                                            .clip(morroShapes().Button)
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
                                                bounds = Rect(0f, 0f, size.width, size.height),
                                                color = pieceColor
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
