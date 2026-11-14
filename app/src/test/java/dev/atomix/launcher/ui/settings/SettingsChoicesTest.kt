package dev.atomix.launcher.ui.settings

import dev.atomix.launcher.prefs.TextSize
import dev.atomix.launcher.prefs.FocusStyle
import dev.atomix.launcher.prefs.ClockChoice
import dev.atomix.launcher.prefs.AccentId
import dev.atomix.launcher.prefs.Look
import dev.atomix.launcher.prefs.ThemeId
import org.junit.Assert.assertEquals
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
}
