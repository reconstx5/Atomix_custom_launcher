package dev.atomix.launcher.ui.settings

import dev.atomix.launcher.ui.kit.RowEnding
import dev.atomix.launcher.ui.kit.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsSectionsTest {
    @Test fun onlySectionsWithAWorkingRowAreListed() = assertEquals(
        listOf(SettingsSection.Setup, SettingsSection.Appearance, SettingsSection.LiveTv, SettingsSection.Account, SettingsSection.About),
        listedSections())

    @Test fun titles_matchTheBoard() = assertEquals(
        listOf("Set-up and launcher", "Appearance", "Home layout", "Rows and apps", "Wallpaper", "Inputs", "Live TV", "Account and server", "About"),
        SettingsSection.entries.map { it.title })

    @Test fun rows_perSection() {
        assertEquals(listOf(SettingsItem.HomeButton), rowsIn(SettingsSection.Setup))
        assertEquals(listOf(SettingsItem.ReducedEffects), rowsIn(SettingsSection.Appearance))
        assertEquals(listOf(SettingsItem.LiveSources, SettingsItem.TestChannel, SettingsItem.ManageSources), rowsIn(SettingsSection.LiveTv))
        assertEquals(listOf(SettingsItem.Server, SettingsItem.FindServer, SettingsItem.SwitchProfile), rowsIn(SettingsSection.Account))
        assertEquals(listOf(SettingsItem.Version), rowsIn(SettingsSection.About))
        assertEquals(emptyList<SettingsItem>(), rowsIn(SettingsSection.Wallpaper))
    }

    @Test fun homeButtonRow_isAStatus_notASwitch() {
        assertEquals(RowEnding.Status("On", Tone.Good, chevron = true), homeButtonEnding(true))
        assertEquals(RowEnding.Status("Off", Tone.Warn, chevron = true), homeButtonEnding(false))
    }

    @Test fun okOpensASection_onlyWhenARowInItCanTakeFocus() {
        assertEquals(listOf(SettingsSection.Setup, SettingsSection.Appearance, SettingsSection.LiveTv, SettingsSection.Account), listedSections().filter(::hasRowToOpen))
    }

    @Test fun testAChannel_offersPlay_onlyWithAChannelToPlay() {
        assertEquals("Play", okActionFor(SettingsItem.TestChannel, reduced = false, hasTestChannel = true))
        assertNull(okActionFor(SettingsItem.TestChannel, reduced = false, hasTestChannel = false))
    }

    @Test fun okAction_saysWhatOkDoes_andNothingForAStatement() {
        assertEquals("Turn on", okActionFor(SettingsItem.ReducedEffects, reduced = false, hasTestChannel = true))
        assertEquals("Turn off", okActionFor(SettingsItem.ReducedEffects, reduced = true, hasTestChannel = true))
        assertNull(okActionFor(SettingsItem.Version, reduced = false, hasTestChannel = true))
        assertNull(okActionFor(null, reduced = false, hasTestChannel = true))
    }
}
