package dev.atomix.launcher.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import dev.atomix.launcher.prefs.AccentId
import dev.atomix.launcher.ui.kit.RowEnding
import dev.atomix.launcher.ui.kit.SettingRow
import dev.atomix.launcher.ui.theme.px

/**
 * Settings › Appearance, as on its board: the rows of [groupsOf] under their labels. A change shows at once; nothing
 * here is applied later. [openList] is the row whose list of choices is open, which keeps a quiet outline meanwhile.
 * [onStep] is Left or Right on a row that steps in place (the choice's new index); [onOpenList] is OK on a row whose
 * choices open as a list.
 */
@Composable
fun AppearancePane(
    host: PaneHost,
    now: Chosen,
    reduced: Boolean,
    openList: SettingsItem?,
    onStep: (SettingsItem, Int) -> Unit,
    onOpenList: (SettingsItem) -> Unit,
    onReduced: (Boolean) -> Unit,
) {
    var firstTaken = false
    // Every row can be given focus by name (a list closing returns to its row); the first one is also where OK or
    // Right on the section lands.
    fun at(item: SettingsItem): Modifier {
        val own = Modifier.focusRequester(host.rowFocus(item))
        return if (firstTaken) own else { firstTaken = true; own.focusRequester(host.first) }
    }
    groupsOf(SettingsSection.Appearance).forEachIndexed { g, group ->
        // A new group starts 72 under the last row: the rows' own 12, this 12, the 12 after it, and the label's 36.
        if (g > 0) Spacer(Modifier.height(px(12)))
        GroupLabel(group.label)
        group.items.forEach { item ->
            when (item) {
                SettingsItem.Theme -> SettingRow(
                    "Theme", { onOpenList(item) }, at(item),
                    ending = RowEnding.Value(now.look.theme.label, chevron = true),
                    held = openList == item,
                    onFocused = { host.onFocused(item) },
                )
                SettingsItem.Accent -> SettingRow(
                    "Accent colour", {}, at(item),
                    description = now.look.accent.label,
                    ending = RowEnding.Dots(AccentId.entries.map { it.argb }, now.look.accent.ordinal),
                    onPill = { onStep(item, it) },
                    onFocused = { host.onFocused(item) },
                )
                SettingsItem.ReducedEffects -> SettingRow(
                    "Reduced effects", { onReduced(!reduced) }, at(item),
                    description = "No glow or animation, for a steadier picture",
                    ending = RowEnding.Switch(reduced),
                    onFocused = { host.onFocused(item) },
                )
                else -> Unit
            }
        }
    }
}
