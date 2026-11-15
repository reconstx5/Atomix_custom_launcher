package dev.atomix.launcher.ui.kit

import org.junit.Assert.assertEquals
import org.junit.Test

class HintsTest {
    private fun h(vararg pairs: Pair<String, String>) = pairs.map { Hint(it.first, it.second) }

    @Test fun homeAtRest_hasNoBar() = assertEquals(emptyList<Hint>(), hintsFor(HintSpot.HomeRest))

    @Test fun homeEdit_namesPickUpThenMoveAndPutDown() {
        assertEquals(h("OK" to "Pick up", "Back" to "Done"), hintsFor(HintSpot.HomeEdit(holding = false)))
        assertEquals(h("◀ ▶" to "Move", "OK" to "Put down", "Back" to "Put down"), hintsFor(HintSpot.HomeEdit(holding = true)))
    }

    @Test fun topBar_opensAndGoesBack() = assertEquals(h("OK" to "Open", "Back" to "Apps"), hintsFor(HintSpot.TopBar("Apps")))

    @Test fun liveTv_rowThenSwitch() {
        assertEquals(h("OK" to "Watch TVNZ 1", "▶" to "Show on Home", "Back" to "Home"), hintsFor(HintSpot.LiveRow("TVNZ 1")))
        assertEquals(h("OK" to "Turn off", "◀" to "Channels", "Back" to "Home"), hintsFor(HintSpot.LiveSwitch(on = true)))
        assertEquals("Turn on", hintsFor(HintSpot.LiveSwitch(on = false)).first().label)
    }

    @Test fun apps_tileEditAndManage() {
        assertEquals(h("OK" to "Open BrowseHere", "Back" to "Home"), hintsFor(HintSpot.AppTile("BrowseHere")))
        assertEquals(h("OK" to "Choose apps", "Back" to "Home"), hintsFor(HintSpot.AppsEdit))
        assertEquals(h("OK" to "Options", "Back" to "Apps"), hintsFor(HintSpot.ManageTile))
        assertEquals(h("OK" to "Add to Your apps", "Back" to "Close menu"), hintsFor(HintSpot.ManageMenu("Add to Your apps")))
        assertEquals(h("OK" to "Select", "Back" to "Apps"), hintsFor(HintSpot.Pill("Apps")))
    }

    @Test fun inputs_switchTo() = assertEquals(h("OK" to "Switch to HDMI 1", "Back" to "Home"), hintsFor(HintSpot.InputCard("HDMI 1")))

    @Test fun settings_backStepsOutOneLevel() {
        assertEquals(h("OK" to "Open", "Back" to "Home"), hintsFor(HintSpot.SettingsSections))
        assertEquals(h("OK" to "Open TV settings", "Back" to "Sections"), hintsFor(HintSpot.SettingsRow("Open TV settings")))
        assertEquals(h("Back" to "Sections"), hintsFor(HintSpot.SettingsRow(null)))
    }

    @Test fun settingsStep_namesTheArrows() =
        assertEquals(h("◀ ▶" to "Pick a colour", "Back" to "Sections"), hintsFor(HintSpot.SettingsStep("Pick a colour")))

    @Test fun settingsChoice_selectsOrCloses() = assertEquals(h("OK" to "Select", "Back" to "Close"), hintsFor(HintSpot.SettingsChoice))

    // Settings › Rows and apps (spec §7.2, §7.5): an entry, one of its options, and the question about deleting it.
    @Test fun rowsPane_hints() {
        assertEquals(h("OK" to "Show or hide", "▼" to "This row's options", "Back" to "Sections"), hintsFor(HintSpot.RowsEntry))
        assertEquals(h("OK" to "Move up", "Back" to "Sections"), hintsFor(HintSpot.RowsOption("Move up")))
        assertEquals(h("OK" to "Keep", "Back" to "Close"), hintsFor(HintSpot.RowsOption("Keep", back = "Close")))
    }

    @Test fun watching_matchesTheBoards() {
        assertEquals(h("▲▼" to "Change channel", "◀" to "Channels", "Back" to "Hide"), hintsFor(HintSpot.PlayerBanner))
        assertEquals(h("▲▼" to "Change channel", "Back" to "Home"), hintsFor(HintSpot.PlayerTuning))
        assertEquals(h("OK" to "Try again", "▲▼" to "Change channel", "Back" to "Home"), hintsFor(HintSpot.PlayerError("Try again", bannerUp = false)))
        assertEquals("Hide", hintsFor(HintSpot.PlayerError("Try again", bannerUp = true)).last().label)
        assertEquals(h("OK" to "Watch TVNZ 2", "Back" to "Close"), hintsFor(HintSpot.PlayerList("TVNZ 2")))
    }
}
