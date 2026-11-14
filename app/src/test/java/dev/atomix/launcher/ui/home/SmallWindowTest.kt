package dev.atomix.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SmallWindowTest {
    @Test fun steppingOntoTheNextCard_turnsThePage_twoAtATime_stoppingAtTheEnd() {
        // Five small cards, two showing: 0 and 1, then 2 and 3, then 3 and 4.
        assertEquals(2, smallWindowStart(start = 0, small = 2, count = 5, page = true))
        assertEquals(3, smallWindowStart(start = 2, small = 4, count = 5, page = true))
    }

    @Test fun pagingStopsAtTheLastTwo() =
        assertEquals(2, smallWindowStart(start = 0, small = 2, count = 4, page = true))

    @Test fun steppingBackUp_turnsThePageBack_stoppingAtTheTop() {
        assertEquals(0, smallWindowStart(start = 2, small = 1, count = 5, page = true))
        assertEquals(1, smallWindowStart(start = 3, small = 2, count = 5, page = true))
    }

    @Test fun withoutPaging_theWindowMovesJustFarEnough() {
        assertEquals(2, smallWindowStart(start = 0, small = 3, count = 5, page = false))
        assertEquals(1, smallWindowStart(start = 3, small = 1, count = 5, page = false))
    }

    @Test fun aCardAlreadyShowing_movesNothing() {
        assertEquals(1, smallWindowStart(start = 1, small = 1, count = 5, page = true))
        assertEquals(1, smallWindowStart(start = 1, small = 2, count = 5, page = true))
    }

    @Test fun aStartPastTheEnd_isBroughtBackFirst() {
        // The row shrank to three small cards while the window was further down.
        assertEquals(1, smallWindowStart(start = 5, small = 2, count = 3, page = true))
        assertEquals(0, smallWindowStart(start = 5, small = 0, count = 3, page = false))
    }

    @Test fun oneOrNoSmallCards_startAtTheTop() {
        assertEquals(0, smallWindowStart(start = 0, small = 0, count = 1, page = true))
        assertEquals(0, smallWindowStart(start = 3, small = 0, count = 0, page = false))
    }
}
