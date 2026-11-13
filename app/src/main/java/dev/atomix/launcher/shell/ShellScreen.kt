package dev.atomix.launcher.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import dev.atomix.launcher.ui.kit.TopMenu
import dev.atomix.launcher.ui.kit.Wash
import dev.atomix.launcher.ui.theme.px

/**
 * The launcher's frame: the ground, whichever place is open, and the top bar over it. Back goes up one level
 * ([Place.back]); on Home it does nothing (Home is the bottom of the stack).
 */
@Composable
fun ShellScreen(
    place: Place,
    onPlace: (Place) -> Unit,
    clock: String,
    onInputs: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable (Place) -> Unit,
) {
    val menuFocus = remember { FocusRequester() }
    BackHandler { place.back()?.let(onPlace) }
    Box(Modifier.fillMaxSize()) {
        Wash()
        content(place)
        TopMenu(
            selected = place.tab,
            current = place.button,
            onSelect = { onPlace(Place.of(it)) },
            onInputs = onInputs,
            onSettings = onSettings,
            clock = clock,
            firstFocus = menuFocus,
            modifier = Modifier.padding(start = px(100), end = px(100), top = px(28)),
        )
    }
}
