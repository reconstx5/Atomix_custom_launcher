package dev.atomix.launcher.ui.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsTest {
    @Test fun full_usesResearchTimings() {
        val e = Effects(reduced = false)
        assertEquals(150, e.focusInMs)
        assertEquals(100, e.focusOutMs)
        assertEquals(250, e.crossfadeMs)
        assertTrue(e.glass); assertTrue(e.shadows)
    }

    @Test fun reduced_isInstantAndSolid() {
        val e = Effects(reduced = true)
        assertEquals(0, e.focusInMs)
        assertEquals(0, e.focusOutMs)
        assertEquals(0, e.crossfadeMs)
        assertFalse(e.glass); assertFalse(e.shadows)
    }
}
