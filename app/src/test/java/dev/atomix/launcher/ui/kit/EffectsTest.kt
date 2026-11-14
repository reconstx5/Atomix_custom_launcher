package dev.atomix.launcher.ui.kit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsTest {
    @Test fun full_hasGlowAndShortMotion() {
        val e = Effects(reduced = false)
        assertTrue(e.glow)
        assertEquals(150, e.motionMs)
    }

    @Test fun reduced_dropsGlowAndMotion() {
        val e = Effects(reduced = true)
        assertFalse(e.glow)
        assertEquals(0, e.motionMs)
    }

    @Test fun glow_needsBothFullEffectsAndTheChoice() {
        assertTrue(Effects(reduced = false, glowWanted = true).glow)
        assertFalse(Effects(reduced = false, glowWanted = false).glow)
        assertFalse(Effects(reduced = true, glowWanted = true).glow)
    }

    @Test fun glow_isWanted_unlessSettingsSaysOtherwise() = assertTrue(Effects(reduced = false).glowWanted)
}
