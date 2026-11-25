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
 * Chess piece vector rendering directly on Canvas.
 *
 * Rules:
 * - 6 vector silhouettes: Pawn, Knight, Bishop, Rook, Queen, King.
 * - No font glyphs, no images, no assets, no emojis.
 * - White: FILLED textPrimary.
 * - Black: STROKED textPrimary at 1.6.dp with NO fill.
 *   (Print-diagram monochrome style, high contrast, instant legibility).
 */
object ChessPieces {

    val StrokeWidth: Dp = 1.6.dp

    /**
     * Draws the given [piece] inside [bounds].
     */
    fun drawPiece(
        scope: DrawScope,
        piece: Piece,
        bounds: Rect,
        color: Color,
        strokeWidth: Dp = StrokeWidth
    ) {
        val path = createPiecePath(piece.type, bounds)
        when (piece.color) {
            PieceColor.WHITE -> {
                scope.drawPath(path = path, color = color, style = Fill)
            }
            PieceColor.BLACK -> {
                val strokePx = with(scope) { strokeWidth.toPx() }
                scope.drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }

    /**
     * Constructs a normalized vector [Path] for the given [type] fitted to [bounds].
     */
    fun createPiecePath(type: PieceType, bounds: Rect): Path {
        val l = bounds.left
        val t = bounds.top
        val w = bounds.width
        val h = bounds.height

        fun p(nx: Float, ny: Float): Offset = Offset(l + nx * w, t + ny * h)

        return Path().apply {
            when (type) {
                PieceType.PAWN -> {
                    // Base
                    moveTo(p(0.24f, 0.88f).x, p(0.24f, 0.88f).y)
                    lineTo(p(0.76f, 0.88f).x, p(0.76f, 0.88f).y)
                    lineTo(p(0.74f, 0.82f).x, p(0.74f, 0.82f).y)
                    lineTo(p(0.66f, 0.78f).x, p(0.66f, 0.78f).y)
                    // Right body to collar
                    cubicTo(
                        p(0.64f, 0.68f).x, p(0.64f, 0.68f).y,
                        p(0.58f, 0.58f).x, p(0.58f, 0.58f).y,
                        p(0.58f, 0.52f).x, p(0.58f, 0.52f).y
                    )
                    // Collar right
                    lineTo(p(0.62f, 0.50f).x, p(0.62f, 0.50f).y)
                    lineTo(p(0.62f, 0.46f).x, p(0.62f, 0.46f).y)
                    lineTo(p(0.57f, 0.44f).x, p(0.57f, 0.44f).y)
                    // Spherical head
                    cubicTo(
                        p(0.65f, 0.38f).x, p(0.65f, 0.38f).y,
                        p(0.64f, 0.20f).x, p(0.64f, 0.20f).y,
                        p(0.50f, 0.20f).x, p(0.50f, 0.20f).y
                    )
                    cubicTo(
                        p(0.36f, 0.20f).x, p(0.36f, 0.20f).y,
                        p(0.35f, 0.38f).x, p(0.35f, 0.38f).y,
                        p(0.43f, 0.44f).x, p(0.43f, 0.44f).y
                    )
                    // Collar left
                    lineTo(p(0.38f, 0.46f).x, p(0.38f, 0.46f).y)
                    lineTo(p(0.38f, 0.50f).x, p(0.38f, 0.50f).y)
                    lineTo(p(0.42f, 0.52f).x, p(0.42f, 0.52f).y)
                    // Left body down to base
                    cubicTo(
                        p(0.42f, 0.58f).x, p(0.42f, 0.58f).y,
                        p(0.36f, 0.68f).x, p(0.36f, 0.68f).y,
                        p(0.34f, 0.78f).x, p(0.34f, 0.78f).y
                    )
                    lineTo(p(0.26f, 0.82f).x, p(0.26f, 0.82f).y)
                    close()
                }

                PieceType.ROOK -> {
                    // Base plinth
                    moveTo(p(0.22f, 0.88f).x, p(0.22f, 0.88f).y)
                    lineTo(p(0.78f, 0.88f).x, p(0.78f, 0.88f).y)
                    lineTo(p(0.76f, 0.82f).x, p(0.76f, 0.82f).y)
                    lineTo(p(0.68f, 0.78f).x, p(0.68f, 0.78f).y)
                    // Right tower wall
                    lineTo(p(0.66f, 0.42f).x, p(0.66f, 0.42f).y)
                    // Cornice right shelf
                    lineTo(p(0.74f, 0.40f).x, p(0.74f, 0.40f).y)
                    lineTo(p(0.74f, 0.36f).x, p(0.74f, 0.36f).y)
                    // Battlements (Right, notch, center, notch, left)
                    lineTo(p(0.74f, 0.22f).x, p(0.74f, 0.22f).y)
                    lineTo(p(0.64f, 0.22f).x, p(0.64f, 0.22f).y)
                    lineTo(p(0.64f, 0.30f).x, p(0.64f, 0.30f).y)
                    lineTo(p(0.56f, 0.30f).x, p(0.56f, 0.30f).y)
                    lineTo(p(0.56f, 0.22f).x, p(0.56f, 0.22f).y)
                    lineTo(p(0.44f, 0.22f).x, p(0.44f, 0.22f).y)
                    lineTo(p(0.44f, 0.30f).x, p(0.44f, 0.30f).y)
                    lineTo(p(0.36f, 0.30f).x, p(0.36f, 0.30f).y)
                    lineTo(p(0.36f, 0.22f).x, p(0.36f, 0.22f).y)
                    lineTo(p(0.26f, 0.22f).x, p(0.26f, 0.22f).y)
                    // Left cornice shelf
                    lineTo(p(0.26f, 0.36f).x, p(0.26f, 0.36f).y)
                    lineTo(p(0.26f, 0.40f).x, p(0.26f, 0.40f).y)
                    lineTo(p(0.34f, 0.42f).x, p(0.34f, 0.42f).y)
                    // Left tower wall down to base
                    lineTo(p(0.32f, 0.78f).x, p(0.32f, 0.78f).y)
                    lineTo(p(0.24f, 0.82f).x, p(0.24f, 0.82f).y)
                    close()
                }

                PieceType.KNIGHT -> {
                    // Base
                    moveTo(p(0.24f, 0.88f).x, p(0.24f, 0.88f).y)
                    lineTo(p(0.76f, 0.88f).x, p(0.76f, 0.88f).y)
                    lineTo(p(0.74f, 0.80f).x, p(0.74f, 0.80f).y)
                    // Arched back / mane up to ears
                    cubicTo(
                        p(0.75f, 0.60f).x, p(0.75f, 0.60f).y,
                        p(0.70f, 0.36f).x, p(0.70f, 0.36f).y,
                        p(0.58f, 0.20f).x, p(0.58f, 0.20f).y
                    )
                    // Notch between ears
                    lineTo(p(0.53f, 0.25f).x, p(0.53f, 0.25f).y)
                    lineTo(p(0.48f, 0.22f).x, p(0.48f, 0.22f).y)
                    // Forehead down to snout
                    cubicTo(
                        p(0.42f, 0.28f).x, p(0.42f, 0.28f).y,
                        p(0.34f, 0.34f).x, p(0.34f, 0.34f).y,
                        p(0.25f, 0.44f).x, p(0.25f, 0.44f).y
                    )
                    // Nose and chin
                    lineTo(p(0.25f, 0.49f).x, p(0.25f, 0.49f).y)
                    lineTo(p(0.32f, 0.52f).x, p(0.32f, 0.52f).y)
                    // Throat / jaw indent
                    cubicTo(
                        p(0.35f, 0.52f).x, p(0.35f, 0.52f).y,
                        p(0.38f, 0.54f).x, p(0.38f, 0.54f).y,
                        p(0.40f, 0.55f).x, p(0.40f, 0.55f).y
                    )
                    // Proud chest curve down to front base
                    cubicTo(
                        p(0.34f, 0.64f).x, p(0.34f, 0.64f).y,
                        p(0.30f, 0.72f).x, p(0.30f, 0.72f).y,
                        p(0.28f, 0.80f).x, p(0.28f, 0.80f).y
                    )
                    close()
                }

                PieceType.BISHOP -> {
                    // Base
                    moveTo(p(0.24f, 0.88f).x, p(0.24f, 0.88f).y)
                    lineTo(p(0.76f, 0.88f).x, p(0.76f, 0.88f).y)
                    lineTo(p(0.74f, 0.82f).x, p(0.74f, 0.82f).y)
                    lineTo(p(0.64f, 0.78f).x, p(0.64f, 0.78f).y)
                    // Body right
                    lineTo(p(0.54f, 0.56f).x, p(0.54f, 0.56f).y)
                    // Collar right
                    lineTo(p(0.60f, 0.52f).x, p(0.60f, 0.52f).y)
                    lineTo(p(0.56f, 0.48f).x, p(0.56f, 0.48f).y)
                    // Mitre head right curving to tip
                    cubicTo(
                        p(0.64f, 0.42f).x, p(0.64f, 0.42f).y,
                        p(0.62f, 0.26f).x, p(0.62f, 0.26f).y,
                        p(0.50f, 0.16f).x, p(0.50f, 0.16f).y
                    )
                    // Mitre head left with distinctive slit
                    cubicTo(
                        p(0.43f, 0.22f).x, p(0.43f, 0.22f).y,
                        p(0.40f, 0.28f).x, p(0.40f, 0.28f).y,
                        p(0.42f, 0.30f).x, p(0.42f, 0.30f).y
                    )
                    // Slit notch cut
                    lineTo(p(0.48f, 0.34f).x, p(0.48f, 0.34f).y)
                    lineTo(p(0.39f, 0.35f).x, p(0.39f, 0.35f).y)
                    cubicTo(
                        p(0.36f, 0.42f).x, p(0.36f, 0.42f).y,
                        p(0.42f, 0.46f).x, p(0.42f, 0.46f).y,
                        p(0.44f, 0.48f).x, p(0.44f, 0.48f).y
                    )
                    // Collar left
                    lineTo(p(0.40f, 0.52f).x, p(0.40f, 0.52f).y)
                    lineTo(p(0.46f, 0.56f).x, p(0.46f, 0.56f).y)
                    // Body left
                    lineTo(p(0.36f, 0.78f).x, p(0.36f, 0.78f).y)
                    lineTo(p(0.26f, 0.82f).x, p(0.26f, 0.82f).y)
                    close()
                }

                PieceType.QUEEN -> {
                    // Base
                    moveTo(p(0.22f, 0.88f).x, p(0.22f, 0.88f).y)
                    lineTo(p(0.78f, 0.88f).x, p(0.78f, 0.88f).y)
                    lineTo(p(0.76f, 0.80f).x, p(0.76f, 0.80f).y)
                    // Skirt right in to waist
                    cubicTo(
                        p(0.70f, 0.72f).x, p(0.70f, 0.72f).y,
                        p(0.62f, 0.65f).x, p(0.62f, 0.65f).y,
                        p(0.58f, 0.58f).x, p(0.58f, 0.58f).y
                    )
                    // Coronet / crown (5 peaks)
                    lineTo(p(0.76f, 0.30f).x, p(0.76f, 0.30f).y) // right peak
                    lineTo(p(0.68f, 0.38f).x, p(0.68f, 0.38f).y) // notch 1
                    lineTo(p(0.63f, 0.24f).x, p(0.63f, 0.24f).y) // mid-right peak
                    lineTo(p(0.56f, 0.36f).x, p(0.56f, 0.36f).y) // notch 2
                    lineTo(p(0.50f, 0.20f).x, p(0.50f, 0.20f).y) // center peak
                    lineTo(p(0.44f, 0.36f).x, p(0.44f, 0.36f).y) // notch 3
                    lineTo(p(0.37f, 0.24f).x, p(0.37f, 0.24f).y) // mid-left peak
                    lineTo(p(0.32f, 0.38f).x, p(0.32f, 0.38f).y) // notch 4
                    lineTo(p(0.24f, 0.30f).x, p(0.24f, 0.30f).y) // left peak
                    // Waist left
                    lineTo(p(0.42f, 0.58f).x, p(0.42f, 0.58f).y)
                    // Skirt left
                    cubicTo(
                        p(0.38f, 0.65f).x, p(0.38f, 0.65f).y,
                        p(0.30f, 0.72f).x, p(0.30f, 0.72f).y,
                        p(0.24f, 0.80f).x, p(0.24f, 0.80f).y
                    )
                    close()
                }

                PieceType.KING -> {
                    // Base
                    moveTo(p(0.22f, 0.88f).x, p(0.22f, 0.88f).y)
                    lineTo(p(0.78f, 0.88f).x, p(0.78f, 0.88f).y)
                    lineTo(p(0.76f, 0.80f).x, p(0.76f, 0.80f).y)
                    // Right body
                    cubicTo(
                        p(0.70f, 0.72f).x, p(0.70f, 0.72f).y,
                        p(0.62f, 0.65f).x, p(0.62f, 0.65f).y,
                        p(0.58f, 0.56f).x, p(0.58f, 0.56f).y
                    )
                    // Crown right band
                    lineTo(p(0.66f, 0.48f).x, p(0.66f, 0.48f).y)
                    // Crown right dome up to cross
                    cubicTo(
                        p(0.68f, 0.36f).x, p(0.68f, 0.36f).y,
                        p(0.58f, 0.28f).x, p(0.58f, 0.28f).y,
                        p(0.53f, 0.28f).x, p(0.53f, 0.28f).y
                    )
                    // Cross on top (right side)
                    lineTo(p(0.53f, 0.24f).x, p(0.53f, 0.24f).y)
                    lineTo(p(0.60f, 0.24f).x, p(0.60f, 0.24f).y)
                    lineTo(p(0.60f, 0.18f).x, p(0.60f, 0.18f).y)
                    lineTo(p(0.53f, 0.18f).x, p(0.53f, 0.18f).y)
                    lineTo(p(0.53f, 0.13f).x, p(0.53f, 0.13f).y)
                    // Cross top
                    lineTo(p(0.47f, 0.13f).x, p(0.47f, 0.13f).y)
                    // Cross left side
                    lineTo(p(0.47f, 0.18f).x, p(0.47f, 0.18f).y)
                    lineTo(p(0.40f, 0.18f).x, p(0.40f, 0.18f).y)
                    lineTo(p(0.40f, 0.24f).x, p(0.40f, 0.24f).y)
                    lineTo(p(0.47f, 0.24f).x, p(0.47f, 0.24f).y)
                    lineTo(p(0.47f, 0.28f).x, p(0.47f, 0.28f).y)
                    // Crown left dome
                    cubicTo(
                        p(0.42f, 0.28f).x, p(0.42f, 0.28f).y,
                        p(0.32f, 0.36f).x, p(0.32f, 0.36f).y,
                        p(0.34f, 0.48f).x, p(0.34f, 0.48f).y
                    )
                    // Crown left band
                    lineTo(p(0.42f, 0.56f).x, p(0.42f, 0.56f).y)
                    // Body left
                    cubicTo(
                        p(0.38f, 0.65f).x, p(0.38f, 0.65f).y,
                        p(0.30f, 0.72f).x, p(0.30f, 0.72f).y,
                        p(0.24f, 0.80f).x, p(0.24f, 0.80f).y
                    )
                    close()
                }
            }
        }
    }
}
