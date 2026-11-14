package dev.atomix.launcher.ui.splash

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import dev.atomix.launcher.R
import dev.atomix.launcher.ui.theme.AtomixColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** The splash is laid out on the TV's own 1920×1080 frame and scaled to whatever the screen is. */
private const val W = 1920f
private const val H = 1080f
private const val CX = 960f
private const val CY = 540f

private val Accent = Color(0xFFFF6B3D)
private val Cyan = Color(0xFF7CC7E8)
private val Warm = Color(0xFFFFA676)

/** Fourteen sparks thrown to the right of the impact, from a fixed seed so every play looks the same. */
private class Spark(val angle: Float, val speed: Float, val color: Color)

private val SPARKS: List<Spark> = run {
    var seed = 5
    fun rnd(): Float {
        seed = seed + 0x6d2b79f5
        var t = seed xor (seed ushr 15)
        t *= 1 or seed
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        return ((t xor (t ushr 14)).toUInt().toFloat()) / 4294967296f
    }
    List(14) { Spark(-0.7f + 1.4f * rnd(), 520f + 1000f * rnd(), if (rnd() < 0.5f) Cyan else Warm) }
}

/**
 * The cold-start splash (concept D, "Play-out"): the orbits sketch in, the play button pops, two particles race
 * round the orbits and collide at its tip, and the name streams out of the tip while the mark settles beside it.
 * With [reduced] it is a short fade of the finished lockup instead. [onDone] fires once, when it has run its course.
 */
@Composable
fun SplashScreen(reduced: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = runCatching { ResourcesCompat.getFont(context, R.font.sora) }.getOrNull()
                ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            // The name is drawn bold, as the wordmark it lands on is.
            fontVariationSettings = "'wght' 700"
        }
    }
    val total = if (reduced) SplashTimeline.REDUCED_DURATION else SplashTimeline.DURATION
    var t by remember { mutableFloatStateOf(0f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t = (now - start) / 1_000_000_000f
            if (t >= total) break
        }
        done()
    }
    Canvas(Modifier.fillMaxSize()) {
        val s = minOf(size.width / W, size.height / H)
        withTransform({
            translate((size.width - W * s) / 2f, (size.height - H * s) / 2f)
            scale(s, s, Offset.Zero)
        }) {
            if (reduced) drawReduced(t, paint) else drawPlayOut(t, paint)
        }
    }
}

// ---------- the mark ----------

/** Draws the orbit loops, each sketched in as far as its own progress. */
private fun DrawScope.orbits(p0: Float, p1: Float, alpha: Float) {
    if (alpha <= 0f) return
    for (k in 0..1) {
        val p = if (k == 0) p0 else p1
        if (p <= 0f) continue
        val path = Path()
        for (arc in Mark.ARCS[k]) {
            val a = arc[0]
            val end = a + (arc[1] - a) * p
            for (i in 0..24) {
                val (x, y) = Mark.orb(k, a + (end - a) * i / 24f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        drawPath(path, Accent.copy(alpha = alpha), style = Stroke(width = 1.7f))
    }
}

/** The play button at the heart of the mark; [s] scales it about its own centre. */
private fun DrawScope.nucleus(s: Float, alpha: Float) {
    if (alpha <= 0f || s <= 0f) return
    withTransform({ scale(s, s, Offset(16.72f, 16f)) }) {
        val path = Path().apply {
            moveTo(13.6f, 11.5f)
            lineTo(Mark.TIP_X, 16f)
            lineTo(13.6f, 20.5f)
            close()
        }
        drawPath(path, Accent.copy(alpha = alpha))
        drawPath(path, Accent.copy(alpha = alpha), style = Stroke(width = 1.6f))
    }
}

/** The opening the splash shares with the web app: orbits sketch in, then the play button pops. */
private fun DrawScope.introMark(t: Float, boost: Float, alpha: Float) {
    orbits(Ease.out(seg(t, 0f, 0.6f)), Ease.out(seg(t, 0.15f, 0.75f)), alpha)
    val np = seg(t, 0.45f, 0.8f)
    nucleus((0.3f + 0.7f * Ease.back(np)) * boost, clamp01(np / 0.6f) * alpha)
}

/** Runs [body] in the mark's own 32-unit space, placed on the 1920×1080 frame. */
private inline fun DrawScope.inMark(dx: Float, scale: Float, body: DrawScope.() -> Unit) {
    withTransform({
        translate(CX + dx, CY)
        scale(Mark.UNIT * scale, Mark.UNIT * scale, Offset.Zero)
        translate(-Mark.CENTRE, -Mark.CENTRE)
    }) { body() }
}

private fun DrawScope.glowDot(x: Float, y: Float, color: Color, alpha: Float, r: Float, halo: Boolean = true) {
    if (alpha <= 0f || r <= 0f) return
    if (halo) {
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.5f * alpha), color.copy(alpha = 0f)), Offset(x, y), r * 3.4f),
            r * 3.4f, Offset(x, y), blendMode = BlendMode.Plus,
        )
    }
    drawCircle(color.copy(alpha = alpha), r, Offset(x, y), blendMode = BlendMode.Plus)
}

/** The cyan electron and its warm twin, with a short trail each. Drawn in logo units. */
private fun DrawScope.particles(t: Float) {
    if (t >= SplashTimeline.RACE_TO) return
    val pop = seg(t, SplashTimeline.PARTICLES_POP_FROM, SplashTimeline.PARTICLES_POP_TO)
    if (pop <= 0f) return
    val t0 = SplashTimeline.RACE_FROM
    val t1 = SplashTimeline.RACE_TO
    for (k in 0..1) {
        val color = if (k == 1) Cyan else Warm
        val r = 2.2f * if (t < t0) 0.3f + 0.7f * Ease.back(pop) else lerp(1f, 0.8f, seg(t, t0, t1))
        for (i in 9 downTo 1) {
            val tt = t - i * 0.016f
            if (tt <= t0) continue
            val (x, y) = Mark.racePos(k, tt, t0, t1)
            glowDot(x, y, color, (1f - i / 10f) * 0.4f, r * (1f - i / 12f), halo = false)
        }
        val (x, y) = Mark.racePos(k, maxOf(t, t0), t0, t1)
        glowDot(x, y, color, clamp01(pop / 0.5f), r)
    }
}

/** The flash, ring and sparks where the two meet. Drawn on the 1920×1080 frame. */
private fun DrawScope.impact(t: Float, at: Offset) {
    val tau = t - SplashTimeline.IMPACT_AT
    if (tau < 0f) return
    val p = seg(t, SplashTimeline.IMPACT_AT, SplashTimeline.IMPACT_AT + 0.4f)
    val q = seg(t, SplashTimeline.IMPACT_AT, SplashTimeline.IMPACT_AT + 0.8f)
    if (p < 1f) glowDot(at.x, at.y, Color.White, 1f - p, lerp(14f, 70f, Ease.out(p)), halo = false)
    if (q < 1f) {
        drawCircle(
            Color.White.copy(alpha = 0.45f * (1f - q)), lerp(10f, 420f, Ease.out(q)), at,
            style = Stroke(width = 2.5f), blendMode = BlendMode.Plus,
        )
    }
    if (tau < 0.55f) {
        for (sp in SPARKS) {
            val d = sp.speed * (1f - exp(-6f * tau)) / 6f
            val c = cos(sp.angle)
            val s = sin(sp.angle)
            drawLine(
                sp.color.copy(alpha = 1f - tau / 0.55f),
                Offset(at.x + c * d * 0.55f, at.y + s * d * 0.55f), Offset(at.x + c * d, at.y + s * d),
                strokeWidth = 3f, blendMode = BlendMode.Plus,
            )
        }
    }
}

// ---------- the name ----------

private fun Paint.setFor(size: Float, color: Color, alpha: Float) {
    textSize = size
    this.color = color.toArgb()
    this.alpha = (clamp01(alpha) * 255).toInt()
}

/** The width of the whole word at [size], including the tracking between letters. */
private fun wordWidth(paint: Paint, size: Float, tracking: Float): Float {
    paint.textSize = size
    var w = 0f
    for (ch in SplashTimeline.WORD) w += paint.measureText(ch.toString())
    return w + tracking * size * (SplashTimeline.WORD.length - 1)
}

/** Draws the word with its tracking, its left edge at [left] and its middle on [cy]. */
private fun DrawScope.word(paint: Paint, left: Float, cy: Float, size: Float, tracking: Float, alpha: Float) {
    if (alpha <= 0f) return
    paint.setFor(size, AtomixColors.Text, alpha)
    val baseline = cy - (paint.descent() + paint.ascent()) / 2f
    var x = left
    drawContext.canvas.nativeCanvas.let { canvas ->
        for (ch in SplashTimeline.WORD) {
            val s = ch.toString()
            canvas.drawText(s, x, baseline, paint)
            x += paint.measureText(s) + tracking * size
        }
    }
}

// ---------- the two splashes ----------

private fun DrawScope.background(alpha: Float) {
    if (alpha <= 0f) return
    drawRect(AtomixColors.Ground.copy(alpha = alpha))
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0x8014263F).copy(alpha = 0.5f * alpha), Color.Transparent),
            Offset(CX, CY + 20f), 950f,
        ),
        950f, Offset(CX, CY + 20f),
    )
}

private fun DrawScope.drawPlayOut(t: Float, paint: Paint) {
    val fade = seg(t, SplashTimeline.HANDOFF_FROM, SplashTimeline.HANDOFF_TO)
    background(1f - fade)

    val size = SplashTimeline.LETTER_SIZE
    val tracking = SplashTimeline.LETTER_TRACKING
    val tw = wordWidth(paint, size, tracking)
    val lockup = SplashTimeline.LOCKUP_SCALE
    val markW = 25.4f * Mark.UNIT * lockup
    val gap = 48f
    val left = CX - (markW + gap + tw) / 2f
    val textLeft = left + markW + gap

    val e = Ease.inOut(seg(t, SplashTimeline.SLIDE_FROM, SplashTimeline.SLIDE_TO))
    val markCx = lerp(CX, left + markW / 2f, e)
    val markScale = lerp(1f, lockup, e)
    val bow = seg(t, SplashTimeline.HANDOFF_FROM, SplashTimeline.MARK_FADE_TO)
    val bump = if (t > SplashTimeline.IMPACT_AT) {
        sin(PI.toFloat() * seg(t, SplashTimeline.IMPACT_AT, SplashTimeline.IMPACT_AT + SplashTimeline.PULSE_DUR)) * SplashTimeline.PULSE_AMOUNT
    } else {
        0f
    }

    inMark(markCx - CX - bow * 80f, markScale * (1f - bow * 0.25f)) {
        introMark(t, 1f + bump, 1f - bow)
        particles(t)
    }
    if (t < SplashTimeline.IMPACT_AT) return

    val tipX = markCx + (Mark.TIP_X - Mark.CENTRE) * Mark.UNIT * markScale

    // A white streak runs right from the tip as the name starts to come out.
    val streak = 1f - seg(t, SplashTimeline.IMPACT_AT + 0.25f, 2.05f)
    if (streak > 0f) {
        val len = Ease.out(seg(t, SplashTimeline.IMPACT_AT, SplashTimeline.IMPACT_AT + SplashTimeline.STREAK_DUR)) * (tw + 160f)
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0.9f * streak), Color.Transparent),
                startX = tipX, endX = tipX + len + 1f,
            ),
            Offset(tipX, CY - 1.5f), Size(len, 3f), blendMode = BlendMode.Plus,
        )
    }

    if (t < SplashTimeline.HANDOFF_FROM) {
        // Each letter flies out of the tip to its place in the word, growing as it goes.
        paint.setFor(size, AtomixColors.Text, 1f)
        val baseline = CY - (paint.descent() + paint.ascent()) / 2f
        var x = textLeft
        SplashTimeline.WORD.forEachIndexed { i, ch ->
            val s = ch.toString()
            val cw = paint.measureText(s)
            val start = SplashTimeline.letterStart(i)
            val p = seg(t, start, start + SplashTimeline.LETTER_DUR)
            if (p > 0f) {
                val px = lerp(tipX, x, Ease.out(p))
                val sc = maxOf(0.01f, lerp(0.25f, 1f, Ease.back(p)))
                paint.alpha = (seg(t, start, start + 0.12f) * 255).toInt()
                withTransform({ scale(sc, sc, Offset(px + cw / 2f, CY)) }) {
                    drawContext.canvas.nativeCanvas.drawText(s, px, baseline, paint)
                }
            }
            x += cw + tracking * size
        }
    } else {
        // The name glides to where Home's own wordmark sits, and the splash fades out from under it.
        val p = Ease.inOut(seg(t, SplashTimeline.HANDOFF_FROM, SplashTimeline.HANDOFF_TO))
        word(
            paint,
            lerp(textLeft, SplashTimeline.HOME_LEFT, p), lerp(CY, SplashTimeline.HOME_CY, p),
            lerp(size, SplashTimeline.HOME_SIZE, p), lerp(tracking, SplashTimeline.HOME_TRACKING, p),
            1f - seg(t, SplashTimeline.HANDOFF_TO - 0.08f, SplashTimeline.HANDOFF_TO),
        )
    }

    impact(t, Offset(CX + (Mark.IMPACT.first - Mark.CENTRE) * Mark.UNIT, CY))
}

/** Reduced effects: the finished logo and name fade in, hold, and fade out onto Home. */
private fun DrawScope.drawReduced(t: Float, paint: Paint) {
    val out = seg(t, SplashTimeline.REDUCED_DURATION - SplashTimeline.REDUCED_FADE, SplashTimeline.REDUCED_DURATION)
    background(1f - out)
    val a = Ease.out(seg(t, 0f, SplashTimeline.REDUCED_FADE)) * (1f - out)
    withTransform({ translate(0f, -110f) }) {
        inMark(0f, 0.55f) { introMark(9f, 1f, a) }
    }
    val size = 84f
    word(paint, CX - wordWidth(paint, size, 0.16f) / 2f, CY + 80f, size, 0.16f, a)
}
