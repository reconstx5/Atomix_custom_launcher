package dev.atomix.launcher.livetv.source

/** What a channel is showing at a moment: the programme (null = no guide info), time left, progress, what's next. */
data class OnAir(val programme: Programme?, val minutesLeft: Int?, val progress: Float?, val upNext: Programme?)

private fun Programme.isOn(at: Long) = start <= at && at < end

/**
 * The guide can be stale while Home sits open: if `now` has ended and `next` has started, `next` is on;
 * if nothing covers [nowMs], there's no programme info (never negative minutes or a full bar).
 */
fun onAir(channel: Channel, nowMs: Long): OnAir {
    val now = channel.now
    val next = channel.next
    val current = when {
        now != null && now.isOn(nowMs) -> now
        next != null && next.isOn(nowMs) -> next
        else -> null
    }
    val upNext = when {
        current == null -> listOfNotNull(now, next).firstOrNull { it.start > nowMs }
        current === now -> next
        else -> null
    }
    if (current == null) return OnAir(null, null, null, upNext)
    val left = ((current.end - nowMs + 59_999) / 60_000).toInt()
    val progress = ((nowMs - current.start).toFloat() / (current.end - current.start)).coerceIn(0f, 1f)
    return OnAir(current, left, progress, upNext)
}
