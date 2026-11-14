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
    TvSettings("TV settings"),
    About("About"),
}

/** The rows that work today. */
enum class SettingsItem {
    HomeButton, Theme, Accent, ReducedEffects, LiveSources, TestChannel, ManageSources, Server, FindServer, SwitchProfile,
    /** The TV's own settings pages, one row each (apps/TvPages.kt). */
    TvPages,
    Version,
}

/** The rows a section holds, in order; empty for a section that has nothing working yet. */
fun rowsIn(section: SettingsSection): List<SettingsItem> = when (section) {
    SettingsSection.Setup -> listOf(SettingsItem.HomeButton)
    SettingsSection.Appearance -> listOf(SettingsItem.Theme, SettingsItem.Accent, SettingsItem.ReducedEffects)
    SettingsSection.LiveTv -> listOf(SettingsItem.LiveSources, SettingsItem.TestChannel, SettingsItem.ManageSources)
    SettingsSection.Account -> listOf(SettingsItem.Server, SettingsItem.FindServer, SettingsItem.SwitchProfile)
    SettingsSection.TvSettings -> listOf(SettingsItem.TvPages)
    SettingsSection.About -> listOf(SettingsItem.Version)
    SettingsSection.HomeLayout, SettingsSection.Rows, SettingsSection.Wallpaper, SettingsSection.Inputs -> emptyList()
}

/**
 * A section appears in the list only when it has at least one working row: no section that does nothing yet.
 * [tvPages] is how many of the TV's own pages can be opened; with none, "TV settings" would be empty and is left out.
 */
fun listedSections(tvPages: Int = 1): List<SettingsSection> =
    SettingsSection.entries.filter { rowsIn(it).isNotEmpty() && (it != SettingsSection.TvSettings || tvPages > 0) }

/**
 * The "More below" line of a windowed list of rows: the labels under the [visible] rows that show from [first], with
 * their first letter lowered; null when nothing is below.
 */
fun moreBelow(labels: List<String>, first: Int, visible: Int): String? {
    val below = labels.drop(first + visible)
    if (below.isEmpty()) return null
    return "More below: " + below.joinToString(", ") { it.replaceFirstChar(Char::lowercaseChar) }
}

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

/**
 * What OK does on a row, for the hint bar; null for a row that only states something, and for "Test a channel" when
 * there is no channel to play ([hasTestChannel] false), so the hint never promises what OK cannot do.
 */
fun okActionFor(item: SettingsItem?, reduced: Boolean, hasTestChannel: Boolean): String? = when (item) {
    SettingsItem.HomeButton -> "Open TV settings"
    SettingsItem.ReducedEffects -> if (reduced) "Turn off" else "Turn on"
    SettingsItem.LiveSources -> "Refresh"
    SettingsItem.TestChannel -> if (hasTestChannel) "Play" else null
    SettingsItem.ManageSources -> "Open Atomix"
    SettingsItem.FindServer -> "Search"
    SettingsItem.SwitchProfile -> "Switch"
    SettingsItem.TvPages -> "Open"
    SettingsItem.Theme -> "Choose"
    // Left and Right change it; OK has nothing to do.
    SettingsItem.Accent -> null
    SettingsItem.Server, SettingsItem.Version, null -> null
}
