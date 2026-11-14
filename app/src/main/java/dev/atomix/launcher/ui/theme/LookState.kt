package dev.atomix.launcher.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.atomix.launcher.prefs.Look

/**
 * The look every screen is drawn in, as one observable value: [AtomixColors], text sizes and the focus style read it,
 * so a change in Settings redraws whatever uses it, at once. It is written in two places only: each activity sets it
 * from the saved settings as it starts, and Settings sets it when the owner changes something.
 */
object LookState {
    var look: Look by mutableStateOf(Look())

    /** The colours of the theme that is on. */
    val palette: Palette get() = paletteFor(look.theme)
}
