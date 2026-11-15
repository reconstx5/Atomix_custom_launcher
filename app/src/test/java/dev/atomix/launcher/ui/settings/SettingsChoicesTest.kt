package dev.atomix.launcher.ui.settings

import dev.atomix.launcher.prefs.OnNowLayout
import dev.atomix.launcher.prefs.TileSize
import dev.atomix.launcher.prefs.TileShape
import dev.atomix.launcher.prefs.Spacing
import dev.atomix.launcher.prefs.HomeLayout
import dev.atomix.launcher.prefs.TextSize
import dev.atomix.launcher.prefs.FocusStyle
import dev.atomix.launcher.prefs.ClockChoice
import dev.atomix.launcher.prefs.AccentId
import dev.atomix.launcher.prefs.Look
import dev.atomix.launcher.prefs.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsChoicesTest {
    @Test fun theme_optionsAreTheFourThemes() = assertEquals(listOf("Midnight", "Black", "Graphite", "Plum"), choiceOptions(SettingsItem.Theme))

    @Test fun accent_optionsAreTheFive() = assertEquals(listOf("Ice", "Mint", "Amber", "Coral", "Violet"), choiceOptions(SettingsItem.Accent))

    @Test fun chosenIndex_readsTheLook() {
        assertEquals(3, chosenIndex(SettingsItem.Theme, Chosen(Look(theme = ThemeId.Plum))))
        assertEquals(0, chosenIndex(SettingsItem.Theme, Chosen(Look())))
        assertEquals(2, chosenIndex(SettingsItem.Accent, Chosen(Look(accent = AccentId.Amber))))
    }

    @Test fun withChoice_changesOnlyThatSetting() {
        val now = Chosen(Look(accent = AccentId.Coral))
        assertEquals(Chosen(Look(theme = ThemeId.Black, accent = AccentId.Coral)), withChoice(SettingsItem.Theme, 1, now))
        assertEquals(Chosen(Look(accent = AccentId.Violet)), withChoice(SettingsItem.Accent, 4, now))
    }

    @Test fun withChoice_outOfRange_changesNothing() {
        val now = Chosen(Look())
        assertEquals(now, withChoice(SettingsItem.Accent, 9, now))
        assertEquals(now, withChoice(SettingsItem.Accent, -1, now))
    }

    @Test fun anItemThatIsNotAChoice_hasNoOptions_andChoosingChangesNothing() {
        assertEquals(emptyList<String>(), choiceOptions(SettingsItem.Version))
        assertEquals(Chosen(Look()), withChoice(SettingsItem.Version, 0, Chosen(Look())))
    }

    @Test fun kinds() {
        assertEquals(RowKind.List, kindOf(SettingsItem.Theme))
        assertEquals(RowKind.Stepped, kindOf(SettingsItem.Accent))
        assertEquals(RowKind.Switch, kindOf(SettingsItem.ReducedEffects))
        assertEquals(RowKind.Statement, kindOf(SettingsItem.Version))
        assertEquals(RowKind.Statement, kindOf(SettingsItem.Server))
        assertEquals(RowKind.Action, kindOf(SettingsItem.TvPages))
    }

    @Test fun stepHint_saysWhatTheArrowsChange() {
        assertEquals("Pick a colour", stepHintFor(SettingsItem.Accent))
        assertNull(stepHintFor(SettingsItem.Theme))
    }

    @Test fun appearance_isTwoGroups_asOnItsBoard() {
        val g = groupsOf(SettingsSection.Appearance)
        assertEquals(listOf("LOOK", "FOCUS AND MOTION"), g.map { it.label })
        assertEquals(rowsIn(SettingsSection.Appearance), g.flatMap { it.items })
    }

    @Test fun rowTop_firstGroupStartsUnderItsLabel_rowsAre104Apart() {
        val g = groupsOf(SettingsSection.Appearance)
        assertEquals(36, rowTop(SettingsItem.Theme, g))
        assertEquals(140, rowTop(SettingsItem.Accent, g))
    }

    @Test fun rowTop_aNewGroupStarts72UnderTheLastRow() {
        val g = listOf(PaneGroup("A", listOf(SettingsItem.Theme, SettingsItem.Accent)), PaneGroup("B", listOf(SettingsItem.ReducedEffects)))
        assertEquals(140 + 92 + 72, rowTop(SettingsItem.ReducedEffects, g))
    }

    @Test fun rowTop_ofARowThatIsNotThere_isNull() = assertNull(rowTop(SettingsItem.Version, groupsOf(SettingsSection.Appearance)))

    @Test fun clock_chosenIndex_followsTheTv_untilChosen() {
        assertEquals(1, chosenIndex(SettingsItem.Clock, Chosen(Look()), tvIs24 = false))
        assertEquals(0, chosenIndex(SettingsItem.Clock, Chosen(Look()), tvIs24 = true))
        assertEquals(2, chosenIndex(SettingsItem.Clock, Chosen(Look(clock = ClockChoice.Hidden)), tvIs24 = true))
    }

    @Test fun clock_withChoice_savesAnExplicitChoice() =
        assertEquals(ClockChoice.H12, withChoice(SettingsItem.Clock, 1, Chosen(Look())).look.clock)

    @Test fun clock_options() = assertEquals(listOf("24-hour", "12-hour", "Hidden"), choiceOptions(SettingsItem.Clock))

    @Test fun textSize_options_andChoice() {
        assertEquals(listOf("Standard", "Large"), choiceOptions(SettingsItem.TextSize))
        assertEquals(TextSize.Large, withChoice(SettingsItem.TextSize, 1, Chosen(Look())).look.textSize)
        assertEquals(1, chosenIndex(SettingsItem.TextSize, Chosen(Look(textSize = TextSize.Large))))
    }

    @Test fun focusHighlight_options_andChoice() {
        assertEquals(listOf("Ring and glow", "Ring only"), choiceOptions(SettingsItem.FocusHighlight))
        assertEquals(FocusStyle.RingOnly, withChoice(SettingsItem.FocusHighlight, 1, Chosen(Look())).look.focus)
    }

    @Test fun focusHighlight_isAStatementWhileReduced() {
        assertEquals(RowKind.List, kindOf(SettingsItem.FocusHighlight, reduced = false))
        assertEquals(RowKind.Statement, kindOf(SettingsItem.FocusHighlight, reduced = true))
    }

    @Test fun textSizeAndClock_stepInPlace_withTheBoardsHints() {
        assertEquals(RowKind.Stepped, kindOf(SettingsItem.TextSize))
        assertEquals(RowKind.Stepped, kindOf(SettingsItem.Clock))
        assertEquals("Change size", stepHintFor(SettingsItem.TextSize))
        assertEquals("Change", stepHintFor(SettingsItem.Clock))
    }

    @Test fun appearance_rowsAndGroups_matchItsBoard() {
        val g = groupsOf(SettingsSection.Appearance)
        assertEquals(listOf(SettingsItem.Theme, SettingsItem.Accent, SettingsItem.TextSize, SettingsItem.Clock), g[0].items)
        assertEquals(listOf(SettingsItem.FocusHighlight, SettingsItem.ReducedEffects), g[1].items)
        assertEquals(listOf("Theme", "Accent colour", "Text size", "Clock", "Focus highlight", "Reduced effects"), rowsIn(SettingsSection.Appearance).map(::titleOf))
        assertEquals(36 + 3 * 104 + 92 + 72, rowTop(SettingsItem.FocusHighlight, g))
    }

    @Test fun tileSize_options_andNote() {
        assertEquals(listOf("Small", "Medium", "Large"), choiceOptions(SettingsItem.TileSize))
        assertEquals("Medium shows 7 across", tileSizeNote(TileSize.Medium))
        assertEquals("Small shows 9 across", tileSizeNote(TileSize.Small))
        assertEquals("Large shows 5 across", tileSizeNote(TileSize.Large))
    }

    @Test fun spacing_withChoice_changesOnlySpacing() {
        val now = Chosen(Look(theme = ThemeId.Plum), HomeLayout(tileSize = TileSize.Large))
        assertEquals(now.copy(layout = now.layout.copy(spacing = Spacing.Roomy)), withChoice(SettingsItem.Spacing, 2, now))
    }

    @Test fun tileSizeAndShape_chosenIndex_andChoice() {
        val now = Chosen(Look(), HomeLayout(tileSize = TileSize.Small, tileShape = TileShape.FullBanner))
        assertEquals(0, chosenIndex(SettingsItem.TileSize, now))
        assertEquals(1, chosenIndex(SettingsItem.TileShape, now))
        assertEquals(TileSize.Large, withChoice(SettingsItem.TileSize, 2, now).layout.tileSize)
        assertEquals(TileShape.Wide, withChoice(SettingsItem.TileShape, 0, now).layout.tileShape)
        assertEquals(now.look, withChoice(SettingsItem.TileShape, 0, now).look)
    }

    @Test fun tileShape_options() = assertEquals(listOf("Wide tile", "Full banner"), choiceOptions(SettingsItem.TileShape))

    @Test fun theTileRows_kinds_andHints() {
        assertEquals(RowKind.Stepped, kindOf(SettingsItem.TileSize))
        assertEquals(RowKind.Stepped, kindOf(SettingsItem.Spacing))
        assertEquals(RowKind.List, kindOf(SettingsItem.TileShape))
        assertEquals(RowKind.Switch, kindOf(SettingsItem.AppNames))
        assertEquals("Change size", stepHintFor(SettingsItem.TileSize))
        assertEquals("Change", stepHintFor(SettingsItem.Spacing))
    }

    @Test fun homeLayout_firstGroupIsFourTileRows_asOnItsBoard() {
        val g = groupsOf(SettingsSection.HomeLayout)
        assertEquals("APP TILES", g[0].label)
        assertEquals(listOf(SettingsItem.TileSize, SettingsItem.TileShape, SettingsItem.Spacing, SettingsItem.AppNames), g[0].items)
        assertEquals(listOf("Tile size", "Tile shape", "Spacing", "App names"), g[0].items.map(::titleOf))
    }

    // Phase 3: "Row order" leaves Home layout; the rows are moved in Rows and apps.
    @Test fun homeLayout_secondGroup_isOnNowLayoutOnly() {
        val g = groupsOf(SettingsSection.HomeLayout)
        assertEquals(listOf("APP TILES", "ROWS"), g.map { it.label })
        assertEquals(listOf(SettingsItem.OnNowLayout), g[1].items)
        assertEquals(36 + 3 * 104 + 92 + 72, rowTop(SettingsItem.OnNowLayout, g))
        assertFalse(SettingsItem.entries.any { it.name == "RowOrder" })
    }

    @Test fun onNow_options_kind_andChoice() {
        assertEquals(listOf("One large, two small", "One large, four small", "Six small"), choiceOptions(SettingsItem.OnNowLayout))
        assertEquals(RowKind.List, kindOf(SettingsItem.OnNowLayout))
        assertEquals(OnNowLayout.SixSmall, withChoice(SettingsItem.OnNowLayout, 2, Chosen(Look())).layout.onNow)
        assertEquals(1, chosenIndex(SettingsItem.OnNowLayout, Chosen(Look(), HomeLayout(onNow = OnNowLayout.LargeFour))))
        assertEquals("On now layout", titleOf(SettingsItem.OnNowLayout))
    }
}
