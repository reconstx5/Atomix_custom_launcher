package dev.atomix.launcher.ui.kit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowStateTest {
    @Test fun shows_onlyTheRowsInTheWindow() {
        val w = WindowState(first = 3)
        assertTrue(w.shows(3, count = 20, visible = 7))
        assertTrue(w.shows(9, count = 20, visible = 7))
        assertFalse(w.shows(2, count = 20, visible = 7))
        assertFalse(w.shows(10, count = 20, visible = 7))
    }

    @Test fun shows_whenTheListHasShrunkUnderTheWindow_usesWhereTheWindowNowIs() {
        val w = WindowState(first = 50)
        assertTrue(w.shows(19, count = 20, visible = 7))
        assertTrue(w.shows(13, count = 20, visible = 7))
        assertFalse(w.shows(12, count = 20, visible = 7))
    }

    @Test fun shows_nothingThatIsNotInTheList() {
        assertFalse(WindowState().shows(-1, count = 20, visible = 7))
        assertFalse(WindowState().shows(0, count = 0, visible = 7))
        assertFalse(WindowState().shows(3, count = 3, visible = 7))
    }
}
