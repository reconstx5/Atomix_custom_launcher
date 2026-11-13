package dev.atomix.launcher.livetv.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgrammeClockTest {
    private val min = 60_000L
    private fun prog(title: String, start: Long, end: Long) = Programme(title = title, start = start, end = end)

    @Test fun midProgramme_minutesLeftAndProgress() {
        val ch = tv("a", now = prog("News", 0, 60 * min), next = prog("Next", 60 * min, 90 * min))
        val on = onAir(ch, 45 * min)
        assertEquals("News", on.programme!!.title)
        assertEquals(15, on.minutesLeft)
        assertEquals(0.75f, on.progress!!, 0.001f)
        assertEquals("Next", on.upNext!!.title)
    }

    @Test fun partialMinute_roundsUp() {
        val on = onAir(tv("a", now = prog("News", 0, 60 * min)), 59 * min + 1)
        assertEquals(1, on.minutesLeft)
    }

    @Test fun nowEnded_nextStarted_usesNext() {
        val ch = tv("a", now = prog("Old", 0, 60 * min), next = prog("New", 60 * min, 120 * min))
        val on = onAir(ch, 70 * min)
        assertEquals("New", on.programme!!.title)
        assertEquals(50, on.minutesLeft)
        assertNull(on.upNext)
    }

    @Test fun nowEnded_noNext_isNoInfo() {
        val on = onAir(tv("a", now = prog("Old", 0, 60 * min)), 61 * min)
        assertNull(on.programme); assertNull(on.progress); assertNull(on.minutesLeft)
    }

    @Test fun noGuide_isNoInfo() {
        val on = onAir(tv("a"), 0)
        assertNull(on.programme); assertNull(on.progress); assertNull(on.upNext)
    }

    @Test fun notStartedYet_isNoInfo_butShowsItAsNext() {
        val on = onAir(tv("a", now = prog("Later", 10 * min, 20 * min)), 0)
        assertNull(on.programme)
        assertEquals("Later", on.upNext!!.title)
    }
}
