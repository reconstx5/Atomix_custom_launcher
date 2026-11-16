package dev.atomix.launcher.ui.home

import dev.atomix.launcher.apps.IconContent
import dev.atomix.launcher.apps.IconQuality
import dev.atomix.launcher.prefs.HomeLayout
import dev.atomix.launcher.prefs.Spacing
import dev.atomix.launcher.prefs.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeArtTest {
    @Test fun choice_iconFirst_thenBanner_thenLetter() {
        assertEquals(HomeArt.Icon, homeArtFor(IconQuality.Sharp, hasBanner = true))
        assertEquals(HomeArt.Icon, homeArtFor(IconQuality.Sharp, hasBanner = false))
        assertEquals(HomeArt.Banner, homeArtFor(IconQuality.Low, hasBanner = true))
        assertEquals(HomeArt.Icon, homeArtFor(IconQuality.Low, hasBanner = false))
        assertEquals(HomeArt.Banner, homeArtFor(IconQuality.None, hasBanner = true))
        assertEquals(HomeArt.Letter, homeArtFor(IconQuality.None, hasBanner = false))
    }

    @Test fun boxes_iconSquare_bannerSameArea() {
        assertEquals(ArtBox(77, 77), homeArtBox(HomeArt.Icon, 77))
        assertEquals(ArtBox(77, 77), homeArtBox(HomeArt.Letter, 77))
        assertEquals(ArtBox(103, 58), homeArtBox(HomeArt.Banner, 77))
        assertEquals(ArtBox(91, 51), homeArtBox(HomeArt.Banner, 68))
        assertEquals(ArtBox(165, 93), homeArtBox(HomeArt.Banner, 124))
    }

    @Test fun boxes_fitTheNarrowestTile() {
        val m = homeMetrics(HomeLayout(tileSize = TileSize.Small, spacing = Spacing.Roomy))
        HomeArt.entries.forEach { assertTrue(it.name, homeArtBox(it, m.artSlot).w <= m.tileW - 24) }
    }

    @Test fun letter_firstVisibleCharacter() {
        assertEquals("P", artLetter("prime Video"))
        assertEquals("T", artLetter("  TV Bro"))
        assertEquals("9", artLetter("9Now"))
        assertEquals("?", artLetter(""))
        assertEquals("?", artLetter("   "))
    }

    @Test fun iconFit_fullOrUnknown_fillsTheSlot() {
        assertEquals(IconFit(77, 0, 0), iconFit(null, 77))
        assertEquals(IconFit(77, 0, 0), iconFit(IconContent(0f, 0f, 1f, 1f), 77))
        assertEquals(IconFit(77, 0, 0), iconFit(IconContent(0.04f, 0.05f, 0.96f, 0.95f), 77)) // 90 % or more: as drawn
    }

    @Test fun iconFit_builtInMargin_scalesTheMarkToFill() {
        // SmartTube-like: a wide mark with empty space round it grows until its wider side spans the slot.
        assertEquals(IconFit(96, 0, 0), iconFit(IconContent(0.1f, 0.2f, 0.9f, 0.8f), 77))
    }

    @Test fun iconFit_neverMoreThanHalfAgainLarger() {
        assertEquals(IconFit(116, 0, 0), iconFit(IconContent(0.2f, 0.3f, 0.8f, 0.7f), 77))
    }

    @Test fun iconFit_offCentreMark_isCentred() {
        assertEquals(IconFit(116, 23, 23), iconFit(IconContent(0f, 0f, 0.6f, 0.6f), 77))
    }
}
