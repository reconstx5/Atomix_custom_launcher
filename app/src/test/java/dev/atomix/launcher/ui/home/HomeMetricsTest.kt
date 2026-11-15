package dev.atomix.launcher.ui.home

import org.junit.Assert.assertNull
import dev.atomix.launcher.prefs.HomeLayout
import dev.atomix.launcher.prefs.OnNowLayout
import dev.atomix.launcher.prefs.ROW_APPS
import dev.atomix.launcher.prefs.ROW_ON_NOW
import dev.atomix.launcher.prefs.RowKind
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
        val m = homeMetrics(HomeLayout(), onNowFirst = true)
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
            for (o in listOf(false, true)) for (l in OnNowLayout.entries) {
                val m = homeMetrics(HomeLayout(s, sh, g, n, l), onNowFirst = o)
                val what = "$s $sh $g $n $o $l"
                assertTrue(what, m.cardsH >= MIN_CARDS_H)
                assertTrue(what, maxOf(m.appsY + m.rowH, m.cardsY + m.cardsH) <= 969)
                assertTrue(what, minOf(m.appsHeadingY, m.onNowHeadingY) == 126)
                assertTrue(what, m.smallX + m.columns * m.smallW + (m.columns - 1) * CARD_GAP <= 1720)
                assertTrue(what, m.smallH * 2 + CARD_GAP <= m.cardsH)
            }
    }

    private val twoRows = listOf(ROW_APPS to RowKind.Apps, ROW_ON_NOW to RowKind.OnNow)
    private val fourRows = listOf(ROW_APPS to RowKind.Apps, ROW_ON_NOW to RowKind.OnNow, "row-1" to RowKind.Apps, "row-2" to RowKind.Apps)

    @Test fun twoRows_placeExactlyAsPhase2() = listOf(
        HomeLayout(),
        HomeLayout(tileSize = TileSize.Large, tileShape = TileShape.FullBanner, spacing = Spacing.Compact),
    ).forEach { l ->
        val a = homeMetrics(l)
        val p = rowPlaces(l, twoRows)
        assertEquals(listOf(a.appsHeadingY, a.appsY, a.onNowHeadingY, a.cardsY, a.cardsH), listOf(p[0].headingY, p[0].y, p[1].headingY, p[1].y, p[1].h))
        assertEquals(a.moreY, p[1].moreY)
        val o = homeMetrics(l, onNowFirst = true)
        val q = rowPlaces(l, twoRows.reversed())
        assertEquals(listOf(o.cardsY, o.cardsH, o.appsHeadingY, o.appsY), listOf(q[0].y, q[0].h, q[1].headingY, q[1].y))
        assertEquals(969, q[1].y + q[1].h)
        assertNull(q[1].moreY)
    }

    @Test fun onNowInTheMiddle_hasTheBand_andAppsFirstHeight() {
        val l = HomeLayout()
        val p = rowPlaces(l, fourRows)
        assertEquals(homeMetrics(l).cardsH, p[1].h)
        assertEquals(p[1].y + p[1].h + 64, p[2].headingY)
        assertEquals(p[2].y + p[2].h + 30, p[3].headingY)
        assertEquals(84, p[3].y - p[3].headingY)
    }

    @Test fun scroll_twoRows_neverMoves() {
        listOf(twoRows, twoRows.reversed()).forEach { rows ->
            val p = rowPlaces(HomeLayout(), rows)
            assertEquals(0, scrollFor(0, p, 1))
            assertEquals(0, scrollFor(0, p, 0))
        }
    }

    @Test fun scroll_down_endsTheRowAt1040_up_putsItsHeadingAt126_firstIsZero() {
        val p = rowPlaces(HomeLayout(), fourRows)
        val atRow2 = scrollFor(0, p, 2)
        assertEquals(p[2].y + p[2].h - 1040, atRow2)
        val atRow3 = scrollFor(atRow2, p, 3)
        assertEquals(p[3].y + p[3].h - 1040, atRow3)
        // Row 1's heading is above the view from there: it lands at 126.
        assertEquals(p[1].headingY - 126, scrollFor(atRow3, p, 1))
        // Row 2 is whole on screen from row 3's place: nothing moves.
        assertEquals(atRow3, scrollFor(atRow3, p, 2))
        assertEquals(0, scrollFor(atRow3, p, 0))
    }

    @Test fun scroll_neverPastTheEnd_neverBelowZero() {
        val p = rowPlaces(HomeLayout(), fourRows)
        val last = p.last()
        assertTrue(scrollFor(0, p, 3) <= last.y + last.h - 1040)
        assertEquals(p[1].headingY - 126, scrollFor(5000, p, 1))
        assertTrue(scrollFor(0, p, 1) >= 0)
    }

    @Test fun scroll_onNowLast_keepsItsMoreLineOnScreen() {
        val rows = listOf(ROW_APPS to RowKind.Apps, "row-1" to RowKind.Apps, ROW_ON_NOW to RowKind.OnNow)
        val p = rowPlaces(HomeLayout(), rows)
        assertEquals(p[2].moreY!! + 30 - 1040, scrollFor(0, p, 2))
    }
}
