package dev.atomix.launcher.ui.home

import dev.atomix.launcher.prefs.HomeLayout
import dev.atomix.launcher.prefs.OnNowLayout
import dev.atomix.launcher.prefs.RowOrder
import dev.atomix.launcher.prefs.Spacing
import dev.atomix.launcher.prefs.TileShape
import dev.atomix.launcher.prefs.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMetricsTest {
    @Test fun defaults_arePhase1sHomeExactly() {
        val m = homeMetrics(HomeLayout())
        assertEquals(230, m.tileW); assertEquals(141, m.tileH); assertEquals(141, m.rowH)
        assertEquals(112, m.bannerW); assertEquals(63, m.bannerH); assertEquals(18, m.gap)
        assertEquals(28, m.namePx); assertEquals(34, m.nameLine); assertEquals(10, m.nameGap)
        assertTrue(m.showNames); assertFalse(m.nameUnder)
        assertEquals(126, m.appsHeadingY); assertEquals(208, m.appsY)
        assertEquals(379, m.onNowHeadingY); assertEquals(463, m.cardsY); assertEquals(506, m.cardsH)
        assertEquals(1050, m.largeW); assertEquals(1074, m.smallX); assertEquals(646, m.smallW); assertEquals(244, m.smallH)
        assertEquals(1, m.columns)
    }

    @Test fun tileSizes_fitTheirCountAcross() = TileSize.entries.forEach { s ->
        Spacing.entries.forEach { g ->
            val m = homeMetrics(HomeLayout(tileSize = s, spacing = g))
            val used = s.across * m.tileW + (s.across - 1) * g.gap
            assertTrue("$s $g", used <= 1720)
            assertTrue("$s $g", 1720 - used < s.across)
        }
    }

    @Test fun wideTileHeights() {
        assertEquals(128, homeMetrics(HomeLayout(tileSize = TileSize.Small)).tileH)
        assertEquals(194, homeMetrics(HomeLayout(tileSize = TileSize.Large)).tileH)
    }

    @Test fun namesOff_shrinksAWideTileToItsBannerAndPadding() {
        val m = homeMetrics(HomeLayout(appNames = false))
        assertEquals(63 + 34, m.tileH)
        assertFalse(m.showNames)
    }

    @Test fun fullBanner_is16by9_withTheNameUnderIt() {
        val m = homeMetrics(HomeLayout(tileShape = TileShape.FullBanner))
        assertEquals(230 * 9 / 16, m.tileH)
        assertEquals(m.tileW, m.bannerW); assertEquals(m.tileH, m.bannerH)
        assertTrue(m.nameUnder)
        assertEquals(m.tileH + 10 + 34, m.rowH)
    }

    @Test fun fullBanner_withNamesOff_isJustTheBanner() {
        val m = homeMetrics(HomeLayout(tileShape = TileShape.FullBanner, appNames = false))
        assertEquals(m.tileH, m.rowH)
    }

    @Test fun biggerTiles_leaveShorterCards() {
        val medium = homeMetrics(HomeLayout())
        val large = homeMetrics(HomeLayout(tileSize = TileSize.Large))
        assertEquals(medium.cardsH - (194 - 141), large.cardsH)
        assertEquals(969, large.cardsY + large.cardsH)
    }

    @Test fun onNowFirst_swapsTheRows_andKeepsABandUnderTheCards() {
        val m = homeMetrics(HomeLayout(rowOrder = RowOrder.OnNowFirst))
        assertEquals(126, m.onNowHeadingY); assertEquals(208, m.cardsY)
        assertEquals(969 - 141, m.appsY); assertEquals(m.appsY - 84, m.appsHeadingY)
        assertEquals(64, m.appsHeadingY - (m.cardsY + m.cardsH))
        assertEquals(m.cardsY + m.cardsH + 16, m.moreY)
    }

    @Test fun appsFirst_theMoreLineSitsUnderTheCards_inTheEmptyBand() = assertEquals(969 + 16, homeMetrics(HomeLayout()).moreY)

    @Test fun grids() {
        val four = homeMetrics(HomeLayout(onNow = OnNowLayout.LargeFour))
        assertEquals(812, four.largeW); assertEquals(836, four.smallX); assertEquals(433, four.smallW); assertEquals(2, four.columns)
        val six = homeMetrics(HomeLayout(onNow = OnNowLayout.SixSmall))
        assertEquals(0, six.largeW); assertEquals(0, six.smallX); assertEquals(561, six.smallW); assertEquals(3, six.columns)
    }

    @Test fun noCombination_leavesTheCardsTooShort_orOffThePage() {
        for (s in TileSize.entries) for (sh in TileShape.entries) for (g in Spacing.entries) for (n in listOf(true, false))
            for (o in RowOrder.entries) for (l in OnNowLayout.entries) {
                val m = homeMetrics(HomeLayout(s, sh, g, n, o, l))
                val what = "$s $sh $g $n $o $l"
                assertTrue(what, m.cardsH >= MIN_CARDS_H)
                assertTrue(what, maxOf(m.appsY + m.rowH, m.cardsY + m.cardsH) <= 969)
                assertTrue(what, minOf(m.appsHeadingY, m.onNowHeadingY) == 126)
                assertTrue(what, m.smallX + m.columns * m.smallW + (m.columns - 1) * CARD_GAP <= 1720)
                assertTrue(what, m.smallH * 2 + CARD_GAP <= m.cardsH)
            }
    }
}
