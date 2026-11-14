package dev.atomix.launcher.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import dev.atomix.launcher.ui.kit.TopMenu
import dev.atomix.launcher.ui.kit.Wash
import dev.atomix.launcher.ui.theme.px

/**
 * How a screen and the top bar hand focus to each other: [marked] is the bar's marked item (the open tab, or the lit
 * round button), where a screen sends Up; [down], set by the screen that is showing, is where Down from the bar lands
 * when the nearest item is not the right one.
 */
class TopBarFocus(val marked: FocusRequester) {
    var down: (() -> FocusRequester)? = null
    /** Whether focus is on the top bar now. Read when deciding whether a page may take focus back. */
    var hasFocus: Boolean = false
}

/**
 * Whether a page puts focus back on its remembered item. It always does when the user comes to it ([byTick]: a tab
 * was opened, an app or the player was left). When only its list changed under it, it does so unless the top bar
 * holds focus: OK there must go on meaning the tab or button the user is on.
 */
fun restoresFocus(byTick: Boolean, barHasFocus: Boolean): Boolean = byTick || !barHasFocus

val LocalTopBarFocus = staticCompositionLocalOf<TopBarFocus?> { null }

/**
 * The launcher's frame: the ground, whichever place is open, and the top bar over it. Back goes up one level
 * ([Place.back]); on Home it does nothing (Home is the bottom of the stack).
 */
@Composable
fun ShellScreen(
    place: Place,
    onPlace: (Place) -> Unit,
    /** The time for the top bar, or null when the clock is hidden. */
    clock: String?,
    onInputs: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable (Place) -> Unit,
) {
    val menuFocus = remember { FocusRequester() }
    val barFocus = remember { TopBarFocus(menuFocus) }
    BackHandler { place.back()?.let(onPlace) }
    Box(Modifier.fillMaxSize()) {
        Wash()
        CompositionLocalProvider(LocalTopBarFocus provides barFocus) { content(place) }
        TopMenu(
            selected = place.tab,
            current = place.button,
            onSelect = { onPlace(Place.of(it)) },
            onInputs = onInputs,
            onSettings = onSettings,
            clock = clock,
            firstFocus = menuFocus,
            modifier = Modifier.padding(start = px(100), end = px(100), top = px(28))
                .onFocusChanged { barFocus.hasFocus = it.hasFocus }
                .focusProperties { down = barFocus.down?.invoke() ?: FocusRequester.Default },
        )
    }
}
