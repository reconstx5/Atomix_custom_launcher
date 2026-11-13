package dev.atomix.launcher.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class SplashTimelineTest {
    private fun near(a: Float, b: Float, eps: Float = 1e-3f) = abs(a - b) <= eps

    @Test fun orbitPoint_roundTripsThroughItsAngle() {
        for (k in 0..1) for (th in listOf(0f, 0.7f, 2.2f, -1.1f)) {
            val (x, y) = Mark.orb(k, th)
            val back = Mark.thetaOf(k, x, y)
            val (x2, y2) = Mark.orb(k, back)
            assertTrue("k=$k th=$th -> ($x,$y) vs ($x2,$y2)", near(x, x2, 1e-2f) && near(y, y2, 1e-2f))
        }
    }

    @Test fun theOrbitsCrossJustPastThePlayTip() {
        val (ix, iy) = Mark.IMPACT
        assertEquals(16f, iy, 1e-3f)
        // Past the play button's tip at x = 21.4, and on both orbits.
        assertTrue("impact x=$ix", ix > 21.4f)
        for (k in 0..1) {
            val (x, y) = Mark.orb(k, Mark.thetaOf(k, ix, iy))
            assertTrue("orbit $k passes through the impact point", hypot(x - ix, y - iy) < 0.01f)
        }
    }

    @Test fun eachParticleStartsAtItsRestingSpotAndEndsOnTheImpact() {
        for (k in 0..1) {
            val start = Mark.racePos(k, t = 0.8f, t0 = 0.8f, t1 = 1.5f)
            val resting = Mark.orb(k, 0f)
            assertTrue("k=$k starts at theta 0", hypot(start.first - resting.first, start.second - resting.second) < 0.01f)

            val end = Mark.racePos(k, t = 1.5f, t0 = 0.8f, t1 = 1.5f)
            assertTrue("k=$k ends on the impact", hypot(end.first - Mark.IMPACT.first, end.second - Mark.IMPACT.second) < 0.01f)
        }
    }

    @Test fun theRaceAccelerates() {
        assertEquals(0f, Mark.raceEase(0f), 1e-6f)
        assertEquals(1f, Mark.raceEase(1f), 1e-6f)
        // Behind a linear run at the half-way mark, and gaining on it.
        assertTrue(Mark.raceEase(0.5f) < 0.5f)
        assertTrue(Mark.raceEase(0.9f) - Mark.raceEase(0.8f) > Mark.raceEase(0.2f) - Mark.raceEase(0.1f))
    }

    @Test fun lettersStreamOutOneByOneAndLandBeforeTheHandoff() {
        assertEquals(9, SplashTimeline.WORD.length)
        assertEquals(SplashTimeline.LETTER_FIRST, SplashTimeline.letterStart(0), 1e-6f)
        assertEquals(SplashTimeline.LETTER_FIRST + 0.05f, SplashTimeline.letterStart(1), 1e-6f)
        val last = SplashTimeline.letterStart(SplashTimeline.WORD.length - 1) + SplashTimeline.LETTER_DUR
        assertTrue("last letter lands at $last, before the hand-off", last <= SplashTimeline.HANDOFF_FROM)
    }

    @Test fun theImpactIsAfterTheRaceAndBeforeTheSlide() {
        assertTrue(SplashTimeline.RACE_FROM < SplashTimeline.IMPACT_AT)
        assertEquals(SplashTimeline.IMPACT_AT, SplashTimeline.RACE_TO, 1e-6f)
        assertTrue(SplashTimeline.IMPACT_AT < SplashTimeline.SLIDE_FROM)
        assertTrue(SplashTimeline.HANDOFF_TO <= SplashTimeline.DURATION)
    }

    @Test fun segmentClampsOutsideItsWindow() {
        assertEquals(0f, seg(0.4f, 0.5f, 1f), 1e-6f)
        assertEquals(0.5f, seg(0.75f, 0.5f, 1f), 1e-6f)
        assertEquals(1f, seg(2f, 0.5f, 1f), 1e-6f)
    }

    @Test fun reducedEffectsIsAShortFadeInstead() {
        assertTrue(SplashTimeline.REDUCED_DURATION < SplashTimeline.DURATION)
        assertEquals(0.4f, SplashTimeline.REDUCED_FADE, 1e-6f)
    }

    @Test fun itPlaysOnceForTheProcess_notWhenHomeReopensIt() {
        val policy = SplashPolicy()
        assertTrue("a cold start plays it", policy.claim(recreated = false))
        assertFalse("pressing Home does not replay it", policy.claim(recreated = false))
    }

    @Test fun itDoesNotPlayWhenTheActivityIsRecreated() {
        assertFalse(SplashPolicy().claim(recreated = true))
    }
}
