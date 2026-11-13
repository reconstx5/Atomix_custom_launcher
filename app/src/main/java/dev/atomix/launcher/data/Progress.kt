package dev.atomix.launcher.data

import kotlin.math.ceil

private fun remaining(p: Progress?, itemDuration: Double?): Pair<Double, Double>? {
    if (p == null || p.watched || p.position <= 0.0) return null
    val duration = p.duration ?: itemDuration ?: return null
    if (duration <= 0.0) return null
    return p.position to duration
}

/** Minutes left to watch, or null when there is nothing to resume. */
fun minutesLeft(p: Progress?, itemDuration: Double?): Int? {
    val (position, duration) = remaining(p, itemDuration) ?: return null
    val left = ceil((duration - position) / 60.0).toInt()
    return left.takeIf { it > 0 }
}

/** How far through, 0..1, or null when there is nothing to resume. */
fun progressFraction(p: Progress?, itemDuration: Double?): Float? {
    val (position, duration) = remaining(p, itemDuration) ?: return null
    return (position / duration).toFloat().coerceIn(0f, 1f)
}
