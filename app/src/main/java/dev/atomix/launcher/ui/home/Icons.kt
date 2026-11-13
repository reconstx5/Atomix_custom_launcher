package dev.atomix.launcher.ui.home

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import dev.atomix.launcher.ui.theme.px

enum class LineIcon { Home, Apps, Library, Live, Inputs, Search, Atom }

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
            LineIcon.Atom -> {
                for (angle in listOf(0f, 60f, 120f)) rotate(angle, p(12f, 12f)) {
                    drawOval(color, p(2f, 8.2f), Size(20f * u, 7.6f * u), style = Stroke(1.6f * u))
                }
                drawCircle(color, 2.1f * u, p(12f, 12f))
            }
        }
    }
}
