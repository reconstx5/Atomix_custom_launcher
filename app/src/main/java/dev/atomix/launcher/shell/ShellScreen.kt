package dev.atomix.launcher.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ImageBitmap
import dev.atomix.launcher.ui.kit.Tab
import dev.atomix.launcher.ui.kit.TopMenu
import dev.atomix.launcher.ui.theme.px

/**
 * The launcher's frame: the top menu over whichever tab is open. Back on Live TV or Apps returns to Home;
 * on Home it does nothing (Home is the bottom of the stack).
 */
@Composable
fun ShellScreen(
    tab: Tab,
    onTab: (Tab) -> Unit,
    clock: String,
    blurred: ImageBitmap?,
    onInputs: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable (Tab) -> Unit,
) {
    val menuFocus = remember { FocusRequester() }
    BackHandler { if (tab != Tab.Home) onTab(Tab.Home) }
    Box(Modifier.fillMaxSize()) {
        content(tab)
        TopMenu(
            selected = tab,
            onSelect = onTab,
            onInputs = onInputs,
            onSettings = onSettings,
            clock = clock,
            blurred = blurred,
            firstFocus = menuFocus,
            modifier = Modifier.padding(start = px(116), end = px(116), top = px(60)),
        )
    }
}
