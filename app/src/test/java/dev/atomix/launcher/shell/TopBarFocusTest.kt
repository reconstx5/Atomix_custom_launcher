package dev.atomix.launcher.shell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopBarFocusTest {
    @Test fun comingToAPage_alwaysRestoresItsFocus() {
        assertTrue(restoresFocus(byTick = true, barHasFocus = true))
        assertTrue(restoresFocus(byTick = true, barHasFocus = false))
    }

    @Test fun aListChangingUnderThePage_doesNotPullFocusOutOfTheTopBar() =
        assertFalse(restoresFocus(byTick = false, barHasFocus = true))

    @Test fun aListChangingUnderThePage_restoresFocusThatWasInThePage() =
        assertTrue(restoresFocus(byTick = false, barHasFocus = false))
}
