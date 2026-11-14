package dev.atomix.launcher.ui.splash

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = (2 * PI).toFloat()

fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x

/** How far [t] has come through the window [a]..[b], clamped to 0..1. */
fun seg(t: Float, a: Float, b: Float): Float = clamp01((t - a) / (b - a))

fun lerp(a: Float, b: Float, p: Float): Float = a + (b - a) * p

object Ease {
    fun out(t: Float) = 1f - (1f - t).pow(3)
    fun inOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
    /** Overshoots a little before settling, for the pop. */
    fun back(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }
}

/**
 * The Atomix mark, in the logo's own 32-unit space (the same geometry as MARK in the web app): two 12.6 × 4.6 orbits
 * tilted ±45° about (16, 16), drawn as their end loops only, with a play-button nucleus between them.
 */
object Mark {
    /** The logo's viewBox is 32 units across; the mark is drawn 300 px wide on a 1920×1080 frame. */
    const val UNIT = 9.375f
    const val CENTRE = 16f
    private val ROT = floatArrayOf((PI / 4).toFloat(), (-PI / 4).toFloat())
    private const val RX = 12.6f
    private const val RY = 4.6f

    /** The point at angle [th] on orbit [k]. */
    fun orb(k: Int, th: Float): Pair<Float, Float> {
        val r = ROT[k]
        val lx = RX * cos(th)
        val ly = RY * sin(th)
        return CENTRE + lx * cos(r) - ly * sin(r) to CENTRE + lx * sin(r) + ly * cos(r)
    }

    /** The angle on orbit [k] that [x], [y] sits at. */
    fun thetaOf(k: Int, x: Float, y: Float): Float {
        val r = ROT[k]
        val dx = x - CENTRE
        val dy = y - CENTRE
        val lx = dx * cos(r) + dy * sin(r)
        val ly = -dx * sin(r) + dy * cos(r)
        return atan2(ly / RY, lx / RX)
    }

    /** Each orbit is drawn as two arcs (its end loops), as [start, end] angle pairs. */
    val ARCS: Array<Array<FloatArray>> = arrayOf(
        arrayOf(floatArrayOf(8.57f, 14.11f, 14.29f, 8.68f), floatArrayOf(23.51f, 18f, 16.27f, 22.32f)),
        arrayOf(floatArrayOf(14.29f, 23.32f, 8.57f, 17.89f), floatArrayOf(16.27f, 9.68f, 23.51f, 14f)),
    ).mapIndexed { k, arcs ->
        arcs.map { (x0, y0, x1, y1) ->
            val a = thetaOf(k, x0, y0)
            var b = thetaOf(k, x1, y1)
            while (b < a) b += TAU
            floatArrayOf(a, b)
        }.toTypedArray()
    }.toTypedArray()

    private operator fun FloatArray.component3() = this[2]
    private operator fun FloatArray.component4() = this[3]

    /** Where the two orbits cross, just past the play button's tip: the particles meet here. */
    val IMPACT: Pair<Float, Float> = run {
        val d = 1f / sqrt(0.5f / (RX * RX) + 0.5f / (RY * RY))
        CENTRE + d to CENTRE
    }

    private val TH0 = thetaOf(0, IMPACT.first, IMPACT.second)
    private val TH1 = thetaOf(1, IMPACT.first, IMPACT.second)
    /** From an electron's resting spot (θ = 0) the long way round to the crossing. */
    private val SPAN = TAU - TH1

    /** Starts moving, then accelerates into the hit. */
    fun raceEase(u: Float): Float = 0.3f * u + 0.7f * u * u * u

    /** Where particle [k] is at [t], racing from its resting spot at [t0] to the crossing at [t1]. */
    fun racePos(k: Int, t: Float, t0: Float, t1: Float): Pair<Float, Float> {
        val e = raceEase(seg(t, t0, t1))
        return orb(k, if (k == 1) TH1 + SPAN * (1f - e) else TH0 - SPAN * (1f - e))
    }

    /** The play button's tip, in logo units. */
    const val TIP_X = 21.4f
}

/** Concept D, "Play-out": the particles collide at the play button's tip and the name streams out of it. */
object SplashTimeline {
    const val WORD = "ATOMIX TV"

    const val DURATION = 3.2f

    /** Both particles appear, then race. */
    const val PARTICLES_POP_FROM = 0.5f
    const val PARTICLES_POP_TO = 0.75f
    const val RACE_FROM = 0.8f
    const val RACE_TO = 1.5f

    /** They collide; the play button pulses and a streak runs right from the tip. */
    const val IMPACT_AT = 1.5f
    const val PULSE_DUR = 0.32f
    const val PULSE_AMOUNT = 0.16f
    const val STREAK_DUR = 0.35f

    /** The mark slides left and shrinks into a lockup beside the name. */
    const val SLIDE_FROM = 1.55f
    const val SLIDE_TO = 2.15f
    const val LOCKUP_SCALE = 0.62f

    /** The letters stream out of the tip, one every 50 ms. */
    const val LETTER_FIRST = 1.58f
    const val LETTER_STAGGER = 0.05f
    const val LETTER_DUR = 0.42f
    const val LETTER_SIZE = 96f
    const val LETTER_TRACKING = 0.16f

    /** The mark bows out and the name glides to the Home wordmark's spot. */
    const val HANDOFF_FROM = 2.6f
    const val HANDOFF_TO = 3.1f
    const val MARK_FADE_TO = 2.9f
    const val HOME_LEFT = 100f
    const val HOME_CY = 60f
    const val HOME_SIZE = 26f
    const val HOME_TRACKING = 0.24f

    /** Reduced effects: the finished logo and name fade in, then Home. */
    const val REDUCED_DURATION = 1.6f
    const val REDUCED_FADE = 0.4f

    fun letterStart(i: Int): Float = LETTER_FIRST + i * LETTER_STAGGER
}

/**
 * The splash plays on a cold start only: once per process, and never when Android recreates the activity or when
 * the Home button brings the existing one back (HomeActivity is singleTask, so that is not a new process).
 */
open class SplashPolicy {
    private var spent = false

    /** One per process: HomeActivity is singleTask, so the Home button reuses the instance and never replays it. */
    companion object Process : SplashPolicy()

    fun claim(recreated: Boolean): Boolean {
        if (spent || recreated) {
            spent = true
            return false
        }
        spent = true
        return true
    }
}
