package dev.atomix.launcher.livetv.player

/** What a stall has earned so far. */
enum class StallAction {
    None,

    /** Long enough that the picture is clearly gone: say "Tuning…" again instead of holding a frozen frame. */
    Tuning,

    /** Long enough to treat as a fault: hand over to the retry ladder, which ends in the error card. */
    Failed,
}

/**
 * Watches for a stream that stops while the session itself looks healthy — the server keeps answering pings but no
 * pictures arrive, which is what a stuck timeshift playlist does. Without this the player holds the last frame with
 * no message for as long as the server keeps saying it is fine.
 */
class StallWatch(private val tuningAfterMs: Long = 5_000, private val failAfterMs: Long = 15_000) {
    private var failedFor: Long? = null

    /**
     * [bufferingSinceMs] is when the player started waiting for pictures, or null while they are arriving.
     * [paused] stalls are the user's doing, not a fault.
     */
    fun tick(bufferingSinceMs: Long?, nowMs: Long, paused: Boolean = false): StallAction {
        if (bufferingSinceMs == null || paused) {
            failedFor = null
            return StallAction.None
        }
        val stalledMs = nowMs - bufferingSinceMs
        return when {
            stalledMs >= failAfterMs && failedFor != bufferingSinceMs -> {
                failedFor = bufferingSinceMs
                StallAction.Failed
            }
            stalledMs >= failAfterMs -> StallAction.None
            stalledMs >= tuningAfterMs -> StallAction.Tuning
            else -> StallAction.None
        }
    }
}
