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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import dev.atomix.launcher.ui.theme.px

enum class LineIcon {
    Home, Apps, Library, Live, Inputs, Search, Atom, Settings, Edit, Star, StarFilled, Plus, Play, Pause, Back30, Forward30, List,
    House, ChevronRight, ChevronDown, Tick, Warn, Info, Grid, Bars, Arc, Plug, Tuner, Person, Drop, Screen, Minus,
}

/** One stroked or filled SVG path on the 24-unit grid, as drawn on the design boards. */
private class Glyph(val d: String, val width: Float = 1.8f, val filled: Boolean = false)

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

/** The icons whose shapes are the boards' own SVG paths. */
private val GLYPHS: Map<LineIcon, List<Glyph>> = mapOf(
    LineIcon.House to listOf(Glyph("M4 11l8-6.5L20 11v8.5a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1V11z")),
    LineIcon.ChevronRight to listOf(Glyph("M9 5l7 7-7 7", 2f)),
    LineIcon.ChevronDown to listOf(Glyph("M6 9l6 6 6-6", 2f)),
    LineIcon.Tick to listOf(Glyph("M5 12.5l4.5 4.5L19 7.5", 2.4f)),
    LineIcon.Warn to listOf(Glyph("M12 7v6M12 16.8h.01" + circle(12f, 12f, 9f), 2.2f)),
    LineIcon.Info to listOf(Glyph(circle(12f, 12f, 8.5f) + "M12 11v5.5M12 7.8h.01")),
    LineIcon.Grid to listOf(Glyph(listOf(5f, 12f, 19f).flatMap { y -> listOf(5f, 12f, 19f).map { x -> circle(x, y, 2f) } }.joinToString(""), filled = true)),
    LineIcon.Bars to listOf(Glyph(rrect(4f, 10f, 3.4f, 9f, 1.2f) + rrect(10.3f, 5f, 3.4f, 14f, 1.2f) + rrect(16.6f, 12f, 3.4f, 7f, 1.2f), filled = true)),
    LineIcon.Arc to listOf(Glyph("M12 3a9 9 0 0 1 9 9", 2.6f)),
    LineIcon.Plug to listOf(Glyph("M4 9h16v4.5l-2.5 3h-11L4 13.5V9zM8 12.5h8", 1.7f)),
    LineIcon.Tuner to listOf(Glyph(rrect(3f, 7f, 18f, 12f, 2f) + "M8 3l4 4 4-4")),
    LineIcon.Person to listOf(Glyph(circle(12f, 8.5f, 3.5f) + "M5 19.5c1.2-3.2 3.8-4.8 7-4.8s5.8 1.6 7 4.8")),
    // A TV on its stand with a small cog on the screen: the TV's own settings.
    LineIcon.Screen to listOf(Glyph(rrect(3f, 5f, 18f, 12f, 2f) + "M8.5 20.5h7" + circle(12f, 11f, 1.7f) + "M12 7.6v1.1M12 13.3v1.1M8.6 11h1.1M14.3 11h1.1")),
    LineIcon.Drop to listOf(Glyph("M12 3.5c3.2 3.9 5.5 6.8 5.5 9.7a5.5 5.5 0 0 1-11 0c0-2.9 2.3-5.8 5.5-9.7z")),
    LineIcon.Inputs to listOf(Glyph(rrect(3f, 5f, 18f, 12f, 2f) + "M8.5 20.5h7M10.5 9.2l3.6 1.8-3.6 1.8z")),
    LineIcon.Settings to listOf(Glyph(circle(12f, 12f, 3.2f) + "M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7")),
    LineIcon.Edit to listOf(Glyph("M4 20h4L19 9l-4-4L4 16v4z")),
    LineIcon.Pause to listOf(Glyph(rrect(6f, 5f, 4.2f, 14f, 1.2f) + rrect(13.8f, 5f, 4.2f, 14f, 1.2f), filled = true)),
    LineIcon.Back30 to listOf(Glyph("M4.5 12a7.5 7.5 0 1 0 2.4-5.5M4 4.5v4.2h4.2", 2f)),
)

private val parsed = HashMap<LineIcon, List<Pair<Glyph, Path>>>()

private fun pathsFor(kind: LineIcon): List<Pair<Glyph, Path>>? {
    val glyphs = GLYPHS[kind] ?: return null
    return parsed.getOrPut(kind) { glyphs.map { it to PathParser().parsePathString(it.d).toPath() } }
}

/** Thin line icons drawn on a 24-unit grid, so no icon font or bitmap is needed. */
@Composable
fun Icon(kind: LineIcon, color: Color, sizePx: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.size(px(sizePx))) {
        val u = size.minDimension / 24f
        val paths = pathsFor(kind)
        if (paths != null) {
            scale(u, u, pivot = Offset.Zero) {
                for ((glyph, path) in paths) {
                    drawPath(path, color, style = if (glyph.filled) Fill else Stroke(glyph.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            return@Canvas
        }
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
            LineIcon.Search -> {
                drawCircle(color, 6.5f * u, p(10.5f, 10.5f), style = stroke)
                drawLine(color, p(15.5f, 15.5f), p(20.5f, 20.5f), stroke.width, StrokeCap.Round)
            }
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
            LineIcon.Minus -> drawLine(color, p(5f, 12f), p(19f, 12f), stroke.width, StrokeCap.Round)
            LineIcon.Play -> drawPath(Path().apply { moveTo(7 * u, 4.5f * u); lineTo(19.5f * u, 12 * u); lineTo(7 * u, 19.5f * u); close() }, color)
            LineIcon.Forward30 -> {
                drawArc(color, -40f, 260f, false, p(4f, 4f), Size(16f * u, 16f * u), style = stroke)
                val tip = p(19.4f, 8.2f)
                drawPath(Path().apply { moveTo(tip.x, tip.y - 3.2f * u); lineTo(tip.x, tip.y); lineTo(tip.x - 3.2f * u, tip.y) }, color, style = stroke)
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
            else -> Unit
        }
    }
}
