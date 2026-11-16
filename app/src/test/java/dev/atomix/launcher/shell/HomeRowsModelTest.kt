package dev.atomix.launcher.shell

import dev.atomix.launcher.prefs.ROW_APPS
import dev.atomix.launcher.prefs.ROW_ON_NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Moving between the rows Home draws (which rows those are is RowsAndAppsTest's). */
class HomeRowsModelTest {
    @Test fun neighbours_skipRowsThatAreNotDrawn() {
        // On now hidden: Your apps and Tools are drawn next to each other.
        val drawn = listOf(ROW_APPS, "row-1")
        assertEquals("row-1", rowBelow(ROW_APPS, drawn))
        assertEquals(ROW_APPS, rowAbove("row-1", drawn))
        assertNull(rowAbove(ROW_APPS, drawn))
        assertNull(rowBelow("row-1", drawn))
        assertNull(rowAbove(ROW_ON_NOW, drawn))
        assertNull(rowBelow(ROW_ON_NOW, drawn))
    }
}
