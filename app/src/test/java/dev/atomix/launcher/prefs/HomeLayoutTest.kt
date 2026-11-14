package dev.atomix.launcher.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {
    @Test fun defaults_areTodaysHome() =
        assertEquals(HomeLayout(TileSize.Medium, TileShape.Wide, Spacing.Comfortable, true, RowOrder.AppsFirst, OnNowLayout.LargeTwo), HomeLayout())

    @Test fun labels_areTheWordsSettingsShows() {
        assertEquals(listOf("Small", "Medium", "Large"), TileSize.entries.map { it.label })
        assertEquals(listOf("Wide tile", "Full banner"), TileShape.entries.map { it.label })
        assertEquals(listOf("Compact", "Comfortable", "Roomy"), Spacing.entries.map { it.label })
        assertEquals(listOf("Apps, then On now", "On now, then Apps"), RowOrder.entries.map { it.label })
        assertEquals(listOf("One large, two small", "One large, four small", "Six small"), OnNowLayout.entries.map { it.label })
    }

    @Test fun tilesAcross_andSpacing_areTheSpecs() {
        assertEquals(listOf(9, 7, 5), TileSize.entries.map { it.across })
        assertEquals(listOf(12, 18, 28), Spacing.entries.map { it.gap })
    }

    @Test fun onNowLayouts_slots() {
        assertEquals(listOf(true, true, false), OnNowLayout.entries.map { it.large })
        assertEquals(listOf(1, 2, 3), OnNowLayout.entries.map { it.columns })
        assertEquals(2, SMALL_ROWS)
    }
}
