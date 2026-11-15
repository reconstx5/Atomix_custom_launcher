package dev.atomix.launcher.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import dev.atomix.launcher.ui.kit.RowEnding
import dev.atomix.launcher.ui.kit.SettingRow
import dev.atomix.launcher.ui.theme.px

/**
 * Settings › Home layout, as on its board: the rows of [groupsOf] under their labels. These change Home only, and
 * each change shows at once in the preview beside the rows. [onStep] is Left or Right on a row that steps in place;
 * [onOpenList] is OK on a row whose choices open as a list; [onAppNames] turns app names on or off.
 */
@Composable
fun HomeLayoutPane(
    host: PaneHost,
    now: Chosen,
    openList: SettingsItem?,
    onStep: (SettingsItem, Int) -> Unit,
    onOpenList: (SettingsItem) -> Unit,
    onAppNames: (Boolean) -> Unit,
) {
    var firstTaken = false
    fun at(item: SettingsItem): Modifier {
        val own = Modifier.focusRequester(host.rowFocus(item))
        return if (firstTaken) own else { firstTaken = true; own.focusRequester(host.first) }
    }
    groupsOf(SettingsSection.HomeLayout).forEachIndexed { g, group ->
        if (g > 0) Spacer(Modifier.height(px(12)))
        GroupLabel(group.label)
        group.items.forEach { item ->
            when (kindOf(item)) {
                RowKind.Stepped -> SettingRow(
                    titleOf(item), {}, at(item),
                    // Tile size says what the chosen size means: how many fit across.
                    description = if (item == SettingsItem.TileSize) tileSizeNote(now.layout.tileSize) else null,
                    ending = RowEnding.Pills(choiceOptions(item), chosenIndex(item, now)),
                    onPill = { onStep(item, it) },
                    onFocused = { host.onFocused(item) },
                )
                RowKind.List -> SettingRow(
                    titleOf(item), { onOpenList(item) }, at(item),
                    ending = RowEnding.Value(choiceOptions(item).getOrElse(chosenIndex(item, now)) { "" }, chevron = true),
                    held = openList == item,
                    onFocused = { host.onFocused(item) },
                )
                RowKind.Switch -> SettingRow(
                    titleOf(item), { onAppNames(!now.layout.appNames) }, at(item),
                    ending = RowEnding.Switch(now.layout.appNames),
                    onFocused = { host.onFocused(item) },
                )
                RowKind.Statement, RowKind.Action -> Unit
            }
        }
    }
}
