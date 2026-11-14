package dev.atomix.launcher.ui.theme

import dev.atomix.launcher.prefs.AccentId
import dev.atomix.launcher.prefs.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {
    @Test fun midnight_isPhase1sColoursExactly() {
        val p = paletteFor(ThemeId.Midnight)
        assertEquals(0xFF0A1322, p.ground); assertEquals(0xFF16243A, p.tile); assertEquals(0xFF1B2C46, p.raised)
        assertEquals(0xFF101B2C, p.panel); assertEquals(0xFF0E1828, p.inset); assertEquals(0xFF24344D, p.keyCap)
        assertEquals(0xFF1A2638, p.roundButton); assertEquals(0xFF111D2E, p.card); assertEquals(0xFF2A3A52, p.switchOff)
        assertEquals(0xFF9FB0C6, p.label); assertEquals(0xFF0A1220, p.overVideo)
        assertEquals(listOf(0xFF14263F, 0xFF0B1626, 0xFF070E1A), p.wash)
        assertEquals(listOf(0xFF0F1C30, 0xFF070E1A, 0xFF05090F), p.playerWash)
    }

    @Test fun otherThemes_haveTheSpecsGrounds() {
        assertEquals(0xFF000000, paletteFor(ThemeId.Black).ground)
        assertEquals(0xFF14171C, paletteFor(ThemeId.Graphite).ground)
        assertEquals(0xFF150F1E, paletteFor(ThemeId.Plum).ground)
    }

    @Test fun everyTheme_keepsTextReadable() = ThemeId.entries.forEach { t ->
        val p = paletteFor(t)
        val white = 0xFFFFFFFF
        listOf(p.tile, p.raised, p.panel).forEach { assertTrue("$t white", contrast(white, it) >= 4.5) }
        listOf(p.ground, p.tile, p.raised).forEach { assertTrue("$t label", contrast(p.label, it) >= 4.5) }
    }

    @Test fun everyTheme_raisesAFocusedSurfaceAboveItsTile() = ThemeId.entries.forEach { t ->
        val p = paletteFor(t)
        assertTrue("$t", contrast(p.raised, 0xFF000000) > contrast(p.tile, 0xFF000000))
    }

    @Test fun everyAccent_isReadableUnderDarkInk() = AccentId.entries.forEach { assertTrue(it.name, contrast(0xFF0A1322, it.argb) >= 4.5) }

    @Test fun ring_isIcesOwn_andLighterThanTheAccentForTheRest() {
        assertEquals(0xFFBFE6FF, ringArgb(AccentId.Ice))
        AccentId.entries.forEach { assertTrue(it.name, contrast(ringArgb(it), 0xFF000000) > contrast(it.argb, 0xFF000000)) }
    }

    @Test fun ring_movesEachChannelAQuarterOfTheWayToWhite() = assertEquals(0xFFFFDCA8, ringArgb(AccentId.Amber))

    @Test fun contrast_knownValues() {
        assertEquals(21.0, contrast(0xFFFFFFFF, 0xFF000000), 0.01)
        assertEquals(1.0, contrast(0xFF123456, 0xFF123456), 0.001)
        assertEquals(contrast(0xFF16243A, 0xFFFFFFFF), contrast(0xFFFFFFFF, 0xFF16243A), 0.0001)
    }
}
