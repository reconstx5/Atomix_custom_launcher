package dev.atomix.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateColorTest {
    private val text = 0xFFF1F1F1.toInt()

    @Test fun darkenForText_reachesSevenToOne() {
        for (c in listOf(0xFFFFFFFF.toInt(), 0xFFFFD400.toInt(), 0xFF00A8E1.toInt(), 0xFFC4302B.toInt())) {
            assertTrue(contrast(text, darkenForText(c)) >= 7.0)
        }
    }

    @Test fun darkColour_isKept() {
        assertEquals(0xFF101010.toInt(), darkenForText(0xFF101010.toInt()))
    }

    @Test fun averageOpaque_ignoresTransparentPixels() {
        val px = intArrayOf(0x00FFFFFF, 0xFFFF0000.toInt(), 0xFFFF0000.toInt(), 0x10000000)
        assertEquals(0xFFFF0000.toInt(), averageOpaque(px))
    }

    @Test fun averageOpaque_allTransparent_isNull() {
        assertEquals(null, averageOpaque(intArrayOf(0, 0)))
    }

    @Test fun hashedPlate_isStablePerName_andDiffersAcrossNames() {
        assertEquals(hashedPlate("TVNZ 1"), hashedPlate("TVNZ 1"))
        assertNotEquals(hashedPlate("TVNZ 1"), hashedPlate("Three"))
        assertTrue(contrast(text, hashedPlate("Three")) >= 7.0)
    }

    @Test fun darkLogo_getsALightPlateItStandsOutOn() {
        val black = 0xFF101010.toInt()
        val plate = plateForLogo(black)
        assertTrue(contrast(black, plate) >= 7.0)
    }

    @Test fun colourfulLogo_getsItsDarkenedColour() {
        val red = 0xFFE0102A.toInt()
        assertEquals(darkenForText(red), plateForLogo(red))
    }
}
