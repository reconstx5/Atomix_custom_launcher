package dev.atomix.launcher.ui.settings

import dev.atomix.launcher.prefs.AccentId
import dev.atomix.launcher.prefs.HomeLayout
import dev.atomix.launcher.prefs.Look
import dev.atomix.launcher.prefs.OnNowLayout
import dev.atomix.launcher.prefs.RowOrder
import dev.atomix.launcher.prefs.Spacing
import dev.atomix.launcher.prefs.ThemeId
import dev.atomix.launcher.prefs.TileShape
import dev.atomix.launcher.prefs.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePreviewTest {
    @Test fun default_showsSevenTiles_oneLarge_twoSmall() {
        val b = previewBlocks(HomeLayout())
        assertEquals(7, b.count { it.kind == BlockKind.Tile })
        assertEquals(1, b.count { it.kind == BlockKind.LargeCard })
        assertEquals(2, b.count { it.kind == BlockKind.SmallCard })
        assertEquals(PreviewBlock(BlockKind.LargeCard, 100, 463, 1050, 506), b.first { it.kind == BlockKind.LargeCard })
        assertEquals(PreviewBlock(BlockKind.Tile, 100, 208, 230, 141), b.first { it.kind == BlockKind.Tile })
        assertEquals(PreviewBlock(BlockKind.SmallCard, 1174, 463 + 244 + 18, 646, 244), b.last { it.kind == BlockKind.SmallCard })
    }

    @Test fun sixSmall_hasNoLargeCard() {
        val b = previewBlocks(HomeLayout(onNow = OnNowLayout.SixSmall))
        assertEquals(0, b.count { it.kind == BlockKind.LargeCard })
        assertEquals(6, b.count { it.kind == BlockKind.SmallCard })
    }

    @Test fun largeFour_hasFourSmall() = assertEquals(4, previewBlocks(HomeLayout(onNow = OnNowLayout.LargeFour)).count { it.kind == BlockKind.SmallCard })

    @Test fun largeTiles_showFive_smallShowNine() {
        assertEquals(5, previewBlocks(HomeLayout(tileSize = TileSize.Large)).count { it.kind == BlockKind.Tile })
        assertEquals(9, previewBlocks(HomeLayout(tileSize = TileSize.Small)).count { it.kind == BlockKind.Tile })
    }

    @Test fun everyBlock_isInsideTheMargins_inEveryCombination() {
        for (s in TileSize.entries) for (sh in TileShape.entries) for (o in RowOrder.entries) for (l in OnNowLayout.entries)
            previewBlocks(HomeLayout(s, sh, Spacing.Roomy, true, o, l)).forEach {
                assertTrue("$s $sh $o $l $it", it.x >= 100 && it.x + it.w <= 1820 && it.y >= 208 && it.y + it.h <= 969)
            }
    }

    @Test fun marks_followWhatTheFocusedRowIsAbout() {
        assertEquals(PreviewMark.FocusedCard, previewMark(SettingsItem.Accent))
        assertEquals(PreviewMark.FocusedCard, previewMark(SettingsItem.Theme))
        assertEquals(PreviewMark.FocusedCard, previewMark(SettingsItem.FocusHighlight))
        assertEquals(PreviewMark.None, previewMark(SettingsItem.Clock))
        assertEquals(PreviewMark.None, previewMark(null))
    }

    @Test fun thePreviewShowsTheChoiceWithFocus_beforeOk() {
        val now = Chosen(Look())
        assertEquals(ThemeId.Plum, previewChoice(now, open = SettingsItem.Theme, focused = 3).look.theme)
        assertEquals(now, previewChoice(now, open = SettingsItem.Theme, focused = null))
        assertEquals(now, previewChoice(now, open = null, focused = 2))
        assertEquals(AccentId.Ice, previewChoice(now, open = SettingsItem.Theme, focused = 3).look.accent)
    }

    @Test fun onlySectionsWithALook_haveAPreview() {
        assertTrue(hasPreview(SettingsSection.Appearance))
        assertEquals(false, hasPreview(SettingsSection.TvSettings))
        assertEquals(false, hasPreview(SettingsSection.About))
    }

    @Test fun theTileRows_markTheTilesInThePreview() =
        listOf(SettingsItem.TileSize, SettingsItem.TileShape, SettingsItem.Spacing, SettingsItem.AppNames).forEach { assertEquals(PreviewMark.Tiles, previewMark(it)) }

    @Test fun homeLayout_hasAPreview() = assertTrue(hasPreview(SettingsSection.HomeLayout))

    @Test fun thePreviewShowsATileShapeWithFocus_beforeOk() =
        assertEquals(TileShape.FullBanner, previewChoice(Chosen(Look()), open = SettingsItem.TileShape, focused = 1).layout.tileShape)

    @Test fun onNowLayout_marksTheCardsInThePreview() = assertEquals(PreviewMark.Cards, previewMark(SettingsItem.OnNowLayout))
}
