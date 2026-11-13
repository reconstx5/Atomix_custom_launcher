package dev.atomix.launcher.livetv.player

/** When a stream fails: retry after 1, 3 and 8 s, and show the error card by 20 s. */
class RetryPolicy(val delaysMs: List<Long> = listOf(1_000, 3_000, 8_000), val giveUpMs: Long = 20_000) {
    /** Delay before retry number [attempt] (0-based), or null to stop and show the error card. */
    fun nextDelay(attempt: Int, elapsedMs: Long): Long? =
        if (elapsedMs >= giveUpMs) null else delaysMs.getOrNull(attempt)
}
