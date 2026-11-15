package dev.atomix.launcher.ui.home

import dev.atomix.launcher.prefs.HomeLayout
import dev.atomix.launcher.prefs.OnNowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnNowSlotsTest {
    @Test fun largeTwo_isTodaysOrder() {
        assertEquals(Slot.Large, slotOf(0, OnNowLayout.LargeTwo))
        assertEquals(Slot.Small(0, 0), slotOf(1, OnNowLayout.LargeTwo))
        assertEquals(Slot.Small(3, 0), slotOf(4, OnNowLayout.LargeTwo))
    }

    @Test fun largeFour_fillsTwoAcross() {
        assertEquals(Slot.Large, slotOf(0, OnNowLayout.LargeFour))
        assertEquals(Slot.Small(0, 1), slotOf(2, OnNowLayout.LargeFour))
        assertEquals(Slot.Small(1, 0), slotOf(3, OnNowLayout.LargeFour))
        assertEquals(Slot.Small(2, 1), slotOf(6, OnNowLayout.LargeFour))
    }

    @Test fun sixSmall_hasNoLarge_andFillsThreeAcross() {
        assertEquals(Slot.Small(0, 0), slotOf(0, OnNowLayout.SixSmall))
        assertEquals(Slot.Small(1, 2), slotOf(5, OnNowLayout.SixSmall))
        assertEquals(Slot.Small(2, 0), slotOf(6, OnNowLayout.SixSmall))
    }

    @Test fun indexAt_isTheOppositeOfSlotOf() = OnNowLayout.entries.forEach { layout ->
        for (i in 0 until 9) {
            val slot = slotOf(i, layout)
            if (slot is Slot.Small) assertEquals("$layout $i", i, indexAt(slot.row, slot.column, layout))
        }
    }

    @Test fun rowCounts() {
        assertEquals(0, smallRowCount(0, OnNowLayout.SixSmall)); assertEquals(1, smallRowCount(1, OnNowLayout.SixSmall))
        assertEquals(2, smallRowCount(6, OnNowLayout.SixSmall)); assertEquals(3, smallRowCount(7, OnNowLayout.SixSmall))
        assertEquals(0, smallRowCount(1, OnNowLayout.LargeFour)); assertEquals(1, smallRowCount(2, OnNowLayout.LargeFour))
        assertEquals(3, smallRowCount(7, OnNowLayout.LargeFour)); assertEquals(6, smallRowCount(7, OnNowLayout.LargeTwo))
        assertEquals(0, smallRowCount(0, OnNowLayout.LargeTwo))
    }

    @Test fun window_staysInsideAShorterList() {
        assertEquals(1, firstRowFor(wanted = 4, channels = 7, layout = OnNowLayout.SixSmall)) // three rows, two show
        assertEquals(0, firstRowFor(wanted = 4, channels = 5, layout = OnNowLayout.SixSmall))
        assertEquals(0, firstRowFor(wanted = -2, channels = 7, layout = OnNowLayout.LargeFour))
        assertEquals(0, firstRowFor(wanted = 3, channels = 0, layout = OnNowLayout.SixSmall))
        assertEquals(4, firstRowFor(wanted = 9, channels = 7, layout = OnNowLayout.LargeTwo))
    }

    /** A channel leaves Home while the cards are paged to the end: no card may be drawn at a row that is gone. */
    @Test fun aChannelLeaving_pullsTheWindowBack() {
        assertEquals(1, firstRowFor(wanted = 1, channels = 7, layout = OnNowLayout.SixSmall))
        assertEquals(0, firstRowFor(wanted = 1, channels = 6, layout = OnNowLayout.SixSmall))
        assertEquals(3, firstRowFor(wanted = 4, channels = 6, layout = OnNowLayout.LargeTwo))
    }

    @Test fun lastShown() {
        assertEquals(2, lastShownIndex(0, 7, OnNowLayout.LargeTwo)); assertEquals(4, lastShownIndex(0, 7, OnNowLayout.LargeFour))
        assertEquals(5, lastShownIndex(0, 7, OnNowLayout.SixSmall)); assertEquals(6, lastShownIndex(1, 7, OnNowLayout.SixSmall))
        assertEquals(1, lastShownIndex(0, 2, OnNowLayout.SixSmall)); assertEquals(-1, lastShownIndex(0, 0, OnNowLayout.SixSmall))
        assertEquals(0, lastShownIndex(0, 1, OnNowLayout.LargeFour)); assertEquals(6, lastShownIndex(4, 7, OnNowLayout.LargeTwo))
    }

    @Test fun places_defaultLayout_arePhase1s() {
        val m = homeMetrics(HomeLayout())
        assertEquals(CardPlace(0, 0, 1050, 506, true), cardPlace(Slot.Large, 0, m))
        assertEquals(CardPlace(1074, 0, 646, 244, true), cardPlace(Slot.Small(0, 0), 0, m))
        assertEquals(CardPlace(1074, 262, 646, 244, true), cardPlace(Slot.Small(1, 0), 0, m))
        assertFalse(cardPlace(Slot.Small(2, 0), 0, m).shown)
        assertFalse(cardPlace(Slot.Small(0, 0), 1, m).shown)
        assertEquals(-262, cardPlace(Slot.Small(0, 0), 1, m).y)
    }

    @Test fun places_sixSmall() {
        val m = homeMetrics(HomeLayout(onNow = OnNowLayout.SixSmall))
        assertEquals(CardPlace(2 * (561 + 18), 244 + 18, 561, 244, true), cardPlace(Slot.Small(1, 2), 0, m))
        assertEquals(CardPlace(0, 0, 561, 244, true), cardPlace(Slot.Small(0, 0), 0, m))
    }

    @Test fun places_largeFour() {
        val m = homeMetrics(HomeLayout(onNow = OnNowLayout.LargeFour))
        assertEquals(CardPlace(0, 0, 812, 506, true), cardPlace(Slot.Large, 0, m))
        assertEquals(CardPlace(836 + 433 + 18, 0, 433, 244, true), cardPlace(Slot.Small(0, 1), 0, m))
    }

    @Test fun onlyCardsInOrBesideTheWindow_areBuilt() {
        assertTrue(isBuilt(Slot.Large, firstRow = 3))
        assertTrue(isBuilt(Slot.Small(0, 0), 0)); assertTrue(isBuilt(Slot.Small(1, 2), 0)); assertTrue(isBuilt(Slot.Small(2, 0), 0))
        assertFalse(isBuilt(Slot.Small(3, 0), 0))
        assertTrue(isBuilt(Slot.Small(1, 0), 2)); assertFalse(isBuilt(Slot.Small(0, 0), 2))
    }

    @Test fun whereFocusEntersTheCards() {
        // With a large card it is the large card, whatever was focused last.
        assertEquals(0, onNowEntryIndex(remembered = 3, firstRow = 0, channels = 7, layout = OnNowLayout.LargeTwo))
        // Without one it is the card last focused while that card shows, else the first card showing.
        assertEquals(4, onNowEntryIndex(remembered = 4, firstRow = 0, channels = 7, layout = OnNowLayout.SixSmall))
        assertEquals(3, onNowEntryIndex(remembered = 0, firstRow = 1, channels = 7, layout = OnNowLayout.SixSmall))
        assertEquals(0, onNowEntryIndex(remembered = null, firstRow = 0, channels = 7, layout = OnNowLayout.SixSmall))
        assertNull(onNowEntryIndex(remembered = null, firstRow = 0, channels = 0, layout = OnNowLayout.SixSmall))
    }
}
