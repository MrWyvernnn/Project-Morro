package com.morro.alarm.ui.puzzle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.morro.alarm.puzzle.chess.Color as PieceColor
import com.morro.alarm.puzzle.chess.Piece
import com.morro.alarm.puzzle.chess.PieceType

/**
 * Authentic Chess.com "Neo" vector piece set rendered smoothly via Canvas Bézier curves.
 *
 * Visual language matches Chess.com's default Neo set:
 * - Clean minimalist Staunton curves with spherical finials (Queen pearls, Bishop cap, Pawn orb).
 * - White pieces: crisp bright ivory/white (#FFFFFF) with `#D8D7D0` base shading and `#312E2B` outline.
 * - Black pieces: deep matte charcoal (#4B4947) with `#22201F` outline and subtle `#B8B5B0` internal
 *   slit/crown accents for high readability on both `#769656` dark green and `#EEEED2` cream squares.
 */
object ChessPieces {

    val StrokeWidth: Dp = 1.6.dp
    private val NeoWhiteFill = Color(0xFFFFFFFF)
    private val NeoWhiteOutline = Color(0xFF312E2B)
    private val NeoWhiteShade = Color(0xFFDAD9D2)
    private val NeoBlackFill = Color(0xFF4B4947)
    private val NeoBlackOutline = Color(0xFF211F1E)
    private val NeoBlackDetail = Color(0xFFBDBAB4)

    /**
     * Draws the given [piece] inside [bounds] using the Chess.com Neo style.
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
        val fillColor = if (isWhite) NeoWhiteFill else NeoBlackFill
        val outlineColor = if (isWhite) NeoWhiteOutline else NeoBlackOutline
        val accentColor = if (isWhite) NeoWhiteShade else NeoBlackDetail
        val strokePx = with(scope) { (w * 0.036f).coerceIn(1.3f, strokeWidth.toPx() * 1.25f) }
        val strokeStyle = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val detailPx = (strokePx * 0.80f).coerceAtLeast(1.1f)

        val bodyPath = createPiecePath(piece.type, bounds)

        // 1. Main body fill
        scope.drawPath(path = bodyPath, color = fillColor, style = Fill)

        // 2. Subtle base shadow stripe so pieces feel grounded like Chess.com Neo
        if (piece.type != PieceType.PAWN) {
            scope.drawLine(
                color = accentColor.copy(alpha = if (isWhite) 0.75f else 0.30f),
                start = p(0.28f, 0.845f),
                end = p(0.72f, 0.845f),
                strokeWidth = (strokePx * 1.15f).coerceAtLeast(1.4f),
                cap = StrokeCap.Round
            )
        }

        // 3. Outer crisp silhouette stroke
        scope.drawPath(path = bodyPath, color = outlineColor, style = strokeStyle)

        // 4. Piece-specific Neo signatures (pearls, slits, eyes, collar rings)
        when (piece.type) {
            PieceType.PAWN -> {
                // Collar ring accent
                scope.drawLine(
                    color = outlineColor,
                    start = p(0.39f, 0.50f),
                    end = p(0.61f, 0.50f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                // Base rim line
                scope.drawLine(
                    color = if (isWhite) NeoWhiteOutline.copy(alpha = 0.45f) else NeoBlackDetail.copy(alpha = 0.45f),
                    start = p(0.30f, 0.78f),
                    end = p(0.70f, 0.78f),
                    strokeWidth = detailPx * 0.9f,
                    cap = StrokeCap.Round
                )
            }

            PieceType.ROOK -> {
                // Upper parapet line + plinth line
                scope.drawLine(
                    color = if (isWhite) NeoWhiteOutline.copy(alpha = 0.60f) else NeoBlackDetail.copy(alpha = 0.50f),
                    start = p(0.32f, 0.41f),
                    end = p(0.68f, 0.41f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                scope.drawLine(
                    color = if (isWhite) NeoWhiteOutline.copy(alpha = 0.55f) else NeoBlackDetail.copy(alpha = 0.48f),
                    start = p(0.30f, 0.77f),
                    end = p(0.70f, 0.77f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
            }

            PieceType.KNIGHT -> {
                // Crisp Neo eye & subtle muzzle detail
                val eyeRadius = (w * 0.030f).coerceAtLeast(1.2f)
                scope.drawCircle(
                    color = if (isWhite) NeoWhiteOutline else NeoBlackDetail,
                    radius = eyeRadius,
                    center = p(0.415f, 0.345f)
                )
                // Nostril slash
                scope.drawCircle(
                    color = if (isWhite) NeoWhiteOutline else NeoBlackDetail,
                    radius = (w * 0.019f).coerceAtLeast(0.9f),
                    center = p(0.285f, 0.485f)
                )
                // Base plinth line
                scope.drawLine(
                    color = if (isWhite) NeoWhiteOutline.copy(alpha = 0.50f) else NeoBlackDetail.copy(alpha = 0.48f),
                    start = p(0.28f, 0.79f),
                    end = p(0.72f, 0.79f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
            }

            PieceType.BISHOP -> {
                // Finial orb atop mitre
                val orbCenter = p(0.50f, 0.145f)
                val orbR = w * 0.044f
                scope.drawCircle(color = fillColor, radius = orbR, center = orbCenter)
                scope.drawCircle(color = outlineColor, radius = orbR, center = orbCenter, style = strokeStyle)

                // Classic Chess.com diagonal mitre cross-cut
                val cutColor = if (isWhite) NeoWhiteOutline else NeoBlackDetail
                scope.drawLine(
                    color = cutColor,
                    start = p(0.44f, 0.34f),
                    end = p(0.56f, 0.25f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                scope.drawLine(
                    color = cutColor,
                    start = p(0.47f, 0.26f),
                    end = p(0.53f, 0.33f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                // Collar line
                scope.drawLine(
                    color = if (isWhite) NeoWhiteOutline.copy(alpha = 0.65f) else NeoBlackDetail.copy(alpha = 0.55f),
                    start = p(0.39f, 0.525f),
                    end = p(0.61f, 0.525f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
            }

            PieceType.QUEEN -> {
                // Five Neo spherical crown pearls atop the coronet tips
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
                // Waist arc & skirt ring
                val beltColor = if (isWhite) NeoWhiteOutline.copy(alpha = 0.60f) else NeoBlackDetail.copy(alpha = 0.55f)
                scope.drawLine(
                    color = beltColor,
                    start = p(0.35f, 0.64f),
                    end = p(0.65f, 0.64f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                scope.drawLine(
                    color = beltColor,
                    start = p(0.28f, 0.78f),
                    end = p(0.72f, 0.78f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
            }

            PieceType.KING -> {
                // Iconic Neo center divider ridge & crown collar bands
                val crownLine = if (isWhite) NeoWhiteOutline.copy(alpha = 0.55f) else NeoBlackDetail.copy(alpha = 0.55f)
                scope.drawLine(
                    color = crownLine,
                    start = p(0.50f, 0.34f),
                    end = p(0.50f, 0.56f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                scope.drawLine(
                    color = crownLine,
                    start = p(0.35f, 0.58f),
                    end = p(0.65f, 0.58f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
                scope.drawLine(
                    color = crownLine,
                    start = p(0.28f, 0.78f),
                    end = p(0.72f, 0.78f),
                    strokeWidth = detailPx,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    /**
     * Constructs smooth Neo-style Staunton vector paths fitted cleanly into [bounds].
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
                    // Wide Neo rounded base -> gentle neck curve -> circular head
                    moveTo(x(0.25f), y(0.88f))
                    lineTo(x(0.75f), y(0.88f))
                    cubicTo(
                        x(0.77f), y(0.88f),
                        x(0.77f), y(0.80f),
                        x(0.68f), y(0.77f)
                    )
                    cubicTo(
                        x(0.64f), y(0.66f),
                        x(0.59f), y(0.56f),
                        x(0.57f), y(0.50f)
                    )
                    cubicTo(
                        x(0.64f), y(0.49f),
                        x(0.64f), y(0.44f),
                        x(0.57f), y(0.43f)
                    )
                    // Spherical head (right side up to crown)
                    cubicTo(
                        x(0.66f), y(0.36f),
                        x(0.64f), y(0.21f),
                        x(0.50f), y(0.21f)
                    )
                    // Crown down left side
                    cubicTo(
                        x(0.36f), y(0.21f),
                        x(0.34f), y(0.36f),
                        x(0.43f), y(0.43f)
                    )
                    cubicTo(
                        x(0.36f), y(0.44f),
                        x(0.36f), y(0.49f),
                        x(0.43f), y(0.50f)
                    )
                    cubicTo(
                        x(0.41f), y(0.56f),
                        x(0.36f), y(0.66f),
                        x(0.32f), y(0.77f)
                    )
                    cubicTo(
                        x(0.23f), y(0.80f),
                        x(0.23f), y(0.88f),
                        x(0.25f), y(0.88f)
                    )
                    close()
                }

                PieceType.ROOK -> {
                    moveTo(x(0.22f), y(0.88f))
                    lineTo(x(0.78f), y(0.88f))
                    lineTo(x(0.78f), y(0.81f))
                    lineTo(x(0.70f), y(0.77f))
                    // Tapered tower right
                    lineTo(x(0.67f), y(0.43f))
                    lineTo(x(0.74f), y(0.39f))
                    lineTo(x(0.74f), y(0.21f))
                    // Battlements
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
                    // Tapered tower left
                    lineTo(x(0.30f), y(0.77f))
                    lineTo(x(0.22f), y(0.81f))
                    close()
                }

                PieceType.KNIGHT -> {
                    // Flowing Chess.com Neo Knight silhouette
                    moveTo(x(0.22f), y(0.88f))
                    lineTo(x(0.78f), y(0.88f))
                    cubicTo(
                        x(0.78f), y(0.81f),
                        x(0.76f), y(0.77f),
                        x(0.74f), y(0.76f)
                    )
                    // Sweeping arched mane right up to ears
                    cubicTo(
                        x(0.79f), y(0.52f),
                        x(0.74f), y(0.28f),
                        x(0.59f), y(0.18f)
                    )
                    // Ear + forelock silhouette
                    lineTo(x(0.54f), y(0.23f))
                    cubicTo(
                        x(0.51f), y(0.19f),
                        x(0.48f), y(0.17f),
                        x(0.45f), y(0.17f)
                    )
                    lineTo(x(0.44f), y(0.23f))
                    // Brow to sculpted muzzle nose
                    cubicTo(
                        x(0.36f), y(0.26f),
                        x(0.26f), y(0.37f),
                        x(0.22f), y(0.48f)
                    )
                    cubicTo(
                        x(0.20f), y(0.53f),
                        x(0.25f), y(0.57f),
                        x(0.31f), y(0.55f)
                    )
                    // Jaw curve tucked to throat
                    cubicTo(
                        x(0.36f), y(0.54f),
                        x(0.41f), y(0.51f),
                        x(0.45f), y(0.48f)
                    )
                    // Proud muscular chest down to front base
                    cubicTo(
                        x(0.43f), y(0.58f),
                        x(0.33f), y(0.65f),
                        x(0.27f), y(0.77f)
                    )
                    cubicTo(
                        x(0.22f), y(0.80f),
                        x(0.22f), y(0.85f),
                        x(0.22f), y(0.88f)
                    )
                    close()
                }

                PieceType.BISHOP -> {
                    moveTo(x(0.22f), y(0.88f))
                    lineTo(x(0.78f), y(0.88f))
                    cubicTo(
                        x(0.79f), y(0.82f),
                        x(0.75f), y(0.78f),
                        x(0.67f), y(0.76f)
                    )
                    // Flared Neo body curve up to collar
                    cubicTo(
                        x(0.61f), y(0.67f),
                        x(0.59f), y(0.58f),
                        x(0.58f), y(0.53f)
                    )
                    // Collar
                    cubicTo(
                        x(0.65f), y(0.51f),
                        x(0.65f), y(0.46f),
                        x(0.59f), y(0.45f)
                    )
                    // Smooth rounded mitre dome
                    cubicTo(
                        x(0.67f), y(0.38f),
                        x(0.64f), y(0.23f),
                        x(0.50f), y(0.18f)
                    )
                    cubicTo(
                        x(0.36f), y(0.23f),
                        x(0.33f), y(0.38f),
                        x(0.41f), y(0.45f)
                    )
                    cubicTo(
                        x(0.35f), y(0.46f),
                        x(0.35f), y(0.51f),
                        x(0.42f), y(0.53f)
                    )
                    cubicTo(
                        x(0.41f), y(0.58f),
                        x(0.39f), y(0.67f),
                        x(0.33f), y(0.76f)
                    )
                    cubicTo(
                        x(0.25f), y(0.78f),
                        x(0.21f), y(0.82f),
                        x(0.22f), y(0.88f)
                    )
                    close()
                }

                PieceType.QUEEN -> {
                    moveTo(x(0.22f), y(0.88f))
                    lineTo(x(0.78f), y(0.88f))
                    cubicTo(
                        x(0.79f), y(0.81f),
                        x(0.75f), y(0.78f),
                        x(0.71f), y(0.76f)
                    )
                    // Coronet flared rays
                    lineTo(x(0.81f), y(0.32f))
                    quadraticTo(x(0.70f), y(0.45f), x(0.66f), y(0.24f))
                    quadraticTo(x(0.56f), y(0.42f), x(0.50f), y(0.20f))
                    quadraticTo(x(0.44f), y(0.42f), x(0.34f), y(0.24f))
                    quadraticTo(x(0.30f), y(0.45f), x(0.19f), y(0.32f))
                    lineTo(x(0.29f), y(0.76f))
                    cubicTo(
                        x(0.25f), y(0.78f),
                        x(0.21f), y(0.81f),
                        x(0.22f), y(0.88f)
                    )
                    close()
                }

                PieceType.KING -> {
                    moveTo(x(0.22f), y(0.88f))
                    lineTo(x(0.78f), y(0.88f))
                    cubicTo(
                        x(0.79f), y(0.81f),
                        x(0.75f), y(0.78f),
                        x(0.70f), y(0.76f)
                    )
                    // Waist & broad Neo shoulders
                    cubicTo(
                        x(0.68f), y(0.68f),
                        x(0.64f), y(0.62f),
                        x(0.63f), y(0.58f)
                    )
                    // Iconic twin rounded crown lobes
                    cubicTo(
                        x(0.78f), y(0.48f),
                        x(0.79f), y(0.30f),
                        x(0.63f), y(0.28f)
                    )
                    cubicTo(
                        x(0.56f), y(0.27f),
                        x(0.53f), y(0.31f),
                        x(0.525f), y(0.33f)
                    )
                    // Upright smooth cross on crown
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
                    // Left crown lobe
                    cubicTo(
                        x(0.47f), y(0.31f),
                        x(0.44f), y(0.27f),
                        x(0.37f), y(0.28f)
                    )
                    cubicTo(
                        x(0.21f), y(0.30f),
                        x(0.22f), y(0.48f),
                        x(0.37f), y(0.58f)
                    )
                    cubicTo(
                        x(0.36f), y(0.62f),
                        x(0.32f), y(0.68f),
                        x(0.30f), y(0.76f)
                    )
                    cubicTo(
                        x(0.25f), y(0.78f),
                        x(0.21f), y(0.81f),
                        x(0.22f), y(0.88f)
                    )
                    close()
                }
            }
        }
    }
}
