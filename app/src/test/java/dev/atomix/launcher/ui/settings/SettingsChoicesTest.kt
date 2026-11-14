package dev.atomix.launcher.ui.settings

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
}
