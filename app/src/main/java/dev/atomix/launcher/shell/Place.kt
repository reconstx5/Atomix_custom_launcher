package dev.atomix.launcher.shell

import dev.atomix.launcher.ui.kit.Tab

/** The two round buttons at the right of the top bar. */
enum class TopButton { Inputs, Settings }

/**
 * Where the launcher is. The top bar marks either a tab (underlined) or a round button (lit), never both: manage apps
 * belongs to the Apps tab; Inputs and Settings are screens of their own.
 */
enum class Place(val tab: Tab?, val button: TopButton?) {
    Home(Tab.Home, null),
    LiveTv(Tab.LiveTv, null),
    Apps(Tab.Apps, null),
    ManageApps(Tab.Apps, null),
    Inputs(null, TopButton.Inputs),
    Settings(null, TopButton.Settings);

    /** Where Back goes: up one level, or nowhere from Home (the bottom of the stack). */
    fun back(): Place? = when (this) {
        Home -> null
        ManageApps -> Apps
        else -> Home
    }

    companion object {
        fun of(tab: Tab): Place = when (tab) {
            Tab.Home -> Home
            Tab.LiveTv -> LiveTv
            Tab.Apps -> Apps
        }
    }
}

/**
 * Where the launcher is after an input was picked on the Inputs page. Home if the input opened, so Back from it
 * lands on Home and not on the list again; still Inputs if it could not be opened, where the message makes sense.
 */
fun placeAfterPick(started: Boolean): Place = if (started) Place.Home else Place.Inputs
