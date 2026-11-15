package dev.atomix.launcher.ui.kit

/** One chip of the hint bar: a remote key and what it does right now. */
data class Hint(val key: String, val label: String)

/** Where focus is, as far as the hint bar is concerned. */
sealed interface HintSpot {
    data object HomeRest : HintSpot
    data class HomeEdit(val holding: Boolean) : HintSpot
    /** Focus is in the top bar; [back] is where Back goes ("Home" or "Apps"). */
    data class TopBar(val back: String) : HintSpot
    data class LiveRow(val channel: String) : HintSpot
    data class LiveSwitch(val on: Boolean) : HintSpot
    data class AppTile(val app: String) : HintSpot
    data object AppsEdit : HintSpot
    /** A tile in manage apps; [back] is where Back goes ("Apps", or "Settings" when opened from there). */
    data class ManageTile(val back: String = "Apps") : HintSpot
    /** A line of an app's menu in manage apps; [back] is "Menu" in the "Add to a row…" list. */
    data class ManageMenu(val item: String, val back: String = "Close menu") : HintSpot
    /** A segmented pill has focus. */
    data class Pill(val back: String) : HintSpot
    data class InputCard(val input: String) : HintSpot
    data object SettingsSections : HintSpot
    /** [action] is what OK does on the row; null when the row does nothing on OK. */
    data class SettingsRow(val action: String?) : HintSpot
    /** A row whose value Left and Right change in place; [what] says what they change. */
    data class SettingsStep(val what: String) : HintSpot
    /** A row's list of choices is open. */
    data object SettingsChoice : HintSpot
    /** A row's entry in Settings › Rows and apps. */
    data object RowsEntry : HintSpot
    /** One of a row's options there; [label] is the option's words, [back] what Back does ("Close" on a question). */
    data class RowsOption(val label: String, val back: String = "Sections") : HintSpot
    data object PlayerBanner : HintSpot
    data object PlayerTuning : HintSpot
    data class PlayerError(val button: String, val bannerUp: Boolean) : HintSpot
    data class PlayerList(val channel: String) : HintSpot
}

/** The key name on the OK chip. */
internal const val OK = "OK"
private const val BACK = "Back"
private const val UP_DOWN = "▲▼"

/** What the hint bar says for each focus position. An empty list means no bar (Home at rest). */
fun hintsFor(spot: HintSpot): List<Hint> = when (spot) {
    HintSpot.HomeRest -> emptyList()
    is HintSpot.HomeEdit ->
        if (spot.holding) listOf(Hint("◀ ▶", "Move"), Hint(OK, "Put down"), Hint(BACK, "Put down"))
        else listOf(Hint(OK, "Pick up"), Hint(BACK, "Done"))
    is HintSpot.TopBar -> listOf(Hint(OK, "Open"), Hint(BACK, spot.back))
    is HintSpot.LiveRow -> listOf(Hint(OK, "Watch ${spot.channel}"), Hint("▶", "Show on Home"), Hint(BACK, "Home"))
    is HintSpot.LiveSwitch -> listOf(Hint(OK, if (spot.on) "Turn off" else "Turn on"), Hint("◀", "Channels"), Hint(BACK, "Home"))
    is HintSpot.AppTile -> listOf(Hint(OK, "Open ${spot.app}"), Hint(BACK, "Home"))
    HintSpot.AppsEdit -> listOf(Hint(OK, "Choose apps"), Hint(BACK, "Home"))
    is HintSpot.ManageTile -> listOf(Hint(OK, "Options"), Hint(BACK, spot.back))
    is HintSpot.ManageMenu -> listOf(Hint(OK, spot.item), Hint(BACK, spot.back))
    is HintSpot.Pill -> listOf(Hint(OK, "Select"), Hint(BACK, spot.back))
    is HintSpot.InputCard -> listOf(Hint(OK, "Switch to ${spot.input}"), Hint(BACK, "Home"))
    HintSpot.SettingsSections -> listOf(Hint(OK, "Open"), Hint(BACK, "Home"))
    is HintSpot.SettingsRow -> listOfNotNull(spot.action?.let { Hint(OK, it) }, Hint(BACK, "Sections"))
    is HintSpot.SettingsStep -> listOf(Hint("◀ ▶", spot.what), Hint(BACK, "Sections"))
    HintSpot.SettingsChoice -> listOf(Hint(OK, "Select"), Hint(BACK, "Close"))
    HintSpot.RowsEntry -> listOf(Hint(OK, "Show or hide"), Hint("▼", "This row's options"), Hint(BACK, "Sections"))
    is HintSpot.RowsOption -> listOf(Hint(OK, spot.label), Hint(BACK, spot.back))
    HintSpot.PlayerBanner -> listOf(Hint(UP_DOWN, "Change channel"), Hint("◀", "Channels"), Hint(BACK, "Hide"))
    HintSpot.PlayerTuning -> listOf(Hint(UP_DOWN, "Change channel"), Hint(BACK, "Home"))
    is HintSpot.PlayerError -> listOf(Hint(OK, spot.button), Hint(UP_DOWN, "Change channel"), Hint(BACK, if (spot.bannerUp) "Hide" else "Home"))
    is HintSpot.PlayerList -> listOf(Hint(OK, "Watch ${spot.channel}"), Hint(BACK, "Close"))
}
