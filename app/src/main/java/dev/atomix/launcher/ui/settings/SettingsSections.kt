package dev.atomix.launcher.ui.settings

import dev.atomix.launcher.ui.kit.RowEnding
import dev.atomix.launcher.ui.kit.Tone

/** Every section Settings will have, in the order of the list. Only those with a working row are listed yet. */
enum class SettingsSection(val title: String) {
    Setup("Set-up and launcher"),
    Appearance("Appearance"),
    HomeLayout("Home layout"),
    Rows("Rows and apps"),
    Wallpaper("Wallpaper"),
    Inputs("Inputs"),
    LiveTv("Live TV"),
    Account("Account and server"),
    About("About"),
}

/** The rows that work today. */
enum class SettingsItem { HomeButton, ReducedEffects, LiveSources, TestChannel, ManageSources, Server, FindServer, SwitchProfile, Version }

/** The rows a section holds, in order; empty for a section that has nothing working yet. */
fun rowsIn(section: SettingsSection): List<SettingsItem> = when (section) {
    SettingsSection.Setup -> listOf(SettingsItem.HomeButton)
    SettingsSection.Appearance -> listOf(SettingsItem.ReducedEffects)
    SettingsSection.LiveTv -> listOf(SettingsItem.LiveSources, SettingsItem.TestChannel, SettingsItem.ManageSources)
    SettingsSection.Account -> listOf(SettingsItem.Server, SettingsItem.FindServer, SettingsItem.SwitchProfile)
    SettingsSection.About -> listOf(SettingsItem.Version)
    SettingsSection.HomeLayout, SettingsSection.Rows, SettingsSection.Wallpaper, SettingsSection.Inputs -> emptyList()
}

/** A section appears in the list only when it has at least one working row: no section that does nothing yet. */
fun listedSections(): List<SettingsSection> = SettingsSection.entries.filter { rowsIn(it).isNotEmpty() }

/** The rows that only state something and never take focus. */
private val STATEMENTS = setOf(SettingsItem.Server, SettingsItem.Version)

/** Whether OK on a section has a row to move into. About has none, so its hint does not offer "OK Open". */
fun hasRowToOpen(section: SettingsSection): Boolean = rowsIn(section).any { it !in STATEMENTS }

/**
 * How the "Home button opens Atomix TV" row ends: a status, not a switch. The launcher never switches the service
 * itself (Android lets an app turn its own service off but not back on, which could strand the Home button); the row
 * says how it stands and opens the TV's own settings, hence the chevron.
 */
fun homeButtonEnding(on: Boolean): RowEnding.Status =
    if (on) RowEnding.Status("On", Tone.Good, chevron = true) else RowEnding.Status("Off", Tone.Warn, chevron = true)
