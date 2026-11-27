package com.morro.alarm.ui.puzzle

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morro.alarm.puzzle.chess.Color as PieceColor
import com.morro.alarm.puzzle.chess.Piece
import com.morro.alarm.puzzle.chess.PieceType

/**
 * Vector chess pieces rendered cleanly on Canvas via Bézier [Path]s (six silhouettes, zero font
 * glyphs, zero bitmap/drawable assets).
 *
 * Scoped specifically for the wooden board:
 *  - White pieces: fill `#F7F1E3`, stroke `#4A3524` at `1.2.dp`
 *  - Black pieces: fill `#2E2118`, stroke `#120C08` at `1.2.dp`
 *  - Tight readability drop shadow ONLY on pieces: offset `(0, 1.dp)`, blur `2.dp`, `#000000` at `0.25f`.
 */
object ChessPieces {

    val StrokeWidth: Dp = 1.2.dp
    private val WoodWhiteFill = Color(0xFFF7F1E3)
    private val WoodWhiteStroke = Color(0xFF4A3524)
    private val WoodBlackFill = Color(0xFF2E2118)
    private val WoodBlackStroke = Color(0xFF120C08)
    private val WoodBlackDetail = Color(0xFF6B5340)
    private val ShadowColor = Color.Black.copy(alpha = 0.25f)

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var cachedBlurPx = -1f

    /**
     * Draws [piece] inside [bounds] on wood with its scoped tight drop shadow.
     */
    fun drawPiece(
        scope: DrawScope,
        piece: Piece,
        bounds: Rect,
        color: Color = Color.Unspecified,
        strokeWidth: Dp = StrokeWidth
    ) {
        val w = bounds.width
        val h = bounds.height
        val l = bounds.left
        val t = bounds.top
        fun p(nx: Float, ny: Float): Offset = Offset(l + nx * w, t + ny * h)

        val isWhite = piece.color == PieceColor.WHITE
        val fillColor = if (isWhite) WoodWhiteFill else WoodBlackFill
        val outlineColor = if (isWhite) WoodWhiteStroke else WoodBlackStroke
        val detailColor = if (isWhite) WoodWhiteStroke.copy(alpha = 0.55f) else WoodBlackDetail
        val strokePx = with(scope) { strokeWidth.toPx() }
        val strokeStyle = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val detailPx = (strokePx * 0.85f).coerceAtLeast(1f)

        val bodyPath = createPiecePath(piece.type, bounds)

        // 1. Tight piece-only drop shadow: offset (0, 1dp), blur 2dp, #000000 @ 0.25 alpha
        val shadowOffsetY = with(scope) { 1.dp.toPx() }
        val shadowBlurPx = with(scope) { 2.dp.toPx() }.coerceAtLeast(0.5f)
        scope.drawIntoCanvas { canvas ->
            if (cachedBlurPx != shadowBlurPx) {
                cachedBlurPx = shadowBlurPx
                shadowPaint.maskFilter = BlurMaskFilter(shadowBlurPx, BlurMaskFilter.Blur.NORMAL)
            }
            shadowPaint.color = ShadowColor.toArgb()
            val nativeCanvas = canvas.nativeCanvas
            nativeCanvas.save()
            nativeCanvas.translate(0f, shadowOffsetY)
            nativeCanvas.drawPath(bodyPath.asAndroidPath(), shadowPaint)
            nativeCanvas.restore()
        }

        // 2. Main piece fill
        scope.drawPath(path = bodyPath, color = fillColor, style = Fill)

        // 3. Piece outline at 1.2dp
        scope.drawPath(path = bodyPath, color = outlineColor, style = strokeStyle)

        // 4. Clean internal silhouette markings for rapid glance recognition at 6 a.m.
        when (piece.type) {
            PieceType.PAWN -> {
                scope.drawLine(
                    color = detailColor,
                    start = p(0.39f, 0.50f),
                    end = p(0.61f, 0.50f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                scope.drawLine(
                    color = detailColor,
                    start = p(0.30f, 0.78f),
                    end = p(0.70f, 0.78f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
            }

            PieceType.ROOK -> {
                scope.drawLine(
                    color = detailColor,
                    start = p(0.32f, 0.41f),
                    end = p(0.68f, 0.41f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                scope.drawLine(
                    color = detailColor,
                    start = p(0.30f, 0.77f),
                    end = p(0.70f, 0.77f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
            }

            PieceType.KNIGHT -> {
                scope.drawCircle(
                    color = detailColor,
                    radius = (w * 0.028f).coerceAtLeast(1.1f),
                    center = p(0.415f, 0.345f)
                )
                scope.drawLine(
                    color = detailColor,
                    start = p(0.28f, 0.79f),
                    end = p(0.72f, 0.79f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
            }

            PieceType.BISHOP -> {
                val orbCenter = p(0.50f, 0.145f)
                val orbR = w * 0.044f
                scope.drawCircle(color = fillColor, radius = orbR, center = orbCenter)
                scope.drawCircle(color = outlineColor, radius = orbR, center = orbCenter, style = strokeStyle)
                scope.drawLine(
                    color = detailColor,
                    start = p(0.44f, 0.34f),
                    end = p(0.56f, 0.25f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                scope.drawLine(
                    color = detailColor,
                    start = p(0.39f, 0.525f),
                    end = p(0.61f, 0.525f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
            }

            PieceType.QUEEN -> {
                val pearlPositions = listOf(
                    p(0.18f, 0.29f),
                    p(0.33f, 0.21f),
                    p(0.50f, 0.17f),
                    p(0.67f, 0.21f),
                    p(0.82f, 0.29f)
                )
                val pearlR = w * 0.039f
                for (pt in pearlPositions) {
                    scope.drawCircle(color = fillColor, radius = pearlR, center = pt)
                    scope.drawCircle(color = outlineColor, radius = pearlR, center = pt, style = strokeStyle)
                }
                scope.drawLine(
                    color = detailColor,
                    start = p(0.35f, 0.64f),
                    end = p(0.65f, 0.64f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                scope.drawLine(
                    color = detailColor,
                    start = p(0.28f, 0.78f),
                    end = p(0.72f, 0.78f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
            }

            PieceType.KING -> {
                scope.drawLine(
                    color = detailColor,
                    start = p(0.50f, 0.34f),
                    end = p(0.50f, 0.56f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                scope.drawLine(
                    color = detailColor,
                    start = p(0.35f, 0.58f),
                    end = p(0.65f, 0.58f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                scope.drawLine(
                    color = detailColor,
                    start = p(0.28f, 0.78f),
                    end = p(0.72f, 0.78f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    /**
     * Constructs normalized vector [Path] silhouettes fitted cleanly into [bounds].
     */
    fun createPiecePath(type: PieceType, bounds: Rect): Path {
        val l = bounds.left
        val t = bounds.top
        val w = bounds.width
        val h = bounds.height
        fun x(nx: Float) = l + nx * w
        fun y(ny: Float) = t + ny * h

        return Path().apply {
            when (type) {
                PieceType.PAWN -> {
                    moveTo(x(0.25f), y(0.88f))
                    lineTo(x(0.75f), y(0.88f))
                    cubicTo(x(0.77f), y(0.88f), x(0.77f), y(0.80f), x(0.68f), y(0.77f))
                    cubicTo(x(0.64f), y(0.66f), x(0.59f), y(0.56f), x(0.57f), y(0.50f))
                    cubicTo(x(0.64f), y(0.49f), x(0.64f), y(0.44f), x(0.57f), y(0.43f))
                    cubicTo(x(0.66f), y(0.36f), x(0.64f), y(0.21f), x(0.50f), y(0.21f))
                    cubicTo(x(0.36f), y(0.21f), x(0.34f), y(0.36f), x(0.43f), y(0.43f))
                    cubicTo(x(0.36f), y(0.44f), x(0.36f), y(0.49f), x(0.43f), y(0.50f))
                    cubicTo(x(0.41f), y(0.56f), x(0.36f), y(0.66f), x(0.32f), y(0.77f))
                    cubicTo(x(0.23f), y(0.80f), x(0.23f), y(0.88f), x(0.25f), y(0.88f))
                    close()
                }

                PieceType.ROOK -> {
                    moveTo(x(0.22f), y(0.88f))
                    lineTo(x(0.78f), y(0.88f))
                    lineTo(x(0.78f), y(0.81f))
                    lineTo(x(0.70f), y(0.77f))
                    lineTo(x(0.67f), y(0.43f))
                    lineTo(x(0.74f), y(0.39f))
                    lineTo(x(0.74f), y(0.21f))
                    lineTo(x(0.63f), y(0.21f))
                    lineTo(x(0.63f), y(0.29f))
                    lineTo(x(0.56f), y(0.29f))
                    lineTo(x(0.56f), y(0.21f))
                    lineTo(x(0.44f), y(0.21f))
                    lineTo(x(0.44f), y(0.29f))
                    lineTo(x(0.37f), y(0.29f))
                    lineTo(x(0.37f), y(0.21f))
                    lineTo(x(0.26f), y(0.21f))
                    lineTo(x(0.26f), y(0.39f))
                    lineTo(x(0.33f), y(0.43f))
                    lineTo(x(0.30f), y(0.77f))
                    lineTo(x(0.22f), y(0.81f))
                    close()
                }

                PieceType.KNIGHT -> {
                    moveTo(x(0.22f), y(0.88f))
                    lineTo(x(0.78f), y(0.88f))
                    cubicTo(x(0.78f), y(0.81f), x(0.76f), y(0.77f), x(0.74f), y(0.76f))
                    cubicTo(x(0.79f), y(0.52f), x(0.74f), y(0.28f), x(0.59f), y(0.18f))
                    lineTo(x(0.54f), y(0.23f))
                    cubicTo(x(0.51f), y(0.19f), x(0.48f), y(0.17f), x(0.45f), y(0.17f))
                    lineTo(x(0.44f), y(0.23f))
                    cubicTo(x(0.36f), y(0.26f), x(0.26f), y(0.37f), x(0.22f), y(0.48f))
                    cubicTo(x(0.20f), y(0.53f), x(0.25f), y(0.57f), x(0.31f), y(0.55f))
                    cubicTo(x(0.36f), y(0.54f), x(0.41f), y(0.51f), x(0.45f), y(0.48f))
                    cubicTo(x(0.43f), y(0.58f), x(0.33f), y(0.65f), x(0.27f), y(0.77f))
                    cubicTo(x(0.22f), y(0.80f), x(0.22f), y(0.85f), x(0.22f), y(0.88f))
                    close()
                }

                PieceType.BISHOP -> {
                    moveTo(x(0.22f), y(0.88f))
                    lineTo(x(0.78f), y(0.88f))
                    cubicTo(x(0.79f), y(0.82f), x(0.75f), y(0.78f), x(0.67f), y(0.76f))
                    cubicTo(x(0.61f), y(0.67f), x(0.59f), y(0.58f), x(0.58f), y(0.53f))
                    cubicTo(x(0.65f), y(0.51f), x(0.65f), y(0.46f), x(0.59f), y(0.45f))
                    cubicTo(x(0.67f), y(0.38f), x(0.64f), y(0.23f), x(0.50f), y(0.18f))
                    cubicTo(x(0.36f), y(0.23f), x(0.33f), y(0.38f), x(0.41f), y(0.45f))
                    cubicTo(x(0.35f), y(0.46f), x(0.35f), y(0.51f), x(0.42f), y(0.53f))
                    cubicTo(x(0.41f), y(0.58f), x(0.39f), y(0.67f), x(0.33f), y(0.76f))
                    cubicTo(x(0.25f), y(0.78f), x(0.21f), y(0.82f), x(0.22f), y(0.88f))
                    close()
                }

                PieceType.QUEEN -> {
                    moveTo(x(0.22f), y(0.88f))
                    lineTo(x(0.78f), y(0.88f))
                    cubicTo(x(0.79f), y(0.81f), x(0.75f), y(0.78f), x(0.71f), y(0.76f))
                    lineTo(x(0.81f), y(0.32f))
                    quadraticTo(x(0.70f), y(0.45f), x(0.66f), y(0.24f))
                    quadraticTo(x(0.56f), y(0.42f), x(0.50f), y(0.20f))
                    quadraticTo(x(0.44f), y(0.42f), x(0.34f), y(0.24f))
                    quadraticTo(x(0.30f), y(0.45f), x(0.19f), y(0.32f))
                    lineTo(x(0.29f), y(0.76f))
                    cubicTo(x(0.25f), y(0.78f), x(0.21f), y(0.81f), x(0.22f), y(0.88f))
                    close()
                }

                PieceType.KING -> {
                    moveTo(x(0.22f), y(0.88f))
                    lineTo(x(0.78f), y(0.88f))
                    cubicTo(x(0.79f), y(0.81f), x(0.75f), y(0.78f), x(0.70f), y(0.76f))
                    cubicTo(x(0.68f), y(0.68f), x(0.64f), y(0.62f), x(0.63f), y(0.58f))
                    cubicTo(x(0.78f), y(0.48f), x(0.79f), y(0.30f), x(0.63f), y(0.28f))
                    cubicTo(x(0.56f), y(0.27f), x(0.53f), y(0.31f), x(0.525f), y(0.33f))
                    lineTo(x(0.525f), y(0.24f))
                    lineTo(x(0.585f), y(0.24f))
                    lineTo(x(0.585f), y(0.185f))
                    lineTo(x(0.525f), y(0.185f))
                    lineTo(x(0.525f), y(0.125f))
                    lineTo(x(0.475f), y(0.125f))
                    lineTo(x(0.475f), y(0.185f))
                    lineTo(x(0.415f), y(0.185f))
                    lineTo(x(0.415f), y(0.24f))
                    lineTo(x(0.475f), y(0.24f))
                    lineTo(x(0.475f), y(0.33f))
                    cubicTo(x(0.47f), y(0.31f), x(0.44f), y(0.27f), x(0.37f), y(0.28f))
                    cubicTo(x(0.21f), y(0.30f), x(0.22f), y(0.48f), x(0.37f), y(0.58f))
                    cubicTo(x(0.36f), y(0.62f), x(0.32f), y(0.68f), x(0.30f), y(0.76f))
                    cubicTo(x(0.25f), y(0.78f), x(0.21f), y(0.81f), x(0.22f), y(0.88f))
                    close()
                }
            }
        }
    }
}
