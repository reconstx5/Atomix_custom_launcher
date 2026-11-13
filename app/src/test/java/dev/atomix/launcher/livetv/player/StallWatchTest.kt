package dev.atomix.launcher.livetv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class StallWatchTest {
    private val watch = StallWatch()

    @Test fun whilePicturesKeepComing_nothingHappens() {
        assertEquals(StallAction.None, watch.tick(bufferingSinceMs = null, nowMs = 10_000))
    }

    @Test fun aShortRebufferIsLeftAlone() {
        assertEquals(StallAction.None, watch.tick(bufferingSinceMs = 1_000, nowMs = 4_000))
    }

    @Test fun afterFiveSecondsItSaysItIsTuningAgain() {
        assertEquals(StallAction.Tuning, watch.tick(bufferingSinceMs = 1_000, nowMs = 6_200))
    }

    @Test fun afterFifteenSecondsItHandsOverToTheRetryLadder() {
        assertEquals(StallAction.Failed, watch.tick(bufferingSinceMs = 1_000, nowMs = 16_100))
    }

    @Test fun itOnlyFailsOncePerStall() {
        assertEquals(StallAction.Failed, watch.tick(bufferingSinceMs = 1_000, nowMs = 16_100))
        assertEquals(StallAction.None, watch.tick(bufferingSinceMs = 1_000, nowMs = 20_000))
        // A new stall can fail again.
        assertEquals(StallAction.None, watch.tick(bufferingSinceMs = null, nowMs = 21_000))
        assertEquals(StallAction.Failed, watch.tick(bufferingSinceMs = 30_000, nowMs = 46_000))
    }

    @Test fun pausedPlaybackIsNotAStall() {
        assertEquals(StallAction.None, watch.tick(bufferingSinceMs = 1_000, nowMs = 30_000, paused = true))
    }
}
