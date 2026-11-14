package dev.atomix.launcher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.focusRequester
import dev.atomix.launcher.apps.TvFacts
import dev.atomix.launcher.apps.TvPage
import dev.atomix.launcher.ui.kit.RowEnding
import dev.atomix.launcher.ui.kit.SettingRow
import dev.atomix.launcher.ui.kit.WindowColumn
import dev.atomix.launcher.ui.kit.WindowState

/** How many of the TV's pages show at once; the rest are reached by moving down, and named under "More below". */
internal const val TV_ROWS_SHOWN = 7

/**
 * Settings › TV settings: one row per page of the TV's own settings that can be opened. OK opens the page over the
 * launcher; Back from it returns here. Network and Storage also say what the launcher can read by itself.
 */
@Composable
fun TvSettingsPane(host: PaneHost, tv: TvFacts, window: WindowState, onOpen: (TvPage) -> Unit) {
    GroupLabel("THIS TV")
    val first = window.startFor(tv.pages.size, TV_ROWS_SHOWN)
    WindowColumn(tv.pages, { _, page -> page }, window, TV_ROWS_SHOWN, rowPx = 92, gapPx = 12) { index, page, slot ->
        SettingRow(
            page.label, { onOpen(page) },
            // OK or Right on the section lands on the first row that shows, wherever the list was left.
            modifier = if (index == first) slot.focusRequester(host.first) else slot,
            description = page.note,
            ending = RowEnding.Value(
                when (page) {
                    TvPage.Network -> tv.network
                    TvPage.Storage -> tv.storage.orEmpty()
                    else -> ""
                },
                chevron = true,
            ),
            onFocused = { host.onFocused(SettingsItem.TvPages) },
        )
    }
}
