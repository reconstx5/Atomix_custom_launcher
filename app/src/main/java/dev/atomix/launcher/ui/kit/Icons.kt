package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.atomix.launcher.ui.theme.px

enum class LineIcon { Home, Apps, Library, Live, Inputs, Search, Atom, Settings, Edit, Star, StarFilled, Plus, Play, Pause, Back30, Forward30, List }

/** Thin line icons drawn on a 24-unit grid, so no icon font or bitmap is needed. */
@Composable
fun Icon(kind: LineIcon, color: Color, sizePx: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.size(px(sizePx))) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        when (kind) {
            LineIcon.Home -> drawPath(Path().apply {
                moveTo(3 * u, 11 * u); lineTo(12 * u, 3.5f * u); lineTo(21 * u, 11 * u)
                moveTo(5.5f * u, 9.5f * u); lineTo(5.5f * u, 20 * u); lineTo(18.5f * u, 20 * u); lineTo(18.5f * u, 9.5f * u)
            }, color, style = stroke)
            LineIcon.Apps -> for (x in listOf(4f, 13.5f)) for (y in listOf(4f, 13.5f))
                drawRoundRect(color, p(x, y), Size(6.5f * u, 6.5f * u), CornerRadius(1.6f * u), style = stroke)
            LineIcon.Library -> {
                drawRoundRect(color, p(3.5f, 7f), Size(17f * u, 13f * u), CornerRadius(2f * u), style = stroke)
                drawLine(color, p(6f, 4f), p(18f, 4f), stroke.width, StrokeCap.Round)
                drawPath(Path().apply { moveTo(10.5f * u, 10.5f * u); lineTo(15f * u, 13.5f * u); lineTo(10.5f * u, 16.5f * u); close() }, color, style = stroke)
            }
            LineIcon.Live -> {
                drawCircle(color, 2f * u, p(12f, 12f))
                drawArc(color, -45f, 90f, false, p(6.5f, 6.5f), Size(11f * u, 11f * u), style = stroke)
                drawArc(color, 135f, 90f, false, p(6.5f, 6.5f), Size(11f * u, 11f * u), style = stroke)
                drawArc(color, -45f, 90f, false, p(2.5f, 2.5f), Size(19f * u, 19f * u), style = stroke)
                drawArc(color, 135f, 90f, false, p(2.5f, 2.5f), Size(19f * u, 19f * u), style = stroke)
            }
            LineIcon.Inputs -> {
                drawRoundRect(color, p(3f, 4.5f), Size(18f * u, 12.5f * u), CornerRadius(2f * u), style = stroke)
                drawLine(color, p(8f, 20.5f), p(16f, 20.5f), stroke.width, StrokeCap.Round)
                drawPath(Path().apply {
                    moveTo(8 * u, 10.75f * u); lineTo(15.5f * u, 10.75f * u)
                    moveTo(12.5f * u, 7.75f * u); lineTo(15.5f * u, 10.75f * u); lineTo(12.5f * u, 13.75f * u)
                }, color, style = stroke)
            }
            LineIcon.Search -> {
                drawCircle(color, 6.5f * u, p(10.5f, 10.5f), style = stroke)
                drawLine(color, p(15.5f, 15.5f), p(20.5f, 20.5f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Settings -> {
                drawCircle(color, 3f * u, p(12f, 12f), style = stroke)
                for (i in 0 until 8) rotate(i * 45f, p(12f, 12f)) {
                    drawLine(color, p(12f, 3f), p(12f, 5.5f), 2.6f * u, StrokeCap.Round)
                }
                drawCircle(color, 6.6f * u, p(12f, 12f), style = stroke)
            }
            LineIcon.Edit -> drawPath(Path().apply {
                moveTo(4 * u, 20 * u); lineTo(4.8f * u, 16 * u); lineTo(15.5f * u, 5.3f * u); lineTo(18.7f * u, 8.5f * u)
                lineTo(8 * u, 19.2f * u); close(); moveTo(13.5f * u, 7.3f * u); lineTo(16.7f * u, 10.5f * u)
            }, color, style = stroke)
            LineIcon.Star, LineIcon.StarFilled -> {
                val star = Path().apply {
                    for (i in 0 until 10) {
                        val r = if (i % 2 == 0) 9.5f else 4.2f
                        val a = Math.toRadians((i * 36 - 90).toDouble())
                        val x = 12f + r * Math.cos(a).toFloat(); val y = 12.6f + r * Math.sin(a).toFloat()
                        if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u)
                    }
                    close()
                }
                drawPath(star, color, style = if (kind == LineIcon.StarFilled) Fill else stroke)
            }
            LineIcon.Plus -> {
                drawLine(color, p(12f, 5f), p(12f, 19f), stroke.width, StrokeCap.Round)
                drawLine(color, p(5f, 12f), p(19f, 12f), stroke.width, StrokeCap.Round)
            }
            LineIcon.Play -> drawPath(Path().apply { moveTo(7 * u, 4.5f * u); lineTo(19.5f * u, 12 * u); lineTo(7 * u, 19.5f * u); close() }, color)
            LineIcon.Pause -> {
                drawRoundRect(color, p(6f, 5f), Size(4f * u, 14f * u), CornerRadius(1f * u))
                drawRoundRect(color, p(14f, 5f), Size(4f * u, 14f * u), CornerRadius(1f * u))
            }
            LineIcon.Back30, LineIcon.Forward30 -> {
                val back = kind == LineIcon.Back30
                drawArc(color, if (back) 200f else -40f, 260f, false, p(4f, 4f), Size(16f * u, 16f * u), style = stroke)
                val tip = if (back) p(4.6f, 8.2f) else p(19.4f, 8.2f)
                drawPath(Path().apply {
                    moveTo(tip.x, tip.y - 3.2f * u); lineTo(tip.x, tip.y); lineTo(tip.x + (if (back) 3.2f else -3.2f) * u, tip.y)
                }, color, style = stroke)
            }
            LineIcon.List -> for (y in listOf(6f, 12f, 18f)) {
                drawCircle(color, 1.2f * u, p(5f, y))
                drawLine(color, p(9f, y), p(20f, y), stroke.width, StrokeCap.Round)
            }
            LineIcon.Atom -> {
                for (angle in listOf(0f, 60f, 120f)) rotate(angle, p(12f, 12f)) {
                    drawOval(color, p(2f, 8.2f), Size(20f * u, 7.6f * u), style = Stroke(1.6f * u))
                }
                drawCircle(color, 2.1f * u, p(12f, 12f))
            }
        }
    }
}
