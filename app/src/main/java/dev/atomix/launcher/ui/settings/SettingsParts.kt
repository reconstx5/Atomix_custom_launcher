package dev.atomix.launcher.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.AtomixType
import dev.atomix.launcher.ui.theme.px

/**
 * What a section's pane needs from the Settings screen: [first] goes on the row OK or Right on the section lands on,
 * and [onFocused] is told which item has focus, for the hint bar.
 */
class PaneHost(val first: FocusRequester, val onFocused: (SettingsItem) -> Unit)

/** A group's label over its rows, in its 36-high line. */
@Composable
internal fun GroupLabel(text: String) {
    // The rows are 12 apart; the label's line already holds the space between it and its first row.
    Box(Modifier.height(px(36 - 12))) { BasicText(text, style = AtomixType.groupLabel(), maxLines = 1) }
}

/** One line of plain words under a group's rows. */
@Composable
internal fun Note(text: String, color: Color = AtomixColors.TextSecondary) {
    BasicText(text, Modifier.padding(start = px(28), top = px(4)), style = AtomixType.description().copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
}
